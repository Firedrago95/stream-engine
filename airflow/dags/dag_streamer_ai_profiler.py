import json
import logging
import os
import re
import urllib.parse
import urllib.request
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.sdk import dag, task
from psycopg2.extras import execute_values

logger = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")

default_args = {
    "owner": "slice-de",
    "retries": 1,
    "retry_delay": timedelta(minutes=2),
}


@dag(
    dag_id="dag_streamer_ai_profiler",
    default_args=default_args,
    description="스트리머 5대 축(스케줄, 콘텐츠, 화력, 팬덤, 밈) AI 프로파일링 파이프라인",
    schedule="0 4 * * 1",  # 매주 월요일 새벽 4시 정기 갱신
    start_date=datetime(2026, 10, 24),
    catchup=False,
    max_active_runs=1,
    tags=["streamer", "ai-profiler", "gemini", "dw"],
)
def streamer_ai_profiler():

    @task
    def get_target_streamers() -> list[dict]:
        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        conn = hook.get_conn()
        try:
            with conn.cursor() as cur:
                # 최근 30일간 3회 이상 방송한 활성 스트리머 상위 300명
                cur.execute(
                    """
                    SELECT stream_id, streamer_name, count(*) as session_count
                    FROM stream_sessions
                    WHERE started_at >= NOW() - INTERVAL '30 days'
                      AND streamer_name IS NOT NULL
                      AND trim(streamer_name) != ''
                    GROUP BY stream_id, streamer_name
                    HAVING count(*) >= 3
                    ORDER BY session_count DESC
                    LIMIT 300;
                    """
                )
                rows = cur.fetchall()
                logger.info(f"[AI Profiler] 최근 30일 활성 대상 스트리머 {len(rows)}명 추출 완료")
                return [
                    {"stream_id": r[0], "streamer_name": r[1], "session_count": r[2]}
                    for r in rows
                ]
        finally:
            conn.close()

    @task
    def generate_ai_profiles(streamers: list[dict]):
        if not streamers:
            logger.info("[AI Profiler] 처리할 스트리머가 없습니다.")
            return

        dw_hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        dw_conn = dw_hook.get_conn()

        profiles_to_upsert = []

        try:
            for s in streamers:
                stream_id = s["stream_id"]
                streamer_name = s["streamer_name"]

                try:
                    fact_sheet = _extract_streamer_fact_sheet(dw_conn, stream_id, streamer_name)
                    if not fact_sheet:
                        continue

                    profile_data = _generate_profile_with_ai(fact_sheet, streamer_name)
                    if profile_data:
                        profiles_to_upsert.append((
                            stream_id,
                            profile_data["summary_line"][:255],
                            profile_data["broadcasting_style"],
                            json.dumps(profile_data.get("core_games", [])),
                            profile_data["fandom_traits"],
                            json.dumps(profile_data.get("popular_memes", [])),
                            fact_sheet.get("total_sessions", 0),
                        ))
                except Exception as ex:
                    logger.warning(f"[AI Profiler] {streamer_name}({stream_id}) 프로필 생성 실패: {ex}")

            logger.info(f"[AI Profiler] 총 {len(profiles_to_upsert)}건 프로필 데이터 생성 완료. DB 적재 진행...")

            if profiles_to_upsert:
                # 1. DW 저장
                _upsert_profiles(dw_conn, profiles_to_upsert)
                dw_conn.commit()

                # 2. OCI 서빙 DB 동기화
                _sync_profiles_to_oci(profiles_to_upsert)

        finally:
            dw_conn.close()

    target_streamers = get_target_streamers()
    generate_ai_profiles(target_streamers)


