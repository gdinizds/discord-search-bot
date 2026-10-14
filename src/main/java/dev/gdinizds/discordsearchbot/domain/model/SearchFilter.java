package dev.gdinizds.discordsearchbot.domain.model;

public record SearchFilter(
        String query,
        SearchType type,
        String language,
        Integer safeSearch,
        int limit
) {
    public static SearchFilter of(String query, SearchType type, String language, Integer safeSearch, int limit) {
        String effectiveLang = (language == null || language.isBlank()) ? "pt-BR" : language.trim();
        Integer effectiveSafeSearch = safeSearch == null ? 1 : Math.max(0, Math.min(2, safeSearch));
        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 25);
        return new SearchFilter(query.trim(), type == null ? SearchType.WEB : type, effectiveLang, effectiveSafeSearch, effectiveLimit);
    }
}
