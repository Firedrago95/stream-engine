-- 1. 방송 마스터 테이블 (1분마다 갱신)
CREATE TABLE streams (
    id                    BIGSERIAL PRIMARY KEY,
    stream_id             VARCHAR(255) NOT NULL UNIQUE,
    streamer_name         VARCHAR(255) NOT NULL,
    live_title            VARCHAR(255),
    profile_image_url     TEXT,
    category_name         VARCHAR(100),
    concurrent_user_count INT      NOT NULL DEFAULT 0,
    is_live               BOOLEAN      NOT NULL DEFAULT FALSE,
    last_update_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 메인 화면에서 라이브 중인 방송을 시청자가 많은 순서로 정렬하여 보여줄 때 사용
CREATE INDEX idx_streams_live_viewers ON streams (is_live, concurrent_user_count DESC);
-- 검색창에서 스트리머 이름의 일부만 입력해도 빠르게 연관 검색어와 결과를 찾아주기 위해 사용
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_streams_streamer_name_trgm ON streams USING gin (LOWER(streamer_name) gin_trgm_ops);


-- 2. 방송 세션 테이블 (방송 기록 보존)
CREATE TABLE stream_sessions (
    id                BIGSERIAL PRIMARY KEY,
    stream_id         VARCHAR(255) NOT NULL,        -- streams 테이블 참조
    session_id        VARCHAR(100) NOT NULL UNIQUE, -- 이번 방송의 고유 ID
    title             VARCHAR(255),
    category_name     VARCHAR(100),
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    peak_viewers      INT DEFAULT 0,
    created_at        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
-- 특정 스트리머의 상세 페이지에서 과거 방송 목록을 최신 날짜 순서대로 불러올 때 사용
CREATE INDEX idx_stream_sessions_stream_time ON stream_sessions (stream_id, started_at DESC);


-- 3. 분석 신호 저장 테이블 (session_id 추가)
CREATE TABLE analysis_signals (
    id          BIGSERIAL PRIMARY KEY,
    stream_id   VARCHAR(255) NOT NULL,
    session_id  VARCHAR(100) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    firepower   BIGINT       NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL,
    offset_ms   BIGINT       NOT NULL
);
-- 특정 스트리머의 전체 분석 신호를 시간 역순으로 빠르게 찾아올 때 사용
CREATE INDEX idx_stream_timestamp ON analysis_signals (stream_id, timestamp DESC);
-- 특정 과거 방송 세션 하나에 포함된 모든 분석 신호를 한 번에 조회할 때 사용
CREATE INDEX idx_analysis_signals_session ON analysis_signals (session_id);


-- 4. 분석 신호 요약 테이블 (session_id 기반 UNIQUE로 변경)
CREATE TABLE analysis_signals_summary (
    id               BIGSERIAL PRIMARY KEY,
    stream_id        VARCHAR(255) NOT NULL,
    session_id       VARCHAR(100) NOT NULL, -- 추가됨
    status           VARCHAR(50)  NOT NULL,
    firepower_avg    BIGINT       NOT NULL,
    firepower_max    BIGINT       NOT NULL,
    timestamp_minute TIMESTAMPTZ  NOT NULL,
    offset_ms        BIGINT,      NOT NULL,
    UNIQUE (session_id, status, offset_ms)
);
-- 특정 스트리머의 전체 1분 요약 데이터를 시간 역순으로 조회할 때 사용
CREATE INDEX idx_summary_stream_time ON analysis_signals_summary (stream_id, timestamp_minute DESC);
-- 과거 방송 상세 화면에서 해당 세션의 전체 화력 그래프를 그리기 위해 데이터를 가져올 때 사용
CREATE INDEX idx_summary_session ON analysis_signals_summary (session_id);


-- 5. 하이라이트 이벤트 테이블
CREATE TABLE highlight_events (
    id                BIGSERIAL PRIMARY KEY,
    stream_id         VARCHAR(255) NOT NULL,
    session_id        VARCHAR(100) NOT NULL,
    category          VARCHAR(50) DEFAULT 'GENERAL' NOT NULL,
    start_time        TIMESTAMPTZ NOT NULL,
    last_peak_time    TIMESTAMPTZ NOT NULL,
    end_time          TIMESTAMPTZ,
    start_time_offset BIGINT NOT NULL,
    last_peak_offset  BIGINT NOT NULL,
    end_time_offset   BIGINT,
    peak_firepower    BIGINT DEFAULT 0 NOT NULL,
    status            VARCHAR(20) NOT NULL,
    created_at        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
-- 하나의 방송 세션에서 처리 중인 하이라이트가 두 개 이상 동시에 만들어지지 않도록 db에서 방어
CREATE UNIQUE INDEX uidx_highlight_ongoing_session ON highlight_events (session_id) WHERE status = 'ONGOING';
-- 특정 방송 세션에서 분석이 끝난 하이라이트 목록만 따로 추려내어 조회할 때 사용
CREATE INDEX idx_highlight_session_find ON highlight_events (session_id, status);
-- 특정 스트리머의 전체 하이라이트 목록을 최신 날짜 순서대로 모아서 볼 때 사용
CREATE INDEX idx_highlight_streamer_date ON highlight_events (stream_id, start_time DESC);
