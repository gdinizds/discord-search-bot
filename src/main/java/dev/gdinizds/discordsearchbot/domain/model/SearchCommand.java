package dev.gdinizds.discordsearchbot.domain.model;

public record SearchCommand(
        String correlationId,
        String guildId,
        String channelId,
        String userId,
        String username,
        String interactionToken,
        String messageId,
        String rawQuery,
        SearchType searchType,
        String language,
        Integer safeSearch,
        boolean isSlash,
        String rawPayloadJson
) {
}