def _extract_streamer_fact_sheet(conn, stream_id: str, streamer_name: str) -> dict | None:
    with conn.cursor() as cur:
        # 1. 세션 기본 통계 (스케줄, 시간대, 평균/피크 시청자, 구독자 비율)
        cur.execute(
            """
            SELECT 
                count(*) as total_sessions,
                round(avg(EXTRACT(EPOCH FROM (ended_at - started_at))/3600)::numeric, 1) as avg_duration_hours,
                round(max(EXTRACT(EPOCH FROM (ended_at - started_at))/3600)::numeric, 1) as max_duration_hours,
                round(avg(average_viewer_count)::numeric, 0) as avg_viewers,
                max(peak_viewers) as max_peak_viewers,
                round(avg(subscriber_chat_ratio)::numeric, 1) as avg_sub_ratio,
                mode() WITHIN GROUP (ORDER BY EXTRACT(HOUR FROM started_at AT TIME ZONE 'Asia/Seoul')) as modal_start_hour
            FROM stream_sessions
            WHERE stream_id = %s
              AND started_at >= NOW() - INTERVAL '30 days'
              AND ended_at IS NOT NULL;
            """,
            (stream_id,)
        )
        row = cur.fetchone()
        if not row or not row[0]:
            return None

        total_sessions, avg_dur, max_dur, avg_viewers, max_peak, avg_sub_ratio, modal_hour = row

        # 2. 카테고리 구성비
        cur.execute(
            """
            SELECT category_name, count(*) as cnt
            FROM stream_sessions
            WHERE stream_id = %s
              AND started_at >= NOW() - INTERVAL '30 days'
              AND ended_at IS NOT NULL
              AND category_name IS NOT NULL
            GROUP BY category_name
            ORDER BY cnt DESC
            LIMIT 5;
            """,
            (stream_id,)
        )
        cat_rows = cur.fetchall()
        categories = [
            f"{r[0]} ({round(r[1] / total_sessions * 100)}%)" if total_sessions else r[0]
            for r in cat_rows if r[0]
        ]

        # 3. 최고 화력 모멘트 (피크 시청자 상위 3개 세션 방제)
        cur.execute(
            """
            SELECT title, peak_viewers, category_name
            FROM stream_sessions
            WHERE stream_id = %s
              AND started_at >= NOW() - INTERVAL '30 days'
              AND ended_at IS NOT NULL
            ORDER BY peak_viewers DESC
            LIMIT 3;
            """,
            (stream_id,)
        )
        peak_rows = cur.fetchall()
        peak_moments = [
            {"title": r[0] or "라이브", "peak_viewers": r[1], "category": r[2] or "기타"}
            for r in peak_rows
        ]

        return {
            "streamer_name": streamer_name,
            "total_sessions": total_sessions,
            "avg_duration_hours": float(avg_dur or 0),
            "max_duration_hours": float(max_dur or 0),
            "avg_viewers": int(avg_viewers or 0),
            "max_peak_viewers": int(max_peak or 0),
            "avg_subscriber_ratio": float(avg_sub_ratio or 0) if avg_sub_ratio is not None else None,
            "modal_start_hour": int(modal_hour or 15),
            "top_categories": categories,
            "peak_moments": peak_moments,
        }


def _generate_profile_with_ai(facts: dict, streamer_name: str) -> dict:
    if GEMINI_API_KEY:
        try:
            return _call_gemini_api(facts, streamer_name)
        except Exception as e:
            logger.warning(f"[Gemini-API] API 호출 실패로 규칙 기반 엔진으로 폴백: {e}")

    # Fallback: 고도화된 규칙형 5대 축 Synthesizer
    return _synthesize_rule_based_profile(facts, streamer_name)


