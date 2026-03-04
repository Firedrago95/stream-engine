-- 하이라이트 구간 정보를 저장하는 테이블
CREATE TABLE highlight_events (
    id              BIGSERIAL PRIMARY KEY,
    stream_id       VARCHAR(255) NOT NULL,
    category        VARCHAR(50) DEFAULT 'GENERAL' NOT NULL, -- 하이라이트 성격
    start_time      TIMESTAMPTZ NOT NULL,                    -- 보정된 시작 시간
    last_peak_time  TIMESTAMPTZ NOT NULL,                    -- 마지막 PEAK 시점
    end_time        TIMESTAMPTZ,                             -- 유예 후 확정된 종료 시간
    peak_firepower  BIGINT DEFAULT 0,                       -- 해당 구간 내 기록된 최고 화력
    status          VARCHAR(20) NOT NULL,                   -- ONGOING(진행중), FINISHED(종료)
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uidx_highlight_ongoing_session
    ON highlight_events (stream_id)
    WHERE status = 'ONGOING';

CREATE INDEX idx_highlight_session_find ON highlight_events (stream_id, status);
CREATE INDEX idx_highlight_streamer_date ON highlight_events (stream_id, start_time DESC);
