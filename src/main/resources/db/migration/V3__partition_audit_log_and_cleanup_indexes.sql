SET search_path TO search_bot;

-- 1. Remove redundant indexes covered by UNIQUE constraints and low-cardinality index
DROP INDEX IF EXISTS search_bot.idx_safesearch_scope;
DROP INDEX IF EXISTS search_bot.idx_audit_event_type;

-- 2. Configure fillfactor for HOT updates on sessions table
ALTER TABLE search_bot.search_sessions SET (fillfactor = 85);

-- 3. Setup pg_partman schema and extension
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

-- 4. Migrate search_audit_log to range-partitioned table by created_at
ALTER TABLE search_bot.search_audit_log RENAME TO search_audit_log_old;

DROP INDEX IF EXISTS search_bot.idx_audit_correlation_id;
DROP INDEX IF EXISTS search_bot.idx_audit_user_id;
DROP INDEX IF EXISTS search_bot.idx_audit_guild_id;
DROP INDEX IF EXISTS search_bot.idx_audit_created_at;

CREATE TABLE search_bot.search_audit_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    correlation_id    VARCHAR(128) NOT NULL,
    guild_id          VARCHAR(64),
    channel_id        VARCHAR(64),
    user_id           VARCHAR(64),
    username          VARCHAR(128),
    event_type        VARCHAR(64)  NOT NULL,
    command_name      VARCHAR(64),
    query             TEXT,
    search_type       VARCHAR(32),
    request_payload   JSONB        NOT NULL,
    response_payload  JSONB,
    status            VARCHAR(32)  NOT NULL,
    error_message     TEXT,
    execution_time_ms BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create partition hierarchy starting from earliest historical record
SELECT partman.create_parent(
    p_parent_table    => 'search_bot.search_audit_log',
    p_control         => 'created_at',
    p_interval        => '1 day',
    p_start_partition => '2026-10-14',
    p_premake         => 3,
    p_default_table   => true
);

-- Migrate existing rows into partitioned hierarchy
INSERT INTO search_bot.search_audit_log (
    id, correlation_id, guild_id, channel_id, user_id, username,
    event_type, command_name, query, search_type,
    request_payload, response_payload, status, error_message,
    execution_time_ms, created_at
)
OVERRIDING SYSTEM VALUE
SELECT id, correlation_id, guild_id, channel_id, user_id, username,
       event_type, command_name, query, search_type,
       request_payload, response_payload, status, error_message,
       execution_time_ms, created_at
FROM search_bot.search_audit_log_old;

SELECT setval(pg_get_serial_sequence('search_bot.search_audit_log', 'id'), COALESCE(MAX(id), 1), true)
  FROM search_bot.search_audit_log;

DROP TABLE search_bot.search_audit_log_old;

-- Local indexes across partitions
CREATE INDEX idx_audit_correlation_id
    ON search_bot.search_audit_log (correlation_id);

CREATE INDEX idx_audit_user_id
    ON search_bot.search_audit_log (user_id);

CREATE INDEX idx_audit_guild_id
    ON search_bot.search_audit_log (guild_id);

CREATE INDEX idx_audit_created_at
    ON search_bot.search_audit_log (created_at DESC);

-- Configure pg_partman retention to 30 days
UPDATE partman.part_config
   SET retention = '30 days',
       retention_keep_table = false,
       retention_keep_index = false,
       infinite_time_partitions = true
 WHERE parent_table = 'search_bot.search_audit_log';