def _call_gemini_api(facts: dict, streamer_name: str) -> dict:
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={GEMINI_API_KEY}"
    
    prompt = f"""
당신은 대한민국 인터넷 방송 생태계와 치지직 문화를 깊이 이해하는 전문 방송 데이터 분석가입니다.
아래 제공된 '{streamer_name}' 스트리머의 30일 방송 팩트 데이터를 바탕으로, 방송을 오랫동안 본 시청자가 쓴 것처럼 생생하고 통찰력 있는 방송인 프로필을 JSON 형식으로 작성하세요.

[스트리머 팩트 데이터]
- 이름: {streamer_name}
- 최근 30일 방송 횟수: {facts['total_sessions']}회
- 평균 방송 시간: {facts['avg_duration_hours']}시간 (최장 세션: {facts['max_duration_hours']}시간)
- 주 출근 시간대: KST {facts['modal_start_hour']}시 전후
- 평균 시청자: {facts['avg_viewers']}명 (최고 순간 시청자: {facts['max_peak_viewers']}명)
- 구독자 채팅 비율: {facts['avg_subscriber_ratio'] if facts['avg_subscriber_ratio'] is not None else '미집계'}%
- 주력 카테고리: {', '.join(facts['top_categories'])}
- 최고 시청자 기록 방송 제목들: {', '.join([f"'{m['title']}' ({m['peak_viewers']}명)" for m in facts['peak_moments']])}

[작성 지침]
1. "총 채팅 수" 같은 단순 카운트 수치는 절대 언급하지 마세요.
2. 반드시 아래 JSON 키와 형식을 정확히 지켜 순수 JSON(코드블록 없이)만 반환하세요:
{{
  "summary_line": "스트리머의 정체성과 강점을 꿰뚫는 한 줄 핵심 요약 (30자 내외)",
  "broadcasting_style": "출근 시간대, 방송 호흡(지구력), 콘텐츠 구성 흐름 서술 (1~2문장)",
  "core_games": ["주력 플레이 카테고리/게임 최대 3개"],
  "fandom_traits": "구독자 비율과 피크 모멘트 방제를 반영한 팬덤 분위기 및 충성도 서술 (1~2문장)",
  "popular_memes": ["방제나 주요 모멘트에서 추출한 핵심 키워드/밈 2~4개"]
}}
"""
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.4, "responseMimeType": "application/json"}
    }
    
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    
    with urllib.request.urlopen(req, timeout=15) as resp:
        res_json = json.loads(resp.read().decode("utf-8"))
        text = res_json["candidates"][0]["content"]["parts"][0]["text"].strip()
        # JSON 블록 정리
        if text.startswith("```"):
            text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.MULTILINE).strip()
        return json.loads(text)


def _synthesize_rule_based_profile(facts: dict, streamer_name: str) -> dict:
    sub_ratio = facts.get("avg_subscriber_ratio")
    avg_dur = facts.get("avg_duration_hours", 0)
    modal_hour = facts.get("modal_start_hour", 15)
    cats = facts.get("top_categories", [])
    peaks = facts.get("peak_moments", [])

    # 1. 스케줄 서술
    time_desc = "오후" if 12 <= modal_hour < 18 else ("저녁" if 18 <= modal_hour < 22 else "심야/새벽")
    if avg_dur >= 8.0:
        dur_desc = f"평균 {avg_dur}시간 동안 내리 달리는 강철 체력의 롱타임 방송 진행"
    elif avg_dur >= 5.0:
        dur_desc = f"평균 {avg_dur}시간의 알차고 안정적인 호흡으로 진행"
    else:
        dur_desc = f"평균 {avg_dur}시간의 집중도 높은 숏타임 방송 진행"

    broadcasting_style = f"{time_desc} {modal_hour}시경 방송을 시작하며, {dur_desc}."

    # 2. 게임 추출
    core_games = []
    for c in cats:
        game_name = re.sub(r"\s*\(\d+%\)", "", c).strip()
        if game_name and game_name.lower() != "talk" and len(core_games) < 3:
            core_games.append(game_name)
    if not core_games:
        core_games = ["토크 & 소통", "종합 게임"]

    # 3. 팬덤 & 충성도
    if sub_ratio is not None and sub_ratio >= 70.0:
        fandom_traits = f"구독자 채팅 비율이 {sub_ratio}%에 달할 정도로 치지직 최상위권의 압도적인 코어 찐팬덤을 보유하고 있습니다."
        summary_line = f"구독자 비율 {sub_ratio}%의 탄탄한 코어 팬덤을 지닌 인기 스트리머"
    elif sub_ratio is not None and sub_ratio >= 40.0:
        fandom_traits = f"구독자 비율 {sub_ratio}%로 유동 유입과 고정 팬층의 참여가 조화롭게 어우러지는 활발한 분위기입니다."
        summary_line = f"안정적인 팬덤과 폭넓은 대중성을 겸비한 종합 방송인"
    else:
        fandom_traits = "신규 시청자와 다양한 유동층이 편하게 소통하며 즐길 수 있는 열린 채팅방 분위기를 형성합니다."
        summary_line = f"다채로운 콘텐츠와 열린 소통으로 시청자 유입이 활발한 스트리머"

    # 4. 키워드/밈 (피크 방제 활용)
    memes = []
    for p in peaks:
        title = p.get("title", "")
        # 의미 있는 키워드 추출
        tokens = re.findall(r"[가-힣a-zA-Z0-9]{2,10}", title)
        for t in tokens:
            if t not in ["오늘", "방송", "치지직", "다시보기", "라이브"] and t not in memes and len(memes) < 4:
                memes.append(t)
    if not memes:
        memes = ["소통", "종합게임", "하이라이트"]

    return {
        "summary_line": summary_line,
        "broadcasting_style": broadcasting_style,
        "core_games": core_games,
        "fandom_traits": fandom_traits,
        "popular_memes": memes,
    }


