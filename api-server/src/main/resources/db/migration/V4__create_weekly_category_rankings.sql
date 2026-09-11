CREATE TABLE weekly_category_rankings (
    id                     BIGSERIAL PRIMARY KEY,
    week_start_date        DATE NOT NULL,
    category_name          VARCHAR(100) NOT NULL,
    rank                   INT NOT NULL,
    accumulated_view_hours VARCHAR(50) NOT NULL,
    exact_hours            BIGINT NOT NULL,
    change_type            VARCHAR(20) NOT NULL,
    change_value           INT,
    icon                   VARCHAR(20),
    created_at             TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uidx_weekly_category_date_rank UNIQUE (week_start_date, rank),
    CONSTRAINT uidx_weekly_category_date_name UNIQUE (week_start_date, category_name)
);

CREATE INDEX idx_weekly_category_rankings_date ON weekly_category_rankings (week_start_date DESC, rank ASC);
