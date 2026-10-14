package dev.gdinizds.discordsearchbot.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public record SearchSession(
        String id,
        String correlationId,
        String userId,
        String guildId,
        String channelId,
        String messageId,
        String interactionToken,
        String query,
        SearchType searchType,
        int currentIndex,
        int totalResults,
        List<HitItem> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public static SearchSession create(
            String id,
            String correlationId,
            String userId,
            String guildId,
            String channelId,
            String messageId,
            String interactionToken,
            String query,
            SearchType searchType,
            List<HitItem> items,
            Instant now,
            long ttlSeconds
    ) {
        int total = items == null ? 0 : items.size();
        return new SearchSession(
                id,
                correlationId,
                userId,
                guildId,
                channelId,
                messageId,
                interactionToken,
                query,
                searchType,
                0,
                total,
                items == null ? List.of() : List.copyOf(items),
                now,
                now,
                now.plusSeconds(ttlSeconds)
        );
    }

    public SearchSession withIndex(int newIndex, Instant now) {
        int boundedIndex = Math.max(0, Math.min(newIndex, totalResults - 1));
        return new SearchSession(
                id,
                correlationId,
                userId,
                guildId,
                channelId,
                messageId,
                interactionToken,
                query,
                searchType,
                boundedIndex,
                totalResults,
                items,
                createdAt,
                now,
                expiresAt
        );
    }

    public SearchSession withMessageId(String newMessageId) {
        return new SearchSession(
                id,
                correlationId,
                userId,
                guildId,
                channelId,
                newMessageId,
                interactionToken,
                query,
                searchType,
                currentIndex,
                totalResults,
                items,
                createdAt,
                updatedAt,
                expiresAt
        );
    }

    public HitItem currentItem() {
        if (items.isEmpty() || currentIndex < 0 || currentIndex >= items.size()) {
            return null;
        }
        return items.get(currentIndex);
    }

    public boolean hasNext() {
        return currentIndex < totalResults - 1;
    }

    public boolean hasPrevious() {
        return currentIndex > 0;
    }

    public int nextIndex() {
        return Math.min(currentIndex + 1, totalResults - 1);
    }

    public int previousIndex() {
        return Math.max(currentIndex - 1, 0);
    }

    public int randomIndex() {
        if (totalResults <= 1) {
            return 0;
        }
        int next;
        do {
            next = ThreadLocalRandom.current().nextInt(totalResults);
        } while (next == currentIndex && totalResults > 1);
        return next;
    }
}
