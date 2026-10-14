package dev.gdinizds.discordsearchbot.domain.model;

public record SearchHit(
        String title,
        String url,
        String content,
        String engine
) {
}
