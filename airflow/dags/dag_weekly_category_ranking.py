import logging
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.sdk import dag, task
from psycopg2.extras import execute_values

logger = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")

DEFAULT_TOP_LIMIT = 6
SAMPLES_PER_HOUR = 120.0  # 30초 폴링 기준 시간당 샘플 수 (3600 / 30)

ICON_MAP = {
    "talk": "💬",
    "소통/토크": "💬",
    "음악/노래": "🎤",
    "메이플스토리": "🍁",
    "마인크래프트": "⛏️",
    "종합 게임": "🎮",
    "리그 오브 레전드": "⚔️",
    "오버워치": "🎯",
    "이터널 리턴": "🧪",
    "로스트아크": "🛡️",
    "발로란트": "🎯",
    "PUBG: 배틀그라운드": "🪂",
}

default_args = {
    "owner": "slice-de",
    "retries": 2,
    "retry_delay": timedelta(minutes=2),
}


def _format_view_hours(exact_hours: int) -> str:
    if exact_hours >= 10_000:
        man_unit = exact_hours / 10000.0
        return f"약 {man_unit:.1f}만 시간"
    return f"약 {exact_hours:,}시간"


def _resolve_icon(category_name: str | None) -> str:
    if not category_name:
        return "🎮"
    return ICON_MAP.get(category_name, "🎮")


def _compute_ranking_rows(
    current_metrics: list[tuple[str, int]],
    previous_ranks: dict[str, int],
    week_start_date: date,
) -> list[tuple]:
    is_initial = len(previous_ranks) == 0
    rows = []

    for idx, (category_name, exact_hours) in enumerate(current_metrics):
        current_rank = idx + 1
        exact_h = int(exact_hours or 0)

        change_type = "same"
        change_value = None

        if is_initial:
            change_type = "same"
        elif category_name not in previous_ranks:
            change_type = "new"
        else:
            prev_rank = previous_ranks[category_name]
            if current_rank < prev_rank:
                change_type = "up"
                change_value = prev_rank - current_rank
            elif current_rank > prev_rank:
                change_type = "down"
                change_value = current_rank - prev_rank
            else:
                change_type = "same"

        rows.append((
            week_start_date,
            category_name,
            current_rank,
            _format_view_hours(exact_h),
            exact_h,
            change_type,
            change_value,
            _resolve_icon(category_name),
        ))

    return rows


@dag(
    dag_id="dag_weekly_category_ranking",
    default_args=default_args,
    description="주간 인기 카테고리 시청시간 랭킹 집계 (홈서버 DW -> OCI 서빙 DB 동기화)",
    schedule="0 6 * * 1",
    start_date=datetime(2026, 10, 24, tzinfo=KST),
    catchup=False,
    max_active_runs=1,
    tags=["weekly", "dw", "category", "oci-sync"],
)
def weekly_category_ranking_pipeline():

    @task
    def calculate_and_sync_weekly_ranking() -> int:
        now_kst = datetime.now(tz=KST)
        current_monday = now_kst.date() - timedelta(days=now_kst.weekday())
        since_dt = now_kst - timedelta(days=7)

        dw_hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        dw_sql = """
            SELECT
                s.category_name,
                CAST(ROUND(SUM(v.viewer_count) / %s) AS BIGINT) AS exact_hours
            FROM view_metric_timelines v
            INNER JOIN stream_session_segments s
                ON v.session_id = s.session_id
               AND v.recorded_at >= s.started_at
               AND (s.ended_at IS NULL OR v.recorded_at < s.ended_at)
            WHERE v.recorded_at >= %s
              AND s.category_name IS NOT NULL
              AND s.category_name != ''
              AND LOWER(s.category_name) != 'talk'
            GROUP BY s.category_name
            HAVING SUM(v.viewer_count) > 0
            ORDER BY exact_hours DESC
            LIMIT %s;
        """
        records = dw_hook.get_records(dw_sql, parameters=(SAMPLES_PER_HOUR, since_dt, DEFAULT_TOP_LIMIT))
        current_metrics = [(str(r[0]), int(r[1])) for r in records if r[0]]
        if not current_metrics:
            logger.warning("[주간카테고리] 최근 7일간 집계된 카테고리 시청 지표가 없습니다.")
            return 0

        oci_hook = PostgresHook(postgres_conn_id="slice_oci_postgres")
        oci_conn = oci_hook.get_conn()
        try:
            with oci_conn.cursor() as cur:
                cur.execute("SELECT MAX(week_start_date) FROM weekly_category_rankings;")
                row = cur.fetchone()
                latest_date = row[0] if row and row[0] else None

                previous_ranks: dict[str, int] = {}
                if latest_date is not None:
                    target_prev_date = (
                        current_monday - timedelta(weeks=1)
                        if latest_date == current_monday
                        else latest_date
                    )
                    cur.execute(
                        """
                        SELECT category_name, rank
                        FROM weekly_category_rankings
                        WHERE week_start_date = %s
                        ORDER BY rank ASC;
                        """,
                        (target_prev_date,),
                    )
                    previous_ranks = {str(cat): int(rnk) for cat, rnk in cur.fetchall()}

                ranking_rows = _compute_ranking_rows(
                    current_metrics=current_metrics,
                    previous_ranks=previous_ranks,
                    week_start_date=current_monday,
                )

                cur.execute(
                    "DELETE FROM weekly_category_rankings WHERE week_start_date = %s;",
                    (current_monday,),
                )
                execute_values(
                    cur,
                    """
                    INSERT INTO weekly_category_rankings (
                        week_start_date, category_name, rank,
                        accumulated_view_hours, exact_hours,
                        change_type, change_value, icon
                    ) VALUES %s;
                    """,
                    ranking_rows,
                )

            oci_conn.commit()
            logger.info(
                f"[주간카테고리] 주간 랭킹 집계 및 OCI 서빙 DB 적재 완료: "
                f"week_start_date={current_monday}, 건수={len(ranking_rows)}"
            )
            return len(ranking_rows)
        except Exception:
            oci_conn.rollback()
            raise
        finally:
            oci_conn.close()

    calculate_and_sync_weekly_ranking()


weekly_category_ranking_pipeline()
