import gzip
import json
import os
import re
from datetime import datetime, timedelta
from airflow.sdk import dag, task
from airflow.providers.postgres.hooks.postgres import PostgresHook
from collections import defaultdict, Counter
from psycopg2.extras import execute_values

# 채팅 파일이 저장되는 디렉토리 경로
CHAT_DATA_DIR = os.getenv("CHAT_SAMPLE_DIR", "/opt/airflow/data/chat_samples")

# 이모티콘 정규식 패턴
EMOTE_REGEX = re.compile(r"\{:[^:]+:\}")

# 일반 단어 추출 패턴: 2글자 이상 한글/영어/자모
WORD_REGEX = re.compile(r"[가-힣a-zA-Z0-9ㄱ-ㅎㅏ-ㅣ]{2,}")

default_args = {
    "owner": "slice-de",
    "retries": 2,
    "retry_delay": timedelta(minutes=1),
}

@dag(
    dag_id="dag_post_broadcast_pipeline",
    default_args=default_args,
    description="방종 세션 사후 분석 파이프라인",
    schedule="*/10 * * * *",
    start_date=datetime(2026, 10, 24),
    catchup=False,
    max_active_runs=1,
    tags=["post-broadcast", "dw"],
)
def post_broadcast_pipeline():

    @task
    def get_pending_sessions() -> list[dict]:
        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        sql = """
            SELECT session_id, stream_id, streamer_name, started_at, ended_at
            FROM stream_sessions
            WHERE ended_at IS NOT NULL 
              AND ended_at < NOW() - INTERVAL '5 minutes' 
              AND is_analyzed = FALSE
            ORDER BY ended_at ASC
            LIMIT 10;
        """
        records = hook.get_records(sql)
        return [
            {
                "session_id": r[0],
                "stream_id": r[1],
                "streamer_name": r[2],
                "started_at": r[3].isoformat() if r[3] else None,
                "ended_at": r[4].isoformat() if r[4] else None,
            }
            for r in records
        ]

    @task
    def process_chat_to_dw(sessions: list[dict]) -> list[dict]:
        if not sessions:
            return []

        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        conn = hook.get_conn()
        processed_session_ids = []

        try:
            for s in sessions:
                session_id = s["session_id"]
                stream_id = s["stream_id"]
                streamer_name = s["streamer_name"]
                
                # 1. 파일 찾기
                target_file = _find_chat_file(session_id, stream_id, streamer_name)
                if not target_file or not os.path.exists(target_file):
                    # 파일이 없는 방송은 완료 처리하고 스킵
                    processed_session_ids.append(session_id)
                    continue

                # 2. 10초 집계 및 토큰 추출
                buckets, session_tokens = _parse_and_aggregate_chat(target_file)

                # 3. DW 적재 (10초 시그널 & 세션 토큰 요약)
                if buckets:
                    _insert_chat_signals(conn, session_id, buckets)
                _insert_token_summary(conn, session_id, session_tokens)

                processed_session_ids.append(session_id)
            conn.commit()
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
            
        return processed_session_ids
                
    @task
    def mark_sessions_analyzed(processed_session_ids: list[str]):
        if not processed_session_ids:
            return
        
        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        sql = """
            UPDATE stream_sessions
            SET is_analyzed = TRUE
            WHERE session_id = ANY(%s)
              AND ended_at IS NOT NULL;
        """
        hook.run(sql, parameters=(processed_session_ids,))
        
    # 태스크 순서 연결 (1번 -> 2번 -> 3번)
    pending = get_pending_sessions()
    processed = process_chat_to_dw(pending)
    mark_sessions_analyzed(processed)


# ==============================================================================
# 순수 파이썬 헬퍼 함수들 (단일 책임 원칙)
# ==============================================================================

