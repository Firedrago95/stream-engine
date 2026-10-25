import gzip
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import MagicMock
from zoneinfo import ZoneInfo

from airflow.models import DagBag

DAGS_DIR = str(Path(__file__).resolve().parents[2] / "airflow" / "dags")
if DAGS_DIR not in sys.path:
    sys.path.insert(0, DAGS_DIR)

import dag_post_broadcast_pipeline
from dag_post_broadcast_pipeline import (
    _detect_highlight_peaks,
    _detect_highlights_v2,
    _find_chat_file,
    _insert_chat_signals,
    _insert_token_summary,
    _parse_and_aggregate_chat,
    _replace_session_highlights,
    _update_session_analysis_state,
)

KST = ZoneInfo("Asia/Seoul")


def test_dag_integrity():
    """Layer 1: DAG 파싱 무결성 및 태스크 토폴로지 검증."""
    dag_bag = DagBag(dag_folder=DAGS_DIR)

    assert len(dag_bag.import_errors) == 0, f"Import errors: {dag_bag.import_errors}"

    dag = dag_bag.dags.get("dag_post_broadcast_pipeline")
    assert dag is not None
    assert dag.catchup is False
    dag.validate()

    t1 = dag.get_task("get_pending_sessions")
    t2 = dag.get_task("process_chat_to_dw")

    assert t2 in t1.downstream_list


def test_parse_and_aggregate_chat(tmp_path):
    """Layer 2: .jsonl.gz 파싱, 고유 유저 기준 토큰 카운트, 이모티콘 분리 및 float 타임스탬프 검증."""
    sample_file = tmp_path / "sample_chat.jsonl.gz"
    epoch_12_00_25 = datetime(2026, 10, 24, 12, 0, 25, tzinfo=KST).timestamp()
    chat_logs = [
        {"type": "METADATA", "version": "1.0"},
        {"timestamp": "2026-10-24T12:00:13", "authorId": "user1", "content": "와 대박 {:redLight:} ㅋㅋㅋㅋ GG"},
        {"timestamp": "2026-10-24T12:00:18", "authorId": "user2", "content": "대박 대박 대박 레전드으으으"},
        {"timestamp": epoch_12_00_25, "authorId": "user1", "content": "다음 버킷 대박"},
        "corrupted json line",
    ]

    with gzip.open(sample_file, "wt", encoding="utf-8") as f:
        for item in chat_logs:
            if isinstance(item, dict):
                f.write(json.dumps(item) + "\n")
            else:
                f.write(item + "\n")

    buckets, session_tokens, truncated = _parse_and_aggregate_chat(str(sample_file))
    assert truncated is False

    bucket_10s = datetime(2026, 10, 24, 12, 0, 10, tzinfo=KST)
    bucket_20s = datetime(2026, 10, 24, 12, 0, 20, tzinfo=KST)

    assert bucket_10s in buckets
    assert bucket_20s in buckets

    b1 = buckets[bucket_10s]
    assert b1["chat_count"] == 2
    assert b1["unique_chatters"] == {"user1", "user2"}
    assert b1["emote_count"] == 1
    assert b1["tokens"][":redLight:"] == 1
    assert "redlight" not in b1["tokens"]
    assert b1["tokens"]["대박"] == 2
    assert b1["tokens"]["레전드으으"] == 1
    assert "ㅋㅋ" not in b1["tokens"]
    assert "gg" not in b1["tokens"]

    b2 = buckets[bucket_20s]
    assert b2["chat_count"] == 1
    assert b2["unique_chatters"] == {"user1"}
    assert b2["tokens"]["대박"] == 1

    assert session_tokens["대박"] == 3
    assert session_tokens[":redLight:"] == 1