def _upsert_profiles(conn, profile_records: list[tuple]):
    sql = """
        INSERT INTO streamer_ai_profiles (
            stream_id, summary_line, broadcasting_style,
            core_games, fandom_traits, popular_memes,
            analyzed_session_count
        ) VALUES %s
        ON CONFLICT (stream_id) DO UPDATE SET
            summary_line = EXCLUDED.summary_line,
            broadcasting_style = EXCLUDED.broadcasting_style,
            core_games = EXCLUDED.core_games,
            fandom_traits = EXCLUDED.fandom_traits,
            popular_memes = EXCLUDED.popular_memes,
            analyzed_session_count = EXCLUDED.analyzed_session_count,
            updated_at = NOW();
    """
    with conn.cursor() as cur:
        execute_values(cur, sql, profile_records, page_size=200)


def _sync_profiles_to_oci(profile_records: list[tuple]):
    try:
        oci_hook = PostgresHook(postgres_conn_id="slice_oci_postgres")
        oci_conn = oci_hook.get_conn()
    except Exception as e:
        logger.warning(f"[AI Profiler] OCI DB 연결 실패로 동기화 스킵: {e}")
        return

    try:
        with oci_conn.cursor() as cur:
            execute_values(
                cur,
                """
                INSERT INTO streamer_ai_profiles (
                    stream_id, summary_line, broadcasting_style,
                    core_games, fandom_traits, popular_memes,
                    analyzed_session_count
                ) VALUES %s
                ON CONFLICT (stream_id) DO UPDATE SET
                    summary_line = EXCLUDED.summary_line,
                    broadcasting_style = EXCLUDED.broadcasting_style,
                    core_games = EXCLUDED.core_games,
                    fandom_traits = EXCLUDED.fandom_traits,
                    popular_memes = EXCLUDED.popular_memes,
                    analyzed_session_count = EXCLUDED.analyzed_session_count,
                    updated_at = NOW();
                """,
                profile_records,
                page_size=200
            )
            oci_conn.commit()
            logger.info(f"[AI Profiler] OCI Hot DB 동기화 완료: {len(profile_records)}건")
    except Exception as e:
        logger.error(f"[AI Profiler] OCI 동기화 오류: {e}")
    finally:
        oci_conn.close()


streamer_ai_profiler()

