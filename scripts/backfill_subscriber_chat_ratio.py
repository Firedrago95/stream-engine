#!/usr/bin/env python3
"""
어제/오늘 방종 세션 구독자 채팅 비율(subscriber_chat_ratio) 백필 스크립트
- /opt/airflow/data/chat_samples/archive 및 /opt/airflow/data/chat_samples 내 .jsonl.gz 파일 스캔
- isSubscriber 카운트 후 DW 및 OCI 서빙 DB에 batch update
"""

import glob
import gzip
import json
import logging
import os
import re
import sys
from collections import defaultdict
import psycopg2
from psycopg2.extras import execute_values

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger("backfill_sub_ratio")

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

ARCHIVE_DIR = "/opt/airflow/data/chat_samples/archive"
SAMPLE_DIR = "/opt/airflow/data/chat_samples"


def find_session_files() -> dict[str, list[str]]:
    pattern = re.compile(r"^(\d+)(?:_part\d+)?\.jsonl\.gz$")
    session_files = defaultdict(list)

    all_files = glob.glob(f"{ARCHIVE_DIR}/*.jsonl.gz") + glob.glob(f"{SAMPLE_DIR}/*.jsonl.gz")
    # 중복 제거 (정규화된 경로)
    seen = set()
    for fpath in all_files:
        if fpath in seen:
            continue
        seen.add(fpath)
        fname = os.path.basename(fpath)
        m = pattern.match(fname)
        if m:
            session_id = m.group(1)
            session_files[session_id].append(fpath)

    return session_files


def calculate_subscriber_ratio(file_paths: list[str]) -> tuple[int, int, float | None]:
    total_chats = 0
    subscriber_chats = 0

    for path in file_paths:
        try:
            with gzip.open(path, "rt", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    if not line.strip():
                        continue
                    try:
                        msg = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if msg.get("type") == "METADATA":
                        continue
                    total_chats += 1
                    if msg.get("isSubscriber") is True:
                        subscriber_chats += 1
        except Exception as e:
            logger.warning(f"파일 읽기 오류 ({path}): {e}")

    ratio = round((subscriber_chats / total_chats) * 100, 1) if total_chats > 0 else None
    return total_chats, subscriber_chats, ratio


def main():
    logger.info("구독자 채팅 비율 백필 시작...")
    session_files = find_session_files()
    logger.info(f"발견된 세션 수: {len(session_files)}개")

    results = []
    for idx, (session_id, fpaths) in enumerate(session_files.items(), 1):
        total, sub, ratio = calculate_subscriber_ratio(fpaths)
        if ratio is not None:
            results.append((session_id, ratio, total, sub))
        if idx % 50 == 0 or idx == len(session_files):
            logger.info(f"[{idx}/{len(session_files)}] 파싱 진행 중... (유효 세션: {len(results)}건)")

    if not results:
        logger.warning("업데이트할 구독자 비율 데이터가 없습니다.")
        return

    logger.info(f"총 {len(results)}건의 세션 구독자 비율 계산 완료. DB 업데이트 진행...")

    # 1. DW 업데이트
    try:
        dw_conn = psycopg2.connect(**DW_CONFIG)
        with dw_conn.cursor() as cur:
            # execute_values를 활용한 일괄 UPDATE
            update_data = [(r[1], r[0]) for r in results]
            execute_values(
                cur,
                """
                UPDATE stream_sessions AS s
                SET subscriber_chat_ratio = v.ratio
                FROM (VALUES %s) AS v(ratio, session_id)
                WHERE s.session_id = v.session_id;
                """,
                update_data,
                page_size=500
            )
            dw_conn.commit()
            logger.info(f"[DW] stream_sessions 업데이트 완료 ({len(results)}건 매칭 시도)")
        dw_conn.close()
    except Exception as e:
        logger.error(f"[DW] 업데이트 실패: {e}")

    # 2. OCI Hot DB 업데이트
    try:
        oci_conn = psycopg2.connect(**OCI_CONFIG)
        with oci_conn.cursor() as cur:
            update_data = [(r[1], r[0]) for r in results]
            execute_values(
                cur,
                """
                UPDATE stream_sessions AS s
                SET subscriber_chat_ratio = v.ratio
                FROM (VALUES %s) AS v(ratio, session_id)
                WHERE s.session_id = v.session_id;
                """,
                update_data,
                page_size=500
            )
            oci_conn.commit()
            logger.info(f"[OCI] stream_sessions 업데이트 완료 ({len(results)}건 매칭 시도)")
        oci_conn.close()
    except Exception as e:
        logger.error(f"[OCI] 업데이트 실패: {e}")

    # 샘플 상위 5건 로깅
    logger.info("=== 백필 샘플 결과 ===")
    for r in results[:5]:
        logger.info(f"세션 {r[0]}: 전체 {r[2]}건 중 구독자 {r[3]}건 ({r[1]}%)")

    logger.info("구독자 채팅 비율 백필 완료!")


if __name__ == "__main__":
    main()
