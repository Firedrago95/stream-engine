import gzip
import hashlib
import json
import logging
import math
import os
import re
import time
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from statistics import median
from zoneinfo import ZoneInfo

from airflow.providers.postgres.hooks.postgres import PostgresHook
from airflow.sdk import dag, task
from psycopg2.extras import execute_values

logger = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")

CHAT_DATA_DIR = os.getenv("CHAT_SAMPLE_DIR", "/opt/airflow/data/chat_samples")
STOPWORDS_CONFIG_PATH = os.getenv(
    "HIGHLIGHT_STOPWORDS_PATH",
    str(Path(__file__).resolve().parent / "highlight_stopwords.json"),
)

ALGO_VERSION = "v2.1"
SIGMA_ALGO_VERSION = "v2.1"
NO_CHAT_FILE_VERSION = "no-chat-file"
MAX_ANALYSIS_ATTEMPTS = 5
ORPHAN_FILE_AGE_SECONDS = 7200

EMOTE_REGEX = re.compile(r"\{:([^:]+):\}")
WORD_REGEX = re.compile(r"[가-힣a-zA-Z0-9]{2,10}")
REPEAT_CHAR_REGEX = re.compile(r"(.)\1{2,}")
NOISE_TOKEN_REGEX = re.compile(r"^[zZgGㅋㅎㅠㅜ?!~ㅎㄷ.,]+$")

DEFAULT_STOPWORDS = {
    "진짜", "너무", "그냥", "아니", "근데", "이제", "오늘", "지금", "여기",
    "뭔가", "약간", "우리", "내가", "나도", "맞아", "계속", "바로", "다시",
    "왜케", "이거", "저거", "그거", "이건", "저건", "그건", "저게", "이게",
    "어떻게", "이렇게", "저렇게", "에서", "으로", "하고", "하는", "는데",
    "어흐", "아오", "뭐야", "아니야", "안돼", "오오", "으악", "와우", "어어",
}

default_args = {
    "owner": "slice-de",
    "retries": 2,
    "retry_delay": timedelta(minutes=1),
}


