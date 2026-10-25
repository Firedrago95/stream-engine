import gzip
import json
import sys
from pathlib import Path
from collections import Counter
from datetime import datetime
from unittest.mock import MagicMock
from airflow.models import DagBag

# airflow/dags 디렉터리를 sys.path에 등록하여 DAG 모듈 import
DAGS_DIR = str(Path(__file__).resolve().parents[2] / "airflow" / "dags")
if DAGS_DIR not in sys.path:
    sys.path.insert(0, DAGS_DIR)

import dag_post_broadcast_pipeline
from dag_post_broadcast_pipeline import (
    _parse_and_aggregate_chat,
    _find_chat_file,
    _insert_chat_signals,
    _insert_token_summary,
)


def test_dag_integrity():
    """Layer 1: DAG 파싱 무결성 및 구조 검증."""
    dag_bag = DagBag(dag_folder=DAGS_DIR)

    # 1. 파싱 및 문법 에러 0건 확인
    assert len(dag_bag.import_errors) == 0, f"Import errors: {dag_bag.import_errors}"

    # 2. DAG 객체 가져오기 및 유효성 검증
    dag = dag_bag.dags.get("dag_post_broadcast_pipeline")
    assert dag is not None
    assert dag.catchup is False
    dag.validate()

    # 3. 태스크 선후관계 검증 (T1 -> T2 -> T3)
    t1 = dag.get_task("get_pending_sessions")
    t2 = dag.get_task("process_chat_to_dw")
    t3 = dag.get_task("mark_sessions_analyzed")

    assert t2 in t1.downstream_list
    assert t3 in t2.downstream_list


def test_parse_and_aggregate_chat(tmp_path):
    """Layer 2: .jsonl.gz 파싱 및 10초 내림 버킷팅, 토큰 추출 로직 검증."""
    # 1. 가상 채팅 .jsonl.gz 파일 생성
    sample_file = tmp_path / "sample_chat.jsonl.gz"
    chat_logs = [
        # METADATA는 스킵되어야 함
        {"type": "METADATA", "version": "1.0"},
        # 12:00:13 -> 12:00:10 버킷
        {"timestamp": "2026-10-24T12:00:13", "authorId": "user1", "content": "와 대박 {:redLight:}"},
        # 12:00:18 -> 12:00:10 버킷 (동일 버킷 누적, user2 추가)
        {"timestamp": "2026-10-24T12:00:18", "authorId": "user2", "content": "대박 대박 레전드"},
        # 12:00:25 -> 12:00:20 버킷 (다음 버킷으로 분리, user1 재등장)
        {"timestamp": "2026-10-24T12:00:25", "authorId": "user1", "content": "다음 버킷 대박"},
        # 손상된 라인 방어
        "corrupted json line",
    ]

    with gzip.open(sample_file, "wt", encoding="utf-8") as f:
        for item in chat_logs:
            if isinstance(item, dict):
                f.write(json.dumps(item) + "\n")
            else:
                f.write(item + "\n")

    # 2. 순수 함수 실행
    buckets, session_tokens = _parse_and_aggregate_chat(str(sample_file))

    # 3. 10초 버킷 검증
    bucket_10s = datetime(2026, 10, 24, 12, 0, 10)
    bucket_20s = datetime(2026, 10, 24, 12, 0, 20)

    assert bucket_10s in buckets
    assert bucket_20s in buckets

    # 12:00:10 버킷 검증
    b1 = buckets[bucket_10s]
    assert b1["chat_count"] == 2
    assert b1["unique_chatters"] == {"user1", "user2"}
    assert b1["emote_count"] == 1
    assert b1["tokens"]["{:redLight:}"] == 1
    assert b1["tokens"]["대박"] == 3
    assert b1["tokens"]["레전드"] == 1

    # 12:00:20 버킷 검증
    b2 = buckets[bucket_20s]
    assert b2["chat_count"] == 1
    assert b2["unique_chatters"] == {"user1"}
    assert b2["tokens"]["대박"] == 1

    # 세션 전체 토큰 요약 검증 (대박 총 4회)
    assert session_tokens["대박"] == 4
    assert session_tokens["{:redLight:}"] == 1


def test_insert_chat_signals_filters_noise(monkeypatch):
    """10초 윈도우 내 3회 미만 단어가 노이즈로 필터링되는지 검증."""
    mock_execute_values = MagicMock()
    monkeypatch.setattr(dag_post_broadcast_pipeline, "execute_values", mock_execute_values)

    mock_conn = MagicMock()
    bucket_time = datetime(2026, 10, 24, 12, 0, 10)
    buckets = {
        bucket_time: {
            "chat_count": 5,
            "unique_chatters": {"user1", "user2"},
            "emote_count": 0,
            "donation_count": 0,
            "tokens": Counter({"대박": 3, "노이즈단어": 2, "일회성단어": 1}),
        }
    }

    _insert_chat_signals(mock_conn, "test_session", buckets)

    # execute_values 호출 인자 확인
    mock_execute_values.assert_called_once()
    call_args = mock_execute_values.call_args[0]
    records = call_args[2]
    assert len(records) == 1

    record = records[0]
    assert record[0] == "test_session"
    assert record[1] == bucket_time
    assert record[2] == 5
    assert record[3] == 2

    # 3회 이상인 "대박"만 JSON에 포함되고 2회 이하인 노이즈는 제외되었는지 확인
    top_tokens = json.loads(record[6])
    assert "대박" in top_tokens
    assert top_tokens["대박"] == 3
    assert "노이즈단어" not in top_tokens
    assert "일회성단어" not in top_tokens


def test_insert_token_summary():
    """세션 토큰 Top 200 요약 적재 쿼리 검증."""
    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_conn.cursor.return_value = mock_cursor

    session_tokens = Counter({"대박": 100, "레전드": 50})
    _insert_token_summary(mock_conn, "test_session", session_tokens)

    mock_cursor.execute.assert_called_once()
    sql_args = mock_cursor.execute.call_args[0]
    params = sql_args[1]

    assert params[0] == "test_session"
    loaded_json = json.loads(params[1])
    assert loaded_json == {"대박": 100, "레전드": 50}


def test_find_chat_file(tmp_path, monkeypatch):
    """채팅 파일 탐색 로직 검증: session_id 우선 매칭 및 streamer_name + 시간 교차 검증."""
    monkeypatch.setattr(dag_post_broadcast_pipeline, "CHAT_DATA_DIR", str(tmp_path))

    # 1. session_id 포함 파일 생성
    file_with_session = tmp_path / "sample_침착맨_session_5678.jsonl.gz"
    file_with_session.touch()

    # 2. 동일 스트리머의 어제 방송(10/23)과 오늘 방송(10/24) 파일 생성
    yesterday_file = tmp_path / "sample_침착맨_20261023_190000.jsonl.gz"
    today_file = tmp_path / "sample_침착맨_20261024_190000.jsonl.gz"
    yesterday_file.touch()
    today_file.touch()

    # 1순위: session_id로 정확히 탐색
    found = _find_chat_file("5678", "침착맨", "2026-10-24T19:00:00+09:00")
    assert found == str(file_with_session)

    # 2순위: session_id가 파일명에 없을 때, 오늘 시작 시각(10/24 19시)과 스트리머명으로 오늘 파일만 매칭 (어제 파일 오탐 방어)
    found_today = _find_chat_file("unknown_id", "침착맨", "2026-10-24T19:00:00+09:00")
    assert found_today == str(today_file)

    # 3. 없는 세션 탐색 시 None 반환
    not_found = _find_chat_file("9999", "풍월량", "2026-10-24T19:00:00+09:00")
    assert not_found is None
