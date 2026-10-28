import json
import logging
import os
import re
import time
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
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-pro")

default_args = {
    "owner": "slice-de",
    "retries": 1,
    "retry_delay": timedelta(minutes=2),
}


@dag(
    dag_id="dag_streamer_ai_profiler",
    default_args=default_args,
    description="스트리머 5대 축(스케줄, 콘텐츠, 화력, 팬덤, 밈) AI 프로파일링 파이프라인 (매일 10명씩 30일 순환)",
    schedule="0 4 * * *",  # 매일 새벽 4시 순환 갱신 (30일 주기 300명)
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
                # 최근 30일 상위 300명 풀 중, 가장 오래 갱신되지 않았거나(미존재 포함) 신규 진입한 10명 추출
                cur.execute(
                    """
                    WITH top_streamers AS (
                        SELECT stream_id, streamer_name, count(*) as session_count
                        FROM stream_sessions
                        WHERE started_at >= NOW() - INTERVAL '30 days'
                          AND streamer_name IS NOT NULL
                          AND trim(streamer_name) != ''
                        GROUP BY stream_id, streamer_name
                        ORDER BY max(peak_viewers) DESC NULLS LAST, max(average_viewer_count) DESC NULLS LAST
                        LIMIT 300
                    )
                    SELECT t.stream_id, t.streamer_name, t.session_count
                    FROM top_streamers t
                    LEFT JOIN streamer_ai_profiles p ON t.stream_id = p.stream_id
                    ORDER BY p.updated_at ASC NULLS FIRST
                    LIMIT 10;
                    """
                )
                rows = cur.fetchall()
                logger.info(f"[AI Profiler] 오늘 분석 대상 순환 스트리머 {len(rows)}명 추출 완료")
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
                    time.sleep(3)  # API 호출 레이트 리밋 방어 및 안정적 간격 확보
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
        # 1. 방송 리듬 & 지구력 통계
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

        # 2. 실제 세그먼트 기반 플레이 카테고리 & 시간 점유율 (다양성 vs 몰입도 측정)
        cur.execute(
            """
            SELECT 
                COALESCE(NULLIF(trim(category_name), ''), 'talk') as cat_name,
                count(*) as seg_count,
                round(sum(EXTRACT(EPOCH FROM (COALESCE(ended_at, NOW()) - started_at))/3600)::numeric, 1) as total_cat_hours
            FROM stream_session_segments
            WHERE stream_id = %s
              AND started_at >= NOW() - INTERVAL '30 days'
            GROUP BY cat_name
            ORDER BY total_cat_hours DESC;
            """,
            (stream_id,)
        )
        cat_rows = cur.fetchall()
        total_broadcast_hours = sum(float(r[2] or 0) for r in cat_rows)

        unique_games_count = len([r for r in cat_rows if r[0].lower() != "talk"])
        top_categories = []
        top1_share_pct = 0.0

        for idx, (cat_name, cnt, cat_hrs) in enumerate(cat_rows[:5]):
            hrs_val = float(cat_hrs or 0)
            pct = round((hrs_val / total_broadcast_hours * 100), 1) if total_broadcast_hours > 0 else 0.0
            if idx == 0 and cat_name.lower() != "talk":
                top1_share_pct = pct
            top_categories.append(f"{cat_name} ({hrs_val}시간, {pct}%)")

        # 게임 소비 스타일 판별
        top2_share_pct = 0.0
        if len(cat_rows) >= 2:
            top2_hrs = float(cat_rows[0][2] or 0) + float(cat_rows[1][2] or 0)
            top2_share_pct = round((top2_hrs / total_broadcast_hours * 100), 1) if total_broadcast_hours > 0 else 0.0

        talk_share_pct = 0.0
        for cat_name, cnt, cat_hrs in cat_rows:
            if cat_name.lower() == "talk":
                talk_share_pct = round((float(cat_hrs or 0) / total_broadcast_hours * 100), 1) if total_broadcast_hours > 0 else 0.0

        if talk_share_pct >= 60.0:
            gaming_style = "COMMUNICATION_FOCUSED (시청자와 일상과 생각을 나누는 소통/토크가 중심이 되는 따뜻한 아지트)"
        elif top1_share_pct >= 35.0 or top2_share_pct >= 45.0:
            gaming_style = "DEEP_IMMERSIVE (주력 대작이나 팀 게임에 수십 시간씩 깊게 몰입하는 열정형 스타일)"
        elif unique_games_count >= 6:
            gaming_style = "EXPLORER (매일 다양한 신작/인디/스팀 게임을 폭넓게 발굴하는 옴니버스 종합게임 탐험가)"
        else:
            gaming_style = "HYBRID_BALANCED (소통과 게임 플레이를 유연하게 오가는 균형 잡힌 방송 구성)"

        # 3. 방제 화법 샘플링 (서로 다른 카테고리/날짜에서 골고루 5개 추출하여 방제 특유의 어조 포착)
        cur.execute(
            """
            WITH ranked_titles AS (
                SELECT 
                    title, category_name, peak_viewers,
                    ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY started_at DESC) as rn
                FROM stream_sessions
                WHERE stream_id = %s
                  AND started_at >= NOW() - INTERVAL '30 days'
                  AND title IS NOT NULL
                  AND trim(title) != ''
            )
            SELECT title, category_name, peak_viewers
            FROM ranked_titles
            WHERE rn <= 2
            ORDER BY peak_viewers DESC
            LIMIT 5;
            """,
            (stream_id,)
        )
        title_rows = cur.fetchall()
        signature_titles = [f"[{r[1] or '기타'}] {r[0]}" for r in title_rows]

        return {
            "streamer_name": streamer_name,
            "total_sessions": total_sessions,
            "avg_duration_hours": float(avg_dur or 0),
            "max_duration_hours": float(max_dur or 0),
            "modal_start_hour": int(modal_hour or 20),
            "avg_subscriber_ratio": float(avg_sub_ratio) if avg_sub_ratio is not None else None,
            "unique_games_count": unique_games_count,
            "gaming_style": gaming_style,
            "top_categories": top_categories,
            "signature_titles": signature_titles,
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
당신은 대한민국 인터넷 방송 생태계와 치지직 문화를 깊이 이해하는 전문 방송 문화 칼럼니스트입니다.
아래 제공된 '{streamer_name}' 스트리머의 30일 방송 팩트 데이터를 바탕으로,
"이 방송은 왜 사람들에게 매력적이고 사랑받는가?"라는 질문에 답하는 생생하고 따뜻한 방송인 프로필을 작성하세요.

[스트리머 방송 팩트 데이터]
- 이름: {streamer_name}
- 최근 30일 방송 횟수: {facts['total_sessions']}회
- 주 출근 시간대: KST {facts['modal_start_hour']}시경 시작
- 평균 방송 호흡: 회당 평균 {facts['avg_duration_hours']}시간 (최장 세션: {facts['max_duration_hours']}시간)
- 콘텐츠 소비 성향: {facts['gaming_style']} (30일간 플레이한 고유 게임 수: {facts['unique_games_count']}종)
- 주요 플레이 카테고리: {', '.join(facts['top_categories'])}
- 대표 방송 제목들(실제 방제 화법과 어조): {', '.join([f"'{t}'" for t in facts['signature_titles']])}
- 구독자 채팅 비율: {f"{facts['avg_subscriber_ratio']}% (높은 팬덤 결집도)" if facts.get('avg_subscriber_ratio') is not None else "누구나 편안하게 소통하는 열린 커뮤니티"}

[작성 절대 지침]
1. '체급', '티어', '급 나누기', '대형', '하꼬' 등 서열화하는 단어는 절대 사용하지 마세요.
2. '총 채팅 수', '조회수' 같은 단순 카운트 수치는 언급하지 마세요.
3. 방제에서 느껴지는 스트리머 특유의 말투나 어조, 게임을 깊게 또는 다채롭게 즐기는 텐션을 포착하세요.
4. 비난이나 조롱 없이, 시청자들이 이 방송을 찾아와 위로와 도파민을 얻는 이유를 감각적이고 매력적으로 서술하세요.
5. 반드시 아래 JSON 형식(코드블록 없이 순수 JSON만)으로 반환하세요:
{{
  "summary_line": "이 스트리머의 매력을 관통하는 한 줄 표현 (30자 내외)",
  "broadcasting_style": "왜 이 방송에 빠져드는가? (플레이 스타일과 방송 호흡, 텐션 서술 1~2문장)",
  "core_games": ["대표 카테고리/콘텐츠 3개 내외"],
  "fandom_traits": "채팅방 특유의 분위기와 시청자들이 함께 교감하는 방식 (1~2문장)",
  "popular_memes": ["방제나 주요 콘텐츠에서 추출한 고유 키워드 2~4개"]
}}
"""
    body = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.5, "responseMimeType": "application/json"}
    }
    
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    
    with urllib.request.urlopen(req, timeout=60) as resp:
        res_json = json.loads(resp.read().decode("utf-8"))
        text = res_json["candidates"][0]["content"]["parts"][0]["text"].strip()
        if text.startswith("```"):
            text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.MULTILINE).strip()
        return json.loads(text)


def _synthesize_rule_based_profile(facts: dict, streamer_name: str) -> dict:
    sub_ratio = facts.get("avg_subscriber_ratio")
    avg_dur = facts.get("avg_duration_hours", 0)
    modal_hour = facts.get("modal_start_hour", 20)
    cats = facts.get("top_categories", [])
    titles = facts.get("signature_titles", [])
    style_desc = facts.get("gaming_style", "")

    # 1. 대표 게임 이름 2~3개 추출
    clean_games = []
    for c in cats:
        g = re.sub(r"\s*\([^)]*\)", "", c).strip()
        if g and g.lower() != "talk" and g not in clean_games and len(clean_games) < 3:
            clean_games.append(g)
    if not clean_games:
        clean_games = ["종합 게임", "토크 & 소통"]
    games_text = ", ".join(clean_games[:2])

    # 2. 시간대별 묘사
    if 6 <= modal_hour < 12:
        time_text = f"오전 {modal_hour}시경 하루를 활기차게 여는 모닝 루틴"
    elif 12 <= modal_hour < 17:
        time_text = f"낮 {modal_hour}시경 찾아오는 편안한 데일리 방송"
    elif 17 <= modal_hour < 21:
        time_text = f"저녁 {modal_hour}시경 퇴근길 시청자들과 만나는 메인 타임"
    else:
        time_text = f"심야 {modal_hour}시경 깊은 밤을 함께 지새우는 올빼미 방송"

    # 3. 게임 소비 스타일에 따른 서술
    main_game = clean_games[0] if clean_games else "게임"
    if "EXPLORER" in style_desc:
        summary_line = f"매일 {games_text} 등 신작을 다채롭게 발굴하는 종합게임 탐험가"
        broadcasting_style = f"{time_text}으로, {games_text} 등 다양한 장르를 넘나들며 평균 {avg_dur}시간 동안 유쾌하고 푸근한 호흡으로 소통합니다."
        fandom_traits = "새로운 게임의 매력을 함께 탐험하며 편안하게 대화를 즐기는 게임 애호가들의 쉼터입니다."
    elif "DEEP_IMMERSIVE" in style_desc:
        summary_line = f"'{main_game}'의 정점을 향해 뜨겁게 질주하는 승부와 몰입의 플레이어"
        broadcasting_style = f"{time_text}으로, {games_text}에 깊게 파고들며 평균 {avg_dur}시간 동안 흔들림 없는 텐션과 진심 어린 몰입을 보여줍니다."
        fandom_traits = "스트리머의 치열한 여정과 도전에 깊게 공감하며 한마음으로 뜨겁게 환호하는 결속력 높은 팬덤입니다."
    elif "COMMUNICATION_FOCUSED" in style_desc:
        summary_line = f"진솔한 대화와 끈끈한 유대감이 흐르는 따뜻한 소통 아지트"
        broadcasting_style = f"{time_text}에 찾아와, 평균 {avg_dur}시간 동안 시청자들과 일상을 나누며 편안한 교감을 나눕니다."
        sub_desc = f"구독자 비율 {sub_ratio}%의 " if sub_ratio is not None else ""
        fandom_traits = f"{sub_desc}두터운 신뢰 속에서 유쾌한 티키타카와 애정 어린 교감이 넘치는 포근한 커뮤니티입니다."
    else:
        summary_line = f"{main_game} 플레이와 소통을 유연하게 넘나드는 균형 잡힌 방송인"
        broadcasting_style = f"{time_text}으로, {games_text} 플레이와 소통을 오가며 평균 {avg_dur}시간 동안 안정적인 호흡으로 진행합니다."
        fandom_traits = "누구나 편안하게 참여하여 대화를 즐길 수 있는 열린 분위기의 포근한 채팅방입니다."

    # 4. 방제에서 의미 있는 고유 명사/키워드 추출 (불용어 제외)
    stop_words = {
        "오늘", "방송", "치지직", "게임", "해봅니다", "다시보기", "결승", "대한민국",
        "남자", "여자", "일본", "사람", "vs", "하는", "통해", "위해", "이거", "저거",
        "준결승", "1기", "2기", "3기"
    }
    memes = []
    for t in titles:
        clean_t = re.sub(r"\[[^\]]*\]", "", t)
        words = re.findall(r"[가-힣a-zA-Z0-9]{2,10}", clean_t)
        for w in words:
            if w.lower() not in stop_words and w not in memes and len(memes) < 4:
                memes.append(w)
    if not memes:
        memes = clean_games[:3]

    return {
        "summary_line": summary_line,
        "broadcasting_style": broadcasting_style,
        "core_games": clean_games,
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