@dag(
    dag_id="dag_post_broadcast_pipeline",
    default_args=default_args,
    description="방종 세션 사후 분석 및 하이라이트(v2.1) 추출 파이프라인",
    schedule="*/10 * * * *",
    start_date=datetime(2026, 10, 24),
    catchup=False,
    max_active_runs=1,
    tags=["post-broadcast", "dw", "highlight"],
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
              AND analysis_attempts < %s
            ORDER BY analysis_attempts ASC, ended_at ASC
            LIMIT 500;
        """
        records = hook.get_records(sql, parameters=(MAX_ANALYSIS_ATTEMPTS,))
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
    def process_chat_to_dw(sessions: list[dict]) -> list[str]:
        if not sessions:
            _warn_orphan_files(CHAT_DATA_DIR)
            return []

        stopwords, stopwords_version, stopwords_hash = _load_stopwords()
        available_files = _list_available_gz_files(CHAT_DATA_DIR)
        now_kst = datetime.now(tz=KST)

        sessions_with_files = []
        stale_no_file_ids = []

        for s in sessions:
            session_id = s["session_id"]
            streamer_name = s.get("streamer_name")
            started_at = s.get("started_at")
            ended_at_dt = _parse_timestamp(s.get("ended_at"))

            target_file = _find_chat_file(
                session_id=session_id,
                streamer_name=streamer_name,
                started_at=started_at,
                available_files=available_files,
            )
            if target_file and os.path.exists(target_file):
                sessions_with_files.append((s, target_file))
            else:
                # 파일이 없는 세션은 방종 후 1시간이 지난 경우에만 'no-chat-file'로 벌크 마감 (지연 도착 보호)
                if ended_at_dt and (now_kst - ended_at_dt) >= timedelta(hours=1):
                    stale_no_file_ids.append(session_id)

        hook = PostgresHook(postgres_conn_id="slice_dw_postgres")
        conn = hook.get_conn()
        processed_session_ids = []

        try:
            if stale_no_file_ids:
                _bulk_close_no_file_sessions(conn, stale_no_file_ids)
                conn.commit()
                processed_session_ids.extend(stale_no_file_ids)
                logger.info(f"[사후분석] 1시간 경과 미수집(비타겟) 세션 일괄 마감 완료: {len(stale_no_file_ids)}건")

            for s, target_file in sessions_with_files[:10]:
                session_id = s["session_id"]
                stream_id = s["stream_id"]
                streamer_name = s.get("streamer_name")
                started_at = s.get("started_at")

                session_stopwords = set(stopwords)
                if streamer_name:
                    clean_streamer = re.sub(r"\s+", "", str(streamer_name)).lower()
                    if len(clean_streamer) >= 2:
                        session_stopwords.add(clean_streamer)

                try:
                    buckets, session_tokens, truncated_input = _parse_and_aggregate_chat(
                        file_path=target_file,
                        stopwords=session_stopwords,
                    )

                    highlights, sigma_session, valid_bucket_count = _detect_highlights_v2(
                        buckets=buckets,
                        session_started_at=started_at,
                        stopwords_version=stopwords_version,
                        stopwords_hash=stopwords_hash,
                        truncated_input=truncated_input,
                    )

                    if buckets:
                        _insert_chat_signals(conn, session_id, buckets)
                    _insert_token_summary(conn, session_id, session_tokens)
                    _replace_session_highlights(
                        conn=conn,
                        session_id=session_id,
                        stream_id=stream_id,
                        algo_version=ALGO_VERSION,
                        highlights=highlights,
                    )
                    _update_session_analysis_state(
                        conn=conn,
                        session_id=session_id,
                        sigma_session=sigma_session,
                        valid_bucket_count=valid_bucket_count,
                        sigma_algo_version=SIGMA_ALGO_VERSION,
                        highlights_algo_version=ALGO_VERSION,
                    )

                    conn.commit()
                    processed_session_ids.append(session_id)

                    try:
                        os.remove(target_file)
                        logger.info(
                            f"[사후분석] 세션 처리 및 원본 파일 파기 완료: session_id={session_id}, "
                            f"하이라이트={len(highlights)}개, sigma_session={sigma_session}, truncated={truncated_input}"
                        )
                    except OSError as rm_err:
                        logger.warning(f"[사후분석] DB 커밋 성공 후 원본 파일 삭제 실패: file={target_file}, 오류={rm_err}")

                except Exception as e:
                    conn.rollback()
                    _increment_analysis_attempts(conn, session_id)
                    conn.commit()
                    logger.error(f"[사후분석] 세션 {session_id} ({streamer_name}) 처리 중 오류 발생, 재시도 카운트 증가: {e}")
        finally:
            conn.close()

        _warn_orphan_files(CHAT_DATA_DIR)
        return processed_session_ids

    pending = get_pending_sessions()
    process_chat_to_dw(pending)


def _load_stopwords() -> tuple[set[str], str, str]:
    try:
        if os.path.exists(STOPWORDS_CONFIG_PATH):
            raw_bytes = Path(STOPWORDS_CONFIG_PATH).read_bytes()
            content_hash = hashlib.sha256(raw_bytes).hexdigest()[:8]
            data = json.loads(raw_bytes.decode("utf-8"))
            words = {str(w).lower() for w in data.get("stopwords", [])}
            version = str(data.get("version", "v1"))
            if words:
                return words, version, content_hash
    except Exception as e:
        logger.warning(f"[사후분석] 불용어 설정 파일 로드 실패, 기본 사전 사용: {e}")
    default_hash = hashlib.sha256(
        json.dumps(sorted(DEFAULT_STOPWORDS), ensure_ascii=False).encode("utf-8")
    ).hexdigest()[:8]
    return {w.lower() for w in DEFAULT_STOPWORDS}, "v1-default", default_hash


def _normalize_word(word: str) -> str:
    return REPEAT_CHAR_REGEX.sub(r"\1\1", word.lower())


def _parse_timestamp(raw_ts) -> datetime | None:
    if raw_ts is None or raw_ts == "":
        return None
    try:
        if isinstance(raw_ts, (int, float)):
            sec = float(raw_ts) / 1000.0 if raw_ts > 1e11 else float(raw_ts)
            return datetime.fromtimestamp(sec, tz=KST)
        dt = datetime.fromisoformat(str(raw_ts).replace("Z", "+00:00"))
        return dt.astimezone(KST) if dt.tzinfo else dt.replace(tzinfo=KST)
    except Exception:
        return None


def _list_available_gz_files(data_dir: str) -> list[str]:
    if not os.path.exists(data_dir):
        return []
    return sorted(f for f in os.listdir(data_dir) if f.endswith(".jsonl.gz"))


def _warn_orphan_files(data_dir: str):
    if not os.path.exists(data_dir):
        return
    now_ts = time.time()
    orphans = []
    for fname in os.listdir(data_dir):
        if not (fname.endswith(".jsonl.gz") or fname.endswith(".tmp")):
            continue
        fpath = os.path.join(data_dir, fname)
        try:
            age_sec = now_ts - os.path.getmtime(fpath)
            if age_sec >= ORPHAN_FILE_AGE_SECONDS:
                orphans.append(fname)
        except OSError:
            continue
    if orphans:
        logger.warning(
            f"[사후분석] 2시간 이상 미처리된 고아/임시 채팅 파일 감지 ({len(orphans)}건): {orphans[:10]}"
        )


def _find_chat_file(
    session_id: str,
    streamer_name: str | None = None,
    started_at: str | None = None,
    available_files: list[str] | None = None,
) -> str | None:
    files = available_files if available_files is not None else _list_available_gz_files(CHAT_DATA_DIR)
    if not files:
        return None

    # 1순위: 고유 session_id 매칭
    for fname in files:
        if session_id in fname:
            return os.path.join(CHAT_DATA_DIR, fname)

    # 2순위: streamer_name + 시작일시(yyyyMMdd_HH) 폴백 매칭 (후보 파일이 정확히 1개일 때만 허용)
    if streamer_name and started_at:
        dt = _parse_timestamp(started_at)
        if dt:
            time_tag = dt.strftime("%Y%m%d_%H")
            matched = [f for f in files if streamer_name in f and time_tag in f]
            if len(matched) == 1:
                return os.path.join(CHAT_DATA_DIR, matched[0])
            if len(matched) > 1:
                logger.warning(
                    f"[사후분석] 폴백 파일 매칭 모호성 감지 (스킵): session_id={session_id}, "
                    f"streamer={streamer_name}, 후보={matched}"
                )

    return None


def _parse_and_aggregate_chat(file_path: str, stopwords: set[str] | None = None):
    active_stopwords = {w.lower() for w in (stopwords if stopwords is not None else DEFAULT_STOPWORDS)}

    buckets = defaultdict(lambda: {
        "chat_count": 0,
        "unique_chatters": set(),
        "emote_count": 0,
        "donation_count": 0,
        "tokens": Counter(),
        "word_users": defaultdict(set),
        "emote_users": defaultdict(set),
    })
    session_tokens = Counter()
    truncated_input = False

    try:
        with gzip.open(file_path, "rt", encoding="utf-8") as f:
            for line_idx, line in enumerate(f):
                if not line.strip():
                    continue
                try:
                    msg = json.loads(line)
                except json.JSONDecodeError:
                    continue

                if msg.get("type") == "METADATA":
                    continue

                dt = _parse_timestamp(msg.get("timestamp"))
                if not dt:
                    continue

                bucket_second = (dt.second // 10) * 10
                bucket_time = dt.replace(second=bucket_second, microsecond=0)

                content = str(msg.get("content", ""))
                raw_author = msg.get("authorId") or msg.get("userId")
                user_key = str(raw_author) if raw_author else f"anon_{line_idx}"

                b = buckets[bucket_time]
                b["chat_count"] += 1
                if raw_author:
                    b["unique_chatters"].add(str(raw_author))

                raw_emotes = EMOTE_REGEX.findall(content)
                b["emote_count"] += len(raw_emotes)
                unique_emotes_in_msg = {f":{em}:" for em in raw_emotes}

                clean_text = EMOTE_REGEX.sub(" ", content)
                unique_words_in_msg = set()
                for raw_w in WORD_REGEX.findall(clean_text):
                    nw = _normalize_word(raw_w)
                    if len(nw) >= 2 and nw not in active_stopwords and not NOISE_TOKEN_REGEX.match(nw):
                        unique_words_in_msg.add(nw)

                for em_tok in unique_emotes_in_msg:
                    b["emote_users"][em_tok].add(user_key)
                    session_tokens[em_tok] += 1

                for w_tok in unique_words_in_msg:
                    b["word_users"][w_tok].add(user_key)
                    session_tokens[w_tok] += 1
    except (EOFError, OSError) as e:
        truncated_input = True
        logger.warning(f"[사후분석] 손상/절단된 gzip 파일 감지, 읽은 구간까지만 분석 진행: file={file_path}, 오류={e}")

    for b in buckets.values():
        user_df_counter = Counter()
        for w_tok, u_set in b["word_users"].items():
            user_df_counter[w_tok] = len(u_set)
        for em_tok, u_set in b["emote_users"].items():
            user_df_counter[em_tok] = len(u_set)
        b["tokens"] = user_df_counter

    return buckets, session_tokens, truncated_input


def _quantile(sorted_arr: list[float], q: float) -> float:
    if not sorted_arr:
        return 0.0
    pos = (len(sorted_arr) - 1) * q
    lo = int(math.floor(pos))
    hi = int(math.ceil(pos))
    if lo == hi:
        return sorted_arr[lo]
    return sorted_arr[lo] + (sorted_arr[hi] - sorted_arr[lo]) * (pos - lo)


def _detect_highlight_peaks(
    chats: list[int],
    users: list[int],
    k_threshold: float = 2.8,
    prominence_sigma: float = 2.0,
    min_chats: int = 10,
    min_users: int = 5,
) -> tuple[list[dict], list[bool], float | None, int]:
    """
    오직 10초 단위 chats, users 정수 배열만 입력받아 산 구간과 LLR 점수를 계산하는 순수 함수.
    원본 채팅 파일 없이 DB(chat_signal_10s) 데이터만으로도 100% 동일하게 재실행 가능하다.
    """
    n = len(chats)
    if n <= 0:
        return [], [], None, 0

    is_missing = [False] * n
    zero_run = 0
    for i in range(n):
        if chats[i] == 0:
            zero_run += 1
        else:
            if zero_run >= 6:
                for j in range(i - zero_run, i):
                    is_missing[j] = True
            zero_run = 0
    if zero_run >= 6:
        for j in range(n - zero_run, n):
            is_missing[j] = True

    valid_bucket_count = sum(1 for m in is_missing if not m)
    if valid_bucket_count == 0:
        return [], is_missing, None, 0

    y = [2.0 * math.sqrt(c + 0.375) for c in chats]
    y_tilde = [0.0] * n
    for i in range(n):
        if 0 < i < n - 1 and not is_missing[i - 1] and not is_missing[i + 1]:
            y_tilde[i] = max(y[i], min(y[i - 1], y[i + 1]))
        else:
            y_tilde[i] = y[i]

    m_base = [0.0] * n
    m_orig = [0.5] * n
    sigma_local = [1.0] * n

    for i in range(n):
        left_w = [y[j] for j in range(max(0, i - 60), i + 1) if not is_missing[j]]
        right_w = [y[j] for j in range(i, min(n, i + 61)) if not is_missing[j]]
        full_w = [y[j] for j in range(max(0, i - 60), min(n, i + 61)) if not is_missing[j]]

        m_l = median(left_w) if left_w else y[i]
        m_r = median(right_w) if right_w else y[i]
        mt = max(m_l, m_r)
        m_base[i] = mt
        m_orig[i] = max(0.5, (mt / 2.0) ** 2 - 0.375)

        if full_w:
            med_full = median(full_w)
            mad = median([abs(x - med_full) for x in full_w])
            sigma_local[i] = max(1.4826 * mad, 1.0)
        else:
            sigma_local[i] = 1.0

    residuals = sorted([y[i] - m_base[i] for i in range(n) if not is_missing[i]])
    if len(residuals) >= 20:
        q05 = _quantile(residuals, 0.05)
        q90 = _quantile(residuals, 0.90)
        body_res = [r for r in residuals if q05 <= r <= q90]
        body_med = median(body_res)
        body_mad = median([abs(r - body_med) for r in body_res])
        sigma_session = round(max(1.4826 * body_mad, 1.0), 4)
    else:
        sigma_session = 1.0

    sigma_stream = 1.0
    sigma_eff = [max(s, sigma_stream) for s in sigma_local]

    raw_peaks = []
    for i in range(n):
        if is_missing[i]:
            continue
        z_t = (y_tilde[i] - m_base[i]) / sigma_eff[i]
        c_max3 = max(chats[max(0, i - 1):min(n, i + 2)])
        u_max3 = max(users[max(0, i - 1):min(n, i + 2)])
        if z_t >= k_threshold and c_max3 >= min_chats and u_max3 >= min_users:
            if y_tilde[i] == max(y_tilde[max(0, i - 2):min(n, i + 3)]):
                if raw_peaks and i - raw_peaks[-1] <= 2 and y_tilde[raw_peaks[-1]] == y_tilde[i]:
                    continue
                raw_peaks.append(i)

    if not raw_peaks:
        return [], is_missing, sigma_session, valid_bucket_count

    merged = []
    for p in raw_peaks:
        if not merged:
            merged.append(p)
            continue
        prev = merged[-1]
        v_idx = min(range(prev, p + 1), key=lambda idx: y_tilde[idx])
        p_min = prev if y_tilde[prev] <= y_tilde[p] else p
        prom_val = (y_tilde[p_min] - y_tilde[v_idx]) / sigma_eff[p_min]

        if p - prev <= 3 or prom_val < prominence_sigma:
            if y_tilde[p] > y_tilde[prev]:
                merged[-1] = p
        else:
            merged.append(p)

    valleys = {}
    for idx_m in range(len(merged) - 1):
        p1, p2 = merged[idx_m], merged[idx_m + 1]
        valleys[idx_m] = min(range(p1, p2 + 1), key=lambda k_i: y_tilde[k_i])

    phi_session = max(1.0, sigma_session ** 2)
    peak_items = []
    for idx_m, p in enumerate(merged):
        cutoff = m_base[p] + 0.25 * (y_tilde[p] - m_base[p])
        left_limit = valleys[idx_m - 1] + 1 if idx_m > 0 else 0
        right_limit = valleys[idx_m] if idx_m < len(merged) - 1 else n - 1

        s = p
        while s > left_limit and y_tilde[s - 1] > cutoff and (p - s) < 18:
            s -= 1
        e = p
        while e < right_limit and y_tilde[e + 1] > cutoff and (e - p) < 18:
            e += 1

        lo_i = max(s, p - 3)
        hi_i = min(e, p + 3)
        llr_sum = 0.0
        for t in range(lo_i, hi_i + 1):
            ct = chats[t]
            mt_orig = m_orig[t]
            if ct > mt_orig:
                llr_sum += ct * math.log(ct / mt_orig) - (ct - mt_orig)
        llr_score = round(llr_sum / phi_session, 4)

        peak_items.append({
            "p": p,
            "s": s,
            "e": e,
            "peak_chats": max(chats[max(0, p - 1):min(n, p + 2)]),
            "peak_unique_users": max(users[max(0, p - 1):min(n, p + 2)]),
            "baseline_chats": round(m_orig[p], 2),
            "sigma_local": round(sigma_local[p], 4),
            "peak_z": round((y_tilde[p] - m_base[p]) / sigma_eff[p], 4),
            "llr_score": llr_score,
        })

    return peak_items, is_missing, sigma_session, valid_bucket_count


def _extract_highlight_tags(
    peak_items: list[dict],
    is_missing: list[bool],
    users: list[int],
    word_users_list: list[dict],
    emote_users_list: list[dict],
    chatter_sets: list[set],
) -> list[dict]:
    """산 구간 목록과 버킷별 유저-단어 집합을 받아 Monroe Fightin' Words Z-score 태그를 산출한다."""
    n = len(users)
    peak_bucket_set = set()
    for item in peak_items:
        for b_i in range(item["s"], item["e"] + 1):
            peak_bucket_set.add(b_i)

    bg_word_users = defaultdict(set)
    bg_all_chatters = set()
    for i in range(n):
        if i not in peak_bucket_set and not is_missing[i]:
            bg_all_chatters.update(chatter_sets[i])
            for w_tok, u_set in word_users_list[i].items():
                bg_word_users[w_tok].update(u_set)
    bg_total_u = max(len(bg_all_chatters), 50)

    tags_per_peak = []
    for item in peak_items:
        p, s, e = item["p"], item["s"], item["e"]
        pk_word_users = defaultdict(set)
        pk_emote_users = defaultdict(set)
        pk_all_chatters = set()
        for t in range(s, e + 1):
            pk_all_chatters.update(chatter_sets[t])
            for w_tok, u_set in word_users_list[t].items():
                pk_word_users[w_tok].update(u_set)
            for em_tok, u_set in emote_users_list[t].items():
                pk_emote_users[em_tok].update(u_set)

        pk_total_u = max(len(pk_all_chatters), max(users[max(0, p - 1):min(n, p + 2)]), 10)

        word_candidates = []
        for w_tok, u_set in pk_word_users.items():
            u_pk = len(u_set)
            if u_pk < 2:
                continue
            u_bg = len(bg_word_users.get(w_tok, set()))
            a_w = 0.5
            a_0 = 10.0
            p_pk = min(max((u_pk + a_w) / (pk_total_u + a_0), 1e-6), 1.0 - 1e-6)
            p_bg = min(max((u_bg + a_w) / (bg_total_u + a_0), 1e-6), 1.0 - 1e-6)
            logit_diff = math.log(p_pk / (1.0 - p_pk)) - math.log(p_bg / (1.0 - p_bg))
            se = math.sqrt(1.0 / (u_pk + a_w) + 1.0 / (u_bg + a_w))
            z_w = round(logit_diff / se, 3)
            word_candidates.append({"w": w_tok, "u": u_pk, "bg_u": u_bg, "z": z_w})

        word_candidates.sort(key=lambda x: (x["u"] >= 3, x["z"], x["u"]), reverse=True)
        top_words = word_candidates[:10]

        emote_candidates = [
            {"w": em_tok, "u": len(u_set)}
            for em_tok, u_set in pk_emote_users.items()
            if len(u_set) >= 2
        ]
        emote_candidates.sort(key=lambda x: x["u"], reverse=True)
        top_emotes = emote_candidates[:5]

        tags_per_peak.append({"words": top_words, "emotes": top_emotes})

    return tags_per_peak


def _detect_highlights_v2(
    buckets: dict,
    session_started_at: str | None = None,
    stopwords_version: str = "v1",
    stopwords_hash: str = "",
    truncated_input: bool = False,
    k_threshold: float = 2.8,
    prominence_sigma: float = 2.0,
    min_chats: int = 10,
    min_users: int = 5,
    top_k: int = 20,
) -> tuple[list[dict], float | None, int]:
    if not buckets:
        return [], None, 0

    sorted_times = sorted(buckets.keys())
    min_time = sorted_times[0]
    max_time = sorted_times[-1]
    n = int((max_time - min_time).total_seconds() // 10) + 1
    if n <= 0:
        return [], None, 0

    session_start_dt = _parse_timestamp(session_started_at) or min_time

    timeline_times = [min_time + timedelta(seconds=10 * i) for i in range(n)]
    chats = [0] * n
    users = [0] * n
    word_users_list = [None] * n
    emote_users_list = [None] * n
    chatter_sets = [None] * n

    for i, t_dt in enumerate(timeline_times):
        if t_dt in buckets:
            b = buckets[t_dt]
            chats[i] = b["chat_count"]
            users[i] = len(b["unique_chatters"])
            word_users_list[i] = b["word_users"]
            emote_users_list[i] = b["emote_users"]
            chatter_sets[i] = b["unique_chatters"]
        else:
            word_users_list[i] = {}
            emote_users_list[i] = {}
            chatter_sets[i] = set()

    peak_items, is_missing, sigma_session, valid_bucket_count = _detect_highlight_peaks(
        chats=chats,
        users=users,
        k_threshold=k_threshold,
        prominence_sigma=prominence_sigma,
        min_chats=min_chats,
        min_users=min_users,
    )
    if not peak_items:
        return [], sigma_session, valid_bucket_count

    tags_per_peak = _extract_highlight_tags(
        peak_items=peak_items,
        is_missing=is_missing,
        users=users,
        word_users_list=word_users_list,
        emote_users_list=emote_users_list,
        chatter_sets=chatter_sets,
    )

    params_snapshot = {
        "filter": "closing_3b",
        "k_threshold": k_threshold,
        "prominence_sigma": prominence_sigma,
        "min_chats": min_chats,
        "min_users": min_users,
        "phi_mode": "session_sigma_sq",
        "sigma_session": sigma_session,
        "stopwords_version": stopwords_version,
        "stopwords_hash": stopwords_hash,
        "truncated_input": truncated_input,
    }

    candidates = []
    for item, top_tags in zip(peak_items, tags_per_peak):
        p, s, e = item["p"], item["s"], item["e"]
        start_dt = timeline_times[s]
        peak_dt = timeline_times[p]
        end_dt = timeline_times[e]

        start_offset_ms = max(0, int((start_dt - session_start_dt).total_seconds() * 1000))
        peak_offset_ms = max(start_offset_ms, int((peak_dt - session_start_dt).total_seconds() * 1000))
        end_offset_ms = max(peak_offset_ms, int((end_dt - session_start_dt).total_seconds() * 1000))

        candidates.append({
            "start_time": start_dt,
            "peak_time": peak_dt,
            "end_time": end_dt,
            "start_offset_ms": start_offset_ms,
            "peak_offset_ms": peak_offset_ms,
            "end_offset_ms": end_offset_ms,
            "peak_chats": item["peak_chats"],
            "peak_unique_users": item["peak_unique_users"],
            "baseline_chats": item["baseline_chats"],
            "sigma_local": item["sigma_local"],
            "peak_z": item["peak_z"],
            "llr_score": item["llr_score"],
            "top_tags": top_tags,
            "params_json": params_snapshot,
        })

    candidates.sort(key=lambda x: x["llr_score"], reverse=True)
    final_highlights = candidates[:top_k]
    for rank_idx, item in enumerate(final_highlights, start=1):
        item["rank_in_session"] = rank_idx

    return final_highlights, sigma_session, valid_bucket_count


def _bulk_close_no_file_sessions(conn, session_ids: list[str]):
    sql = """
        UPDATE stream_sessions
        SET is_analyzed = TRUE,
            highlights_algo_version = %s
        WHERE session_id = ANY(%s)
          AND ended_at IS NOT NULL
          AND is_analyzed = FALSE;
    """
    with conn.cursor() as cur:
        cur.execute(sql, (NO_CHAT_FILE_VERSION, session_ids))


def _increment_analysis_attempts(conn, session_id: str):
    sql = """
        UPDATE stream_sessions
        SET analysis_attempts = analysis_attempts + 1
        WHERE session_id = %s;
    """
    with conn.cursor() as cur:
        cur.execute(sql, (session_id,))


def _insert_chat_signals(conn, session_id: str, buckets: dict):
    records = []
    for b_time, b in sorted(buckets.items()):
        top_10s_tokens = {k: v for k, v in b["tokens"].items() if v >= 2}
        records.append((
            session_id,
            b_time,
            b["chat_count"],
            len(b["unique_chatters"]),
            b["emote_count"],
            b["donation_count"],
            json.dumps(top_10s_tokens, ensure_ascii=False),
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
    with conn.cursor() as cur:
        execute_values(cur, sql, records, page_size=1000)


def _insert_token_summary(conn, session_id: str, session_tokens: Counter):
    filtered_tokens = {k: v for k, v in session_tokens.most_common(500) if v >= 5}
    sql = """
        INSERT INTO session_token_summary (session_id, token_stats)
        VALUES (%s, %s)
        ON CONFLICT (session_id) DO UPDATE
        SET token_stats = EXCLUDED.token_stats,
            created_at = CURRENT_TIMESTAMP;
    """
    with conn.cursor() as cur:
        cur.execute(sql, (session_id, json.dumps(filtered_tokens, ensure_ascii=False)))


def _replace_session_highlights(
    conn,
    session_id: str,
    stream_id: str,
    algo_version: str,
    highlights: list[dict],
):
    delete_sql = """
        DELETE FROM session_highlights
        WHERE session_id = %s AND algo_version = %s;
    """
    with conn.cursor() as cur:
        cur.execute(delete_sql, (session_id, algo_version))
        if not highlights:
            return

        records = [
            (
                session_id,
                stream_id,
                algo_version,
                h["rank_in_session"],
                h["start_time"],
                h["peak_time"],
                h["end_time"],
                h["start_offset_ms"],
                h["peak_offset_ms"],
                h["end_offset_ms"],
                h["peak_chats"],
                h["peak_unique_users"],
                h["baseline_chats"],
                h["sigma_local"],
                h["peak_z"],
                h["llr_score"],
                json.dumps(h["top_tags"], ensure_ascii=False),
                json.dumps(h["params_json"], ensure_ascii=False),
            )
            for h in highlights
        ]

        insert_sql = """
            INSERT INTO session_highlights (
                session_id, stream_id, algo_version, rank_in_session,
                start_time, peak_time, end_time,
                start_offset_ms, peak_offset_ms, end_offset_ms,
                peak_chats, peak_unique_users, baseline_chats,
                sigma_local, peak_z, llr_score, top_tags, params_json
            ) VALUES %s;
        """
        execute_values(cur, insert_sql, records, page_size=500)


def _update_session_analysis_state(
    conn,
    session_id: str,
    sigma_session: float | None,
    valid_bucket_count: int,
    sigma_algo_version: str,
    highlights_algo_version: str,
):
    sql = """
        UPDATE stream_sessions
        SET is_analyzed = TRUE,
            sigma_session = %s,
            valid_bucket_count = %s,
            sigma_algo_version = %s,
            highlights_algo_version = %s
        WHERE session_id = %s
          AND ended_at IS NOT NULL;
    """
    with conn.cursor() as cur:
        cur.execute(
            sql,
            (sigma_session, valid_bucket_count, sigma_algo_version, highlights_algo_version, session_id),
        )


post_broadcast_pipeline()
