-- 1. 분석 신호 저장 테이블 (offset_ms 추가됨)
CREATE TABLE analysis_signals (
    id          BIGSERIAL PRIMARY KEY,
    stream_id   VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    firepower   BIGINT       NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL,
    offset_ms   BIGINT
);

CREATE INDEX idx_stream_timestamp ON analysis_signals (stream_id, timestamp DESC);
