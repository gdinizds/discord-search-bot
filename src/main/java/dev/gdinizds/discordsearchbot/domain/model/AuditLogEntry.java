package dev.gdinizds.discordsearchbot.domain.model;

import java.time.Instant;

public record AuditLogEntry(
        Long id,
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String username,
        String eventType,
        String commandName,
        String query,
        String searchType,
        String requestPayload,
        String responsePayload,
        String status,
        String errorMessage,
        Long executionTimeMs,
        Instant createdAt
) {
    public static AuditLogEntry create(
            String correlationId,
            String guildId,
            String channelId,
            String userId,
            String username,
            String eventType,
            String commandName,
            String query,
            String searchType,
            String requestPayload,
            String responsePayload,
            String status,
            String errorMessage,
            Long executionTimeMs
    ) {
        return new AuditLogEntry(
                null,
                correlationId,
                guildId,
                channelId,
                userId,
                username,
                eventType,
                commandName,
                query,
                searchType,
                requestPayload,
                responsePayload,
                status,
                errorMessage,
                executionTimeMs,
                Instant.now()
        );
    }
}
