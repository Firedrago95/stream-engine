-- ============================================================================
-- 치즈픽 데이터 웨어하우스 (cheesepick_dw) V3: 하이라이트(산) 및 세션 체질 프로파일 추가
-- ============================================================================

-- 1. stream_sessions 테이블에 잔차 몸통 기반 흔들림 체질, 재시도 횟수 및 분석 알고리즘 버전 컬럼 추가
ALTER TABLE stream_sessions
    ADD COLUMN analysis_attempts       SMALLINT         NOT NULL DEFAULT 0,
    ADD COLUMN sigma_session           DOUBLE PRECISION,
    ADD COLUMN valid_bucket_count      INT,
    ADD COLUMN sigma_algo_version      VARCHAR(32),
    ADD COLUMN highlights_algo_version VARCHAR(32);

-- 2. 사후 분석 하이라이트(산) 결과 테이블
-- 모든 *_offset_ms 컬럼은 해당 세션의 stream_sessions.started_at 기준 경과 밀리초(ms)임
CREATE TABLE session_highlights (
    id                BIGSERIAL        PRIMARY KEY,
    session_id        VARCHAR(64)      NOT NULL REFERENCES stream_sessions(session_id) ON DELETE CASCADE,
    stream_id         VARCHAR(64)      NOT NULL,
    algo_version      VARCHAR(32)      NOT NULL,
    rank_in_session   INT              NOT NULL,
    start_time        TIMESTAMPTZ      NOT NULL,
    peak_time         TIMESTAMPTZ      NOT NULL,
    end_time          TIMESTAMPTZ      NOT NULL,
    start_offset_ms   BIGINT           NOT NULL,
    peak_offset_ms    BIGINT           NOT NULL,
    end_offset_ms     BIGINT           NOT NULL,
    peak_chats        INT              NOT NULL,
    peak_unique_users INT              NOT NULL,
    baseline_chats    DOUBLE PRECISION NOT NULL,
    sigma_local       DOUBLE PRECISION NOT NULL,
    peak_z            DOUBLE PRECISION NOT NULL,
    llr_score         DOUBLE PRECISION NOT NULL,
    -- 상위 단어 후보 최대 10개 + 이모티콘 후보 최대 5개 저장:
    -- {"words": [{"w": "다시다", "u": 7, "bg_u": 1, "z": 3.82}, ...], "emotes": [{"w": ":d_106:", "u": 5}, ...]}
    top_tags          JSONB            NOT NULL DEFAULT '{"words": [], "emotes": []}'::jsonb,
    params_json       JSONB            NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_session_highlight_peak UNIQUE (session_id, algo_version, peak_time),
    CONSTRAINT uq_session_highlight_rank UNIQUE (session_id, algo_version, rank_in_session),
    CONSTRAINT ck_highlight_time_order   CHECK (start_time <= peak_time AND peak_time <= end_time),
    CONSTRAINT ck_highlight_rank_pos     CHECK (rank_in_session >= 1)
);

CREATE INDEX idx_dw_highlights_session_rank
    ON session_highlights (session_id, algo_version, rank_in_session ASC);

CREATE INDEX idx_dw_highlights_stream_peak
    ON session_highlights (stream_id, peak_time DESC);
