-- 1. 분석 신호 저장 테이블 생성
CREATE TABLE analysis_signals (
    id          BIGSERIAL PRIMARY KEY,
    stream_id   VARCHAR(255) NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    firepower   BIGINT       NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL
);

-- 2. 특정 스트림의 최신 신호를 빠르게 조회하기 위한 복합 인덱스
-- stream_id로 먼저 필터링하고, timestamp 기준 내림차순(DESC) 정렬 최적화
CREATE INDEX idx_stream_timestamp ON analysis_signals (stream_id, timestamp DESC);
