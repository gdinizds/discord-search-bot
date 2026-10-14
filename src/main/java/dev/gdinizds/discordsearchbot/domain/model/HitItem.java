package dev.gdinizds.discordsearchbot.domain.model;

public record HitItem(
        String title,
        String url,
        String snippet,
        String imageUrl,
        String mediaUrl,
        String engine,
        String resolution,
        SearchType type
) {
    public static HitItem fromSearchHit(SearchHit hit, SearchType type) {
        return new HitItem(
                hit.title(),
                hit.url(),
                hit.content(),
                null,
                null,
                hit.engine(),
                null,
                type
        );
    }

    public static HitItem fromImageHit(ImageHit hit, String storedMediaUrl) {
        return new HitItem(
                hit.title(),
                hit.sourceUrl() != null && !hit.sourceUrl().isBlank() ? hit.sourceUrl() : hit.imageUrl(),
                hit.title(),
                hit.imageUrl(),
                storedMediaUrl != null ? storedMediaUrl : hit.imageUrl(),
                hit.engine(),
                hit.resolution(),
                SearchType.IMAGE
        );
    }
}
