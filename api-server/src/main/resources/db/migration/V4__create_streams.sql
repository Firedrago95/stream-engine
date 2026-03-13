-- 1. 스트리머 마스터 테이블 생성
CREATE TABLE streams
(
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

-- 2. 메인 화면 조회 최적화 (is_live 필터링 + 최신순 정렬)
CREATE INDEX idx_streams_live_status ON streams (is_live, last_update_at DESC);

-- 2.1 메인 화면 시청자 순 정렬 최적화 인덱스
CREATE INDEX idx_streams_live_viewers ON streams (is_live, concurrent_user_count DESC);

-- 3. 스트리머 이름 검색용 인덱스
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_streams_streamer_name_trgm ON streams USING gin (LOWER(streamer_name) gin_trgm_ops);
