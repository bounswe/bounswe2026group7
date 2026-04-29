-- Create match_history table to track top matches for each user
-- Used by MatchRecalculationScheduler to detect when top match changes
-- and publish notifications only on actual changes (not on every browse)

CREATE TABLE match_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    top_match_id BIGINT,
    top_match_name VARCHAR(255),
    match_score INTEGER NOT NULL DEFAULT 0,
    calculated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_match_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index for finding latest match history for a user
CREATE INDEX idx_match_history_user_type ON match_history(user_id, user_type);

-- Index for finding records by calculated_at for cleanup purposes
CREATE INDEX idx_match_history_calculated_at ON match_history(calculated_at);
