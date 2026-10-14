CREATE SCHEMA IF NOT EXISTS search_bot;

CREATE TABLE IF NOT EXISTS search_bot.search_audit_log (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    guild_id VARCHAR(64),
    channel_id VARCHAR(64),
    user_id VARCHAR(64),
    username VARCHAR(128),
    event_type VARCHAR(64) NOT NULL,
    command_name VARCHAR(64),
    query TEXT,
    search_type VARCHAR(32),
    request_payload JSONB NOT NULL,
    response_payload JSONB,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    execution_time_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_correlation_id ON search_bot.search_audit_log (correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON search_bot.search_audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_guild_id ON search_bot.search_audit_log (guild_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON search_bot.search_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_event_type ON search_bot.search_audit_log (event_type);

CREATE TABLE IF NOT EXISTS search_bot.search_sessions (
    id VARCHAR(64) PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    guild_id VARCHAR(64),
    channel_id VARCHAR(64),
    message_id VARCHAR(64),
    interaction_token VARCHAR(256),
    query TEXT NOT NULL,
    search_type VARCHAR(32) NOT NULL,
    current_index INT NOT NULL DEFAULT 0,
    total_results INT NOT NULL,
    results_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON search_bot.search_sessions (expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON search_bot.search_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_correlation_id ON search_bot.search_sessions (correlation_id);