def _find_chat_file(session_id: str, stream_id: str, streamer_name: str) -> str | None:
    """디렉터리 내에서 세션에 맞는 .jsonl.gz 파일을 탐색한다."""
    if not os.path.exists(CHAT_DATA_DIR):
        return None

    for fname in os.listdir(CHAT_DATA_DIR):
        if fname.endswith(".jsonl.gz") and (stream_id in fname or streamer_name in fname or session_id in fname):
            return os.path.join(CHAT_DATA_DIR, fname)
    return None


def _parse_and_aggregate_chat(file_path: str):
    """
    .jsonl.gz 파일을 스트리밍으로 한 줄씩 읽어 10초 버킷 및 토큰을 집계한다.
    """
    buckets = defaultdict(lambda: {
        "chat_count": 0,
        "unique_chatters": set(),
        "emote_count": 0,
        "donation_count": 0,
        "tokens": Counter(),
    })
    session_tokens = Counter()

    with gzip.open(file_path, "rt", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            try:
                msg = json.loads(line)
            except json.JSONDecodeError:
                continue

            if msg.get("type") == "METADATA":
                continue

            raw_ts = msg.get("timestamp")
            if not raw_ts:
                continue

            # 1. 10초 버킷 시간 계산 (초 내림 처리)
            dt = datetime.fromisoformat(raw_ts)
            bucket_second = (dt.second // 10) * 10
            bucket_time = dt.replace(second=bucket_second, microsecond=0)

            content = msg.get("content", "")
            author_id = msg.get("authorId", "")

            # 2. 10초 버킷 기본 지표 누적
            b = buckets[bucket_time]
            b["chat_count"] += 1
            if author_id:
                b["unique_chatters"].add(author_id)

            # 3. 이모티콘 카운트
            emotes = EMOTE_REGEX.findall(content)
            b["emote_count"] += len(emotes)

            # 4. 단어 및 이모티콘 토큰 추출
            words = WORD_REGEX.findall(content)
            tokens = emotes + words

            for t in tokens:
                b["tokens"][t] += 1
                session_tokens[t] += 1

    return buckets, session_tokens


def _insert_chat_signals(conn, session_id: str, buckets: dict):
    """chat_signal_10s 테이블에 10초 시그널을 일괄 멱등 적재한다 (execute_values)."""
    records = []
    for b_time, b in sorted(buckets.items()):
        # 10초 윈도우 내 3회 이상 출현한 단어만 보관 (노이즈 및 DB 스토리지 절약)
        top_10s_tokens = {k: v for k, v in b["tokens"].items() if v >= 3}

        records.append((
            session_id,
            b_time,
            b["chat_count"],
            len(b["unique_chatters"]),
            b["emote_count"],
            b["donation_count"],
            json.dumps(top_10s_tokens, ensure_ascii=False)
        ))

    sql = """
        INSERT INTO chat_signal_10s (
            session_id, bucket_time, chat_count, unique_chatter,
            emote_count, donation_count, top_tokens
        ) VALUES %s
        ON CONFLICT (session_id, bucket_time) DO UPDATE 
        SET chat_count = EXCLUDED.chat_count,
            unique_chatter = EXCLUDED.unique_chatter,
            emote_count = EXCLUDED.emote_count,
            donation_count = EXCLUDED.donation_count,
            top_tokens = EXCLUDED.top_tokens;
    """
    cursor = conn.cursor()
    execute_values(cursor, sql, records)


def _insert_token_summary(conn, session_id: str, session_tokens: Counter):
    """session_token_summary 테이블에 세션 전체 Top 200 단어를 단일 행으로 적재한다."""
    top_200 = dict(session_tokens.most_common(200))

    sql = """
        INSERT INTO session_token_summary (session_id, token_stats)
        VALUES (%s, %s)
        ON CONFLICT (session_id) DO UPDATE 
        SET token_stats = EXCLUDED.token_stats,
            created_at = CURRENT_TIMESTAMP;
    """
    cursor = conn.cursor()
    cursor.execute(sql, (session_id, json.dumps(top_200, ensure_ascii=False)))


# Airflow가 이 DAG 객체를 찾아서 등록하도록 함수 호출
post_broadcast_pipeline_dag = post_broadcast_pipeline()

