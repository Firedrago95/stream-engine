#!/usr/bin/env python3
"""
과거 전기간 세션 시청자수 백필(Backfill) 스크립트
- 홈서버 DW(view_metric_timelines) -> OCI DB(analysis_signals_summary.viewer_count)
- 100개 세션 단위 배치 청크 분할 및 트랜잭션 커밋으로 DB 부하 및 락 최소화
"""

import sys
import time
import logging
import psycopg2
from psycopg2.extras import execute_values

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("backfill")

DW_CONFIG = {
    "host": "dw-postgres",
    "port": 5432,
    "dbname": "cheesepick_dw",
    "user": "yonghwa95",
    "password": "ELwkemdaud1528!"
}

OCI_CONFIG = {
    "host": "oci-db-tunnel",
    "port": 5432,
    "dbname": "slicedb",
    "user": "sliceuser",
    "password": "slicepass"
}

BATCH_SESSION_SIZE = 100
INTER_BATCH_SLEEP_SEC = 0.05


def get_target_sessions(oci_conn) -> list[str]:
    """OCI DB에서 viewer_count가 아직 0인 세션 ID 목록을 최신순으로 추출"""
    logger.info("OCI DB에서 viewer_count가 0인 대상 세션 목록 조회 중...")
    with oci_conn.cursor() as cur:
        # 최근 세션부터 우선 복구되도록 timestamp_minute DESC 순 정렬
        cur.execute("""
            SELECT session_id
            FROM analysis_signals_summary
            WHERE viewer_count = 0
            GROUP BY session_id
            ORDER BY max(timestamp_minute) DESC;
        """)
        sessions = [row[0] for row in cur.fetchall()]
    logger.info(f"복구 대상 세션 총 {len(sessions):,}개 확인")
    return sessions


def fetch_dw_metrics(dw_conn, session_ids: list[str]) -> list[tuple]:
    """홈서버 DW에서 대상 세션들의 1분 평균 시청자수 집계 (인덱스 레인지 스캔)"""
    if not session_ids:
        return []
    with dw_conn.cursor() as cur:
        cur.execute("""
            SELECT session_id,
                   date_trunc('minute', recorded_at) AS minute_ts,
                   CAST(ROUND(AVG(viewer_count)) AS INT) AS avg_v
            FROM view_metric_timelines
            WHERE session_id = ANY(%s)
            GROUP BY session_id, 2;
        """, (session_ids,))
        return cur.fetchall()


def update_oci_viewer_counts(oci_conn, records: list[tuple]) -> int:
    """OCI DB에 임시 테이블 생성 후 인덱스를 활용하여 viewer_count 일괄 업데이트"""
    if not records:
        return 0
    with oci_conn.cursor() as cur:
        cur.execute("""
            CREATE TEMP TABLE tmp_batch_vmt (
                session_id varchar(100),
                minute_ts timestamptz,
                avg_v int
            ) ON COMMIT DROP;
        """)
        execute_values(
            cur,
            "INSERT INTO tmp_batch_vmt (session_id, minute_ts, avg_v) VALUES %s;",
            records,
            page_size=2000
        )
        cur.execute("""
            UPDATE analysis_signals_summary AS s
            SET viewer_count = t.avg_v
            FROM tmp_batch_vmt AS t
            WHERE s.session_id = t.session_id
              AND date_trunc('minute', s.timestamp_minute) = t.minute_ts;
        """)
        updated_count = cur.rowcount
    oci_conn.commit()
    return updated_count


def run_backfill():
    start_time = time.time()
    logger.info("=== 전기간 시청자수 백필 작업 시작 ===")

    dw_conn = psycopg2.connect(**DW_CONFIG)
    oci_conn = psycopg2.connect(**OCI_CONFIG)

    try:
        sessions = get_target_sessions(oci_conn)
        total_sessions = len(sessions)
        if total_sessions == 0:
            logger.info("복구할 세션이 없습니다. (모든 세션 정상)")
            return

        total_updated_rows = 0
        total_batches = (total_sessions + BATCH_SESSION_SIZE - 1) // BATCH_SESSION_SIZE

        for batch_idx in range(total_batches):
            chunk = sessions[batch_idx * BATCH_SESSION_SIZE : (batch_idx + 1) * BATCH_SESSION_SIZE]
            t0 = time.time()

            dw_records = fetch_dw_metrics(dw_conn, chunk)
            updated_rows = update_oci_viewer_counts(oci_conn, dw_records)
            total_updated_rows += updated_rows

            elapsed_chunk = time.time() - t0
            processed_count = min((batch_idx + 1) * BATCH_SESSION_SIZE, total_sessions)
            progress_pct = (processed_count / total_sessions) * 100
            total_elapsed = time.time() - start_time

            logger.info(
                f"[진행률 {progress_pct:5.1f}%] 청크 {batch_idx + 1}/{total_batches} 완료 | "
                f"세션 {processed_count:,}/{total_sessions:,}개 | "
                f"이번 청크 {updated_rows:,}행 ({elapsed_chunk:.2f}s) | "
                f"누적 {total_updated_rows:,}행 | 경과 {total_elapsed:.1f}s"
            )

            if INTER_BATCH_SLEEP_SEC > 0:
                time.sleep(INTER_BATCH_SLEEP_SEC)

        logger.info(
            f"=== 백필 작업 완료! 총 {total_sessions:,}개 세션, "
            f"{total_updated_rows:,}행 업데이트 완료 (총 소요 시간: {time.time() - start_time:.1f}초) ==="
        )

    finally:
        dw_conn.close()
        oci_conn.close()


if __name__ == "__main__":
    run_backfill()
