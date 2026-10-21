-- ============================================================================
-- 치즈픽 데이터 웨어하우스 (cheesepick_dw) 초기 스키마 (V1)
-- ============================================================================

-- 1. 방송 세션 마스터 (방송 1회당 1행)
CREATE TABLE stream_sessions (
    session_id            VARCHAR(64)  PRIMARY KEY,
    stream_id             VARCHAR(64)  NOT NULL,
    streamer_name         VARCHAR(255) NOT NULL,
    title                 VARCHAR(255),
    category_name         VARCHAR(100),
    started_at            TIMESTAMPTZ  NOT NULL,
    ended_at              TIMESTAMPTZ,
    peak_viewers          INT          NOT NULL DEFAULT 0,
    average_viewer_count  INT          NOT NULL DEFAULT 0,
    paid_promotion        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_adult              BOOLEAN      NOT NULL DEFAULT FALSE,
    collected_seconds     INT,
    is_analyzed           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dw_sessions_stream_started
    ON stream_sessions (stream_id, started_at DESC);

CREATE INDEX idx_dw_sessions_pending_analysis
    ON stream_sessions (ended_at)
    WHERE ended_at IS NOT NULL AND is_analyzed = FALSE;


-- 2. 방송 중 카테고리/방제 변경 구간 (세그먼트)
CREATE TABLE stream_session_segments (
    id                BIGSERIAL    PRIMARY KEY,
    session_id        VARCHAR(64)  NOT NULL,
    stream_id         VARCHAR(64)  NOT NULL,
    title             VARCHAR(255),
    category_name     VARCHAR(100),
    started_at        TIMESTAMPTZ  NOT NULL,
    ended_at          TIMESTAMPTZ,
    start_offset_ms   BIGINT       NOT NULL,
    end_offset_ms     BIGINT,
    paid_promotion    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_adult          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_true_ad        BOOLEAN
);

CREATE INDEX idx_dw_segments_session_offset
    ON stream_session_segments (session_id, start_offset_ms ASC);


-- 3. 30초 주기 시청자 수 시계열 (월별 Range 파티셔닝)
CREATE TABLE view_metric_timelines (
    session_id    VARCHAR(64)  NOT NULL,
    stream_id     VARCHAR(64)  NOT NULL,
    recorded_at   TIMESTAMPTZ  NOT NULL,
    viewer_count  INT          NOT NULL,
    PRIMARY KEY (session_id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE INDEX idx_dw_vmt_recorded_brin
    ON view_metric_timelines USING brin (recorded_at);

CREATE TABLE view_metric_timelines_y2026m10 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2026-10-01 00:00:00+09') TO ('2026-11-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2026m11 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2026-11-01 00:00:00+09') TO ('2026-12-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2026m12 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2026-12-01 00:00:00+09') TO ('2027-01-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m01 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-01-01 00:00:00+09') TO ('2027-02-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m02 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-02-01 00:00:00+09') TO ('2027-03-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m03 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-03-01 00:00:00+09') TO ('2027-04-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m04 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-04-01 00:00:00+09') TO ('2027-05-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m05 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-05-01 00:00:00+09') TO ('2027-06-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m06 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-06-01 00:00:00+09') TO ('2027-07-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m07 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-07-01 00:00:00+09') TO ('2027-08-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m08 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-08-01 00:00:00+09') TO ('2027-09-01 00:00:00+09');
CREATE TABLE view_metric_timelines_y2027m09 PARTITION OF view_metric_timelines
    FOR VALUES FROM ('2027-09-01 00:00:00+09') TO ('2027-10-01 00:00:00+09');


-- 4. 10초 주기 채팅 비식별 집계 시계열 (하이라이트 감지 & 구간 성격 분류용)
CREATE TABLE chat_signal_10s (
    session_id        VARCHAR(64)  NOT NULL,
    bucket_time       TIMESTAMPTZ  NOT NULL,
    chat_count        INT          NOT NULL,
    unique_chatter    INT          NOT NULL,
    emote_count       INT          NOT NULL,
    donation_count    INT          NOT NULL,
    top_tokens        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (session_id, bucket_time)
) PARTITION BY RANGE (bucket_time);

CREATE TABLE chat_signal_10s_y2026m10 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2026-10-01 00:00:00+09') TO ('2026-11-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2026m11 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2026-11-01 00:00:00+09') TO ('2026-12-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2026m12 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2026-12-01 00:00:00+09') TO ('2027-01-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m01 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-01-01 00:00:00+09') TO ('2027-02-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m02 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-02-01 00:00:00+09') TO ('2027-03-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m03 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-03-01 00:00:00+09') TO ('2027-04-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m04 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-04-01 00:00:00+09') TO ('2027-05-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m05 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-05-01 00:00:00+09') TO ('2027-06-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m06 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-06-01 00:00:00+09') TO ('2027-07-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m07 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-07-01 00:00:00+09') TO ('2027-08-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m08 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-08-01 00:00:00+09') TO ('2027-09-01 00:00:00+09');
CREATE TABLE chat_signal_10s_y2027m09 PARTITION OF chat_signal_10s
    FOR VALUES FROM ('2027-09-01 00:00:00+09') TO ('2027-10-01 00:00:00+09');


-- 5. 세션별 단어 요약 보따리 (스트리머 고유 밈 TF-IDF & 전체 Lift 분석용, 세션당 1행)
CREATE TABLE session_token_summary (
    session_id    VARCHAR(64)  PRIMARY KEY,
    token_stats   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
