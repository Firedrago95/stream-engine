import sys
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "airflow" / "dags"))

from dag_post_broadcast_pipeline import (
    _build_one_minute_summaries,
    _resolve_highlight_category,
)
from dag_weekly_category_ranking import (
    _compute_ranking_rows,
    _format_view_hours,
    _resolve_icon,
)

KST = ZoneInfo("Asia/Seoul")


def test_build_one_minute_summaries_aggregates_chats_viewers_and_peaks():
    t0 = datetime(2026, 10, 25, 20, 0, 0, tzinfo=KST)
    t0_10s = datetime(2026, 10, 25, 20, 0, 10, tzinfo=KST)
    t1 = datetime(2026, 10, 25, 20, 1, 0, tzinfo=KST)

    buckets = {
        t0: {"chat_count": 20},
        t0_10s: {"chat_count": 40},
        t1: {"chat_count": 100},
    }
    highlights = [
        {
            "start_time": t1,
            "end_time": t1,
        }
    ]
    minute_viewers = {
        t0: 1500,
        t1: 2500,
    }

    rows = _build_one_minute_summaries(
        session_id="sess-1",
        stream_id="stream-1",
        started_at_dt=t0,
        buckets=buckets,
        highlights=highlights,
        minute_viewers=minute_viewers,
    )

    assert len(rows) == 2

    # 20:00 버킷 (NORMAL)
    assert rows[0] == (
        "stream-1",
        "sess-1",
        "NORMAL",
        30,    # avg(20, 40)
        40,    # max(20, 40)
        t0,
        0,     # offset_ms
        1500,  # viewer_count
    )

    # 20:01 버킷 (PEAK)
    assert rows[1] == (
        "stream-1",
        "sess-1",
        "PEAK",
        100,
        100,
        t1,
        60000,
        2500,
    )


def test_resolve_highlight_category_prefers_top_word_then_emote_then_general():
    hl_with_word = {
        "top_tags": {
            "words": [{"w": "레전드", "z": 5.2}],
            "emotes": [{"w": ":짜스:", "u": 10}],
        }
    }
    assert _resolve_highlight_category(hl_with_word) == "레전드"

    hl_with_emote_only = {
        "top_tags": {
            "words": [],
            "emotes": [{"w": ":짜스:", "u": 10}],
        }
    }
    assert _resolve_highlight_category(hl_with_emote_only) == ":짜스:"

    hl_empty = {"top_tags": {}}
    assert _resolve_highlight_category(hl_empty) == "GENERAL"


def test_weekly_category_ranking_computation_and_formatting():
    monday = date(2026, 10, 19)
    current_metrics = [
        ("메이플스토리", 65838),
        ("리그 오브 레전드", 42171),
        ("신규게임", 8500),
    ]
    previous_ranks = {
        "리그 오브 레전드": 1,
        "메이플스토리": 3,
    }

    rows = _compute_ranking_rows(current_metrics, previous_ranks, monday)
    assert len(rows) == 3

    # 1위: 메이플스토리 (3위 -> 1위: up 2)
    assert rows[0] == (
        monday,
        "메이플스토리",
        1,
        "약 6.6만 시간",
        65838,
        "up",
        2,
        "🍁",
    )

    # 2위: 리그 오브 레전드 (1위 -> 2위: down 1)
    assert rows[1] == (
        monday,
        "리그 오브 레전드",
        2,
        "약 4.2만 시간",
        42171,
        "down",
        1,
        "⚔️",
    )

    # 3위: 신규게임 (new)
    assert rows[2] == (
        monday,
        "신규게임",
        3,
        "약 8,500시간",
        8500,
        "new",
        None,
        "🎮",
    )

    assert _format_view_hours(10000) == "약 1.0만 시간"
    assert _resolve_icon(None) == "🎮"
