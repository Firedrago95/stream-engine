-- 3. 하이라이트 이벤트 테이블
CREATE TABLE highlight_events (
    id                BIGSERIAL PRIMARY KEY,
    stream_id         VARCHAR(255) NOT NULL,
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

CREATE UNIQUE INDEX uidx_highlight_ongoing_session
    ON highlight_events (stream_id)
    WHERE status = 'ONGOING';

CREATE INDEX idx_highlight_session_find ON highlight_events (stream_id, status);
CREATE INDEX idx_highlight_streamer_date ON highlight_events (stream_id, start_time DESC);
