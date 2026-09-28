CREATE TABLE streamer_similarities (
    id                      BIGSERIAL PRIMARY KEY,
    stream_id               VARCHAR(255) NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    target_stream_id        VARCHAR(255),
    target_streamer_name    VARCHAR(100),
    target_profile_image    TEXT,
    target_primary_category VARCHAR(100),
    rank_order              INT NOT NULL DEFAULT 0,
    similarity_percent      DOUBLE PRECISION,
    common_chatter_count    INT,
    total_chatter_count     INT,
    calculated_date         DATE NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_streamer_similarities_stream_date_rank UNIQUE (stream_id, calculated_date, rank_order)
);

CREATE INDEX idx_streamer_similarities_lookup 
ON streamer_similarities (stream_id, calculated_date DESC);