def test_parse_truncated_gzip_graceful_recovery(tmp_path):
    """잘린 .jsonl.gz 파일(EOFError) 발생 시 읽은 구간까지 보존하고 truncated=True를 반환하는지 검증."""
    valid_gz = tmp_path / "valid.jsonl.gz"
    lines = [
        {"timestamp": "2026-10-24T12:00:13", "authorId": "u1", "content": "정상라인 대박"},
        {"timestamp": "2026-10-24T12:00:18", "authorId": "u2", "content": "정상라인2 대박"},
    ]
    with gzip.open(valid_gz, "wt", encoding="utf-8") as f:
        for item in lines:
            f.write(json.dumps(item) + "\n")

    raw_bytes = valid_gz.read_bytes()
    truncated_gz = tmp_path / "truncated.jsonl.gz"
    # 마지막 4바이트(gzip 푸터) 절단
    truncated_gz.write_bytes(raw_bytes[:-4])

    buckets, _, truncated = _parse_and_aggregate_chat(str(truncated_gz))
    assert truncated is True
    assert len(buckets) == 1


def test_detect_highlights_v2_consecutive_and_ranking():
    """Layer 2: v2.1 순수 피크 탐지 함수 및 태그 결합 검증."""
    base_dt = datetime(2026, 10, 24, 18, 0, 0, tzinfo=KST)
    buckets = {}

    for i in range(180):
        t_dt = base_dt + timedelta(seconds=10 * i)
        if i in (59, 60, 61):
            c = 25 if i == 60 else 15
            u = 18 if i == 60 else 12
            word = "역전"
        elif i in (67, 68, 69):
            c = 45 if i == 68 else 22
            u = 32 if i == 68 else 16
            word = "한판승"
        else:
            c = 4
            u = 4
            word = "평소단어"

        chatters = {f"u_{k}" for k in range(u)}
        word_users = defaultdict(set)
        word_users[word] = set(chatters)
        emote_users = defaultdict(set)
        if i == 68:
            emote_users[":d_108:"] = {f"u_{k}" for k in range(6)}

        buckets[t_dt] = {
            "chat_count": c,
            "unique_chatters": chatters,
            "emote_count": len(emote_users.get(":d_108:", set())),
            "donation_count": 0,
            "tokens": Counter({word: u}),
            "word_users": word_users,
            "emote_users": emote_users,
        }

    # 1. 순수 배열 기반 _detect_highlight_peaks 직접 호출 검증
    chats_arr = [buckets[base_dt + timedelta(seconds=10 * i)]["chat_count"] for i in range(180)]
    users_arr = [len(buckets[base_dt + timedelta(seconds=10 * i)]["unique_chatters"]) for i in range(180)]
    peaks_only, _, sig_only, valid_only = _detect_highlight_peaks(chats_arr, users_arr)
    assert len(peaks_only) == 2
    assert valid_only == 180
    assert sig_only >= 1.0

    # 2. 전체 파이프라인(_detect_highlights_v2) 호출 검증
    highlights, sigma_session, valid_cnt = _detect_highlights_v2(
        buckets=buckets,
        session_started_at=base_dt.isoformat(),
    )

    assert valid_cnt == 180
    assert sigma_session == sig_only
    assert len(highlights) == 2

    first = highlights[0]
    second = highlights[1]
    assert first["rank_in_session"] == 1
    assert first["peak_chats"] == 45
    assert first["llr_score"] > second["llr_score"]
    assert first["start_offset_ms"] <= first["peak_offset_ms"] <= first["end_offset_ms"]
    assert first["top_tags"]["words"][0]["w"] == "한판승"
    assert first["top_tags"]["emotes"][0]["w"] == ":d_108:"

    assert second["rank_in_session"] == 2
    assert second["peak_chats"] == 25
    assert second["top_tags"]["words"][0]["w"] == "역전"


def test_insert_chat_signals_filters_noise(monkeypatch):
    """10초 윈도우 내 고유 유저 2명 이상(v >= 2) 단어만 보관되는지 검증."""
    mock_execute_values = MagicMock()
    monkeypatch.setattr(dag_post_broadcast_pipeline, "execute_values", mock_execute_values)

    mock_conn = MagicMock()
    bucket_time = datetime(2026, 10, 24, 12, 0, 10, tzinfo=KST)
    buckets = {
        bucket_time: {
            "chat_count": 5,
            "unique_chatters": {"user1", "user2"},
            "emote_count": 0,
            "donation_count": 0,
            "tokens": Counter({"대박": 3, "이인단어": 2, "일회성단어": 1}),
        }
    }

    _insert_chat_signals(mock_conn, "test_session", buckets)

    mock_execute_values.assert_called_once()
    records = mock_execute_values.call_args[0][2]
    assert len(records) == 1

    top_tokens = json.loads(records[0][6])
    assert top_tokens == {"대박": 3, "이인단어": 2}
    assert "일회성단어" not in top_tokens


