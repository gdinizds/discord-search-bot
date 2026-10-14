package dev.gdinizds.discordsearchbot.adapter.out.persistence;

import dev.gdinizds.discordsearchbot.application.port.out.AuditLogPort;
import dev.gdinizds.discordsearchbot.config.Resilience;
import dev.gdinizds.discordsearchbot.domain.model.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;

public class JdbcAuditLogAdapter implements AuditLogPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditLogAdapter.class);

    private static final String INSERT = """
            INSERT INTO search_bot.search_audit_log
                   (correlation_id, guild_id, channel_id, user_id, username, event_type,
                    command_name, query, search_type, request_payload, response_payload,
                    status, error_message, execution_time_ms, created_at)
            VALUES (:correlationId, :guildId, :channelId, :userId, :username, :eventType,
                    :commandName, :query, :searchType, CAST(:requestPayload AS jsonb),
                    CAST(:responsePayload AS jsonb), :status, :errorMessage, :executionTimeMs, :createdAt)
            """;

    private final JdbcClient jdbc;
    private final Resilience resilience;

    public JdbcAuditLogAdapter(JdbcClient jdbc, Resilience resilience) {
        this.jdbc = jdbc;
        this.resilience = resilience;
    }

    @Override
    public void record(AuditLogEntry entry) {
        try {
            resilience.run("database", () -> jdbc.sql(INSERT)
                    .param("correlationId", entry.correlationId())
                    .param("guildId", entry.guildId())
                    .param("channelId", entry.channelId())
                    .param("userId", entry.userId())
                    .param("username", entry.username())
                    .param("eventType", entry.eventType())
                    .param("commandName", entry.commandName())
                    .param("query", entry.query())
                    .param("searchType", entry.searchType())
                    .param("requestPayload", entry.requestPayload())
                    .param("responsePayload", entry.responsePayload())
                    .param("status", entry.status())
                    .param("errorMessage", entry.errorMessage())
                    .param("executionTimeMs", entry.executionTimeMs())
                    .param("createdAt", Timestamp.from(entry.createdAt()))
                    .update());
        } catch (Exception e) {
            log.error("Failed to persist audit log [correlationId={}]: {}", entry.correlationId(), e.getMessage());
        }
    }
}
