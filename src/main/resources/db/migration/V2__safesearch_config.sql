CREATE TABLE IF NOT EXISTS search_bot.safesearch_config (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(64) NOT NULL,
    channel_id VARCHAR(64) NOT NULL DEFAULT '',
    level INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_safesearch_scope UNIQUE (guild_id, channel_id)
);

CREATE INDEX IF NOT EXISTS idx_safesearch_scope ON search_bot.safesearch_config (guild_id, channel_id);