def test_insert_token_summary():
    """세션 토큰 요약(5회 이상) 적재 쿼리 검증."""
    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

    session_tokens = Counter({"대박": 100, "레전드": 50, "희귀": 2})
    _insert_token_summary(mock_conn, "test_session", session_tokens)

    mock_cursor.execute.assert_called_once()
    params = mock_cursor.execute.call_args[0][1]

    assert params[0] == "test_session"
    loaded_json = json.loads(params[1])
    assert loaded_json == {"대박": 100, "레전드": 50}


def test_replace_highlights_and_update_state(monkeypatch):
    """하이라이트 DELETE 후 INSERT 멱등성 및 세션 상태 UPDATE 검증."""
    mock_execute_values = MagicMock()
    monkeypatch.setattr(dag_post_broadcast_pipeline, "execute_values", mock_execute_values)

    mock_conn = MagicMock()
    mock_cursor = MagicMock()
    mock_conn.cursor.return_value.__enter__.return_value = mock_cursor

    now_dt = datetime(2026, 10, 24, 19, 0, 0, tzinfo=KST)
    highlights = [
        {
            "rank_in_session": 1,
            "start_time": now_dt,
            "peak_time": now_dt,
            "end_time": now_dt,
            "start_offset_ms": 1000,
            "peak_offset_ms": 1000,
            "end_offset_ms": 1000,
            "peak_chats": 30,
            "peak_unique_users": 20,
            "baseline_chats": 5.0,
            "sigma_local": 1.2,
            "peak_z": 4.1,
            "llr_score": 25.5,
            "top_tags": {"words": [{"w": "다시다", "u": 7, "bg_u": 1, "z": 3.8}], "emotes": []},
            "params_json": {"k_threshold": 2.8},
        }
    ]

    _replace_session_highlights(mock_conn, "sess_1", "stream_1", "v2.1", highlights)
    mock_cursor.execute.assert_called_once()
    mock_execute_values.assert_called_once()

    _update_session_analysis_state(mock_conn, "sess_1", 1.45, 360, "v2.1", "v2.1")
    assert mock_cursor.execute.call_count == 2


def test_find_chat_file(tmp_path, monkeypatch):
    """채팅 파일 탐색 로직 검증: session_id 우선, 단일 후보 폴백 허용 및 다중 후보 모호성 차단 검증."""
    monkeypatch.setattr(dag_post_broadcast_pipeline, "CHAT_DATA_DIR", str(tmp_path))

    file_with_session = tmp_path / "sample_침착맨_session_5678.jsonl.gz"
    file_with_session.touch()

    yesterday_file = tmp_path / "sample_침착맨_20261023_190000.jsonl.gz"
    today_file = tmp_path / "sample_침착맨_20261024_190000.jsonl.gz"
    yesterday_file.touch()
    today_file.touch()

    # 1순위: session_id로 정확히 탐색
    found = _find_chat_file("5678", "침착맨", "2026-10-24T19:00:00+09:00")
    assert found == str(file_with_session)

    # 2순위: 후보가 정확히 1개일 때 폴백 매칭 성공
    found_today = _find_chat_file("unknown_id", "침착맨", "2026-10-24T19:00:00+09:00")
    assert found_today == str(today_file)

    # 3순위: 같은 시간(19시)에 2개 파일이 존재하면 모호성으로 인해 None 반환 (오매칭 차단)
    today_part2 = tmp_path / "sample_침착맨_20261024_193000.jsonl.gz"
    today_part2.touch()
    ambiguous = _find_chat_file("unknown_id", "침착맨", "2026-10-24T19:00:00+09:00")
    assert ambiguous is None

    # 4. .tmp 파일은 후보에서 제외되는지 검증
    tmp_writing = tmp_path / "sample_풍월량_9999_20261024_190000.jsonl.gz.tmp"
    tmp_writing.touch()
    not_found = _find_chat_file("9999", "풍월량", "2026-10-24T19:00:00+09:00")
    assert not_found is None
