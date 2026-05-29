CREATE TABLE stream_session_segments (
    id                    BIGSERIAL PRIMARY KEY,
    stream_id             VARCHAR(255) NOT NULL,
    session_id            VARCHAR(255) NOT NULL,
    title                 VARCHAR(255),
    category_name         VARCHAR(100),
    started_at            TIMESTAMPZ NOT NULL,
    ended_at              TIMESTAMPZ,
    started_offset_ms     BIGINT NOT NULL,
    ended_offset_ms       BIGINT
);
-- 과거 방송 정보 조회시, 해당 방송의 차트 세그먼트 구간 그리기 위한 용도
CREATE INDEX idx_session_segments_session_time ON stream_session_segments (session_id, started_at ASC);
