package dev.gdinizds.discordsearchbot.domain.model;

public record NavigationCommand(
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String username,
        String interactionToken,
        String messageId,
        String action,
        String sessionId,
        String rawPayloadJson
) {
}
