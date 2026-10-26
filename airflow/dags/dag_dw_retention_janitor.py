import logging
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.sdk import dag, task

logger = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")

# 관리 대상 테이블 및 보존 개월 수
RETENTION_MONTHS = 3  # 90일 (3개월) 경과 파티션 DROP
PRECREATE_MONTHS = 3  # 향후 3개월 미래 파티션 사전 생성

PARTITION_TABLES = [
    "chat_signal_10s",
    "view_metric_timelines",
]

default_args = {
    "owner": "slice-de",
    "retries": 1,
    "retry_delay": timedelta(minutes=5),
}


def _get_month_range(year: int, month: int) -> tuple[str, str]:
    """해당 월의 시작일시와 다음 달 시작일시를 ISO 문자열로 반환"""
    start_dt = datetime(year, month, 1, 0, 0, 0, tzinfo=KST)
    if month == 12:
        end_dt = datetime(year + 1, 1, 1, 0, 0, 0, tzinfo=KST)
    else:
        end_dt = datetime(year, month + 1, 1, 0, 0, 0, tzinfo=KST)
    return start_dt.strftime("%Y-%m-%d %H:%M:%S%z"), end_dt.strftime("%Y-%m-%d %H:%M:%S%z")


def _add_months(year: int, month: int, delta: int) -> tuple[int, int]:
    """연/월에 delta 개월을 더한 (year, month) 반환"""
    total_months = year * 12 + (month - 1) + delta
    new_year = total_months // 12
    new_month = (total_months % 12) + 1
    return new_year, new_month


@dag(
    dag_id="dag_dw_retention_janitor",
    description="홈서버 DW 파티션 수명주기 관리 (90일 초과 파티션 DROP 및 미래 파티션 사전 생성)",
    schedule="0 4 1 * *",  # 매월 1일 새벽 04:00 KST
    start_date=datetime(2026, 10, 1, tzinfo=KST),
    catchup=False,
    default_args=default_args,
    tags=["dw", "janitor", "maintenance", "partition"],
)
def dw_retention_janitor():
    @task
    def precreate_future_partitions() -> list[str]:
        """미래 1~3개월 치 파티션을 미리 생성하여 데이터 적재 실패 방지"""
        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        now = datetime.now(KST)
        created_partitions = []

        with hook.get_conn() as conn:
            with conn.cursor() as cur:
                for delta in range(1, PRECREATE_MONTHS + 1):
                    target_y, target_m = _add_months(now.year, now.month, delta)
                    start_str, end_str = _get_month_range(target_y, target_m)
                    suffix = f"y{target_y}m{target_m:02d}"

                    for base_table in PARTITION_TABLES:
                        part_name = f"{base_table}_{suffix}"
                        sql = f"""
                            CREATE TABLE IF NOT EXISTS {part_name} PARTITION OF {base_table}
                            FOR VALUES FROM ('{start_str}') TO ('{end_str}');
                        """
                        cur.execute(sql)
                        created_partitions.append(part_name)

            conn.commit()

        logger.info(f"[Janitor] 미래 파티션 사전 확인/생성 완료: {len(created_partitions)}개 파티션")
        return created_partitions

    @task
    def drop_expired_partitions() -> list[str]:
        """보존 기간(90일 / 3개월)이 지난 과거 파티션을 DROP하여 디스크 용량 즉시 회수"""
        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        now = datetime.now(KST)
        cutoff_y, cutoff_m = _add_months(now.year, now.month, -RETENTION_MONTHS)
        cutoff_val = cutoff_y * 100 + cutoff_m  # 예: 202607

        dropped_partitions = []

        with hook.get_conn() as conn:
            with conn.cursor() as cur:
                # 정보 스키마에서 파티션 테이블 목록 조회
                for base_table in PARTITION_TABLES:
                    cur.execute(
                        """
                        SELECT inhrelid::regclass::text
                        FROM pg_inherits
                        WHERE inhparent = %s::regclass;
                        """,
                        (base_table,),
                    )
                    partitions = [row[0] for row in cur.fetchall()]

                    for part in partitions:
                        # part 형식: base_table_yYYYYmMM
                        if "_y" in part and "m" in part:
                            try:
                                suffix = part.split("_y")[-1]  # YYYYmMM
                                y_str, m_str = suffix.split("m")
                                part_val = int(y_str) * 100 + int(m_str)

                                # 만료 기준 이전 파티션인 경우 안전하게 DROP
                                if part_val < cutoff_val:
                                    logger.warning(f"[Janitor] 만료 파티션 감지 ➔ DROP TABLE 실행: {part}")
                                    cur.execute(f"DROP TABLE IF EXISTS {part};")
                                    dropped_partitions.append(part)
                            except Exception as parse_err:
                                logger.error(f"[Janitor] 파티션명 파싱 실패 (스킵): {part}, 오류={parse_err}")

            conn.commit()

        logger.info(f"[Janitor] 만료 파티션 정리 완료: 총 {len(dropped_partitions)}개 파티션 DROP 회수")
        return dropped_partitions

    # 태스크 의존성 연결
    precreate_future_partitions() >> drop_expired_partitions()


dw_retention_janitor()
