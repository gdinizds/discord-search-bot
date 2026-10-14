package dev.gdinizds.discordsearchbot.domain.model;

public record ImageHit(
        String title,
        String imageUrl,
        String sourceUrl,
        String engine,
        String resolution,
        String s3Url
) {
    public ImageHit withS3Url(String newS3Url) {
        return new ImageHit(title, imageUrl, sourceUrl, engine, resolution, newS3Url);
    }
}
