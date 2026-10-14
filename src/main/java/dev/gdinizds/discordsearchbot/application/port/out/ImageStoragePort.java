package dev.gdinizds.discordsearchbot.application.port.out;

public interface ImageStoragePort {
    String storeImage(String guildId, String correlationId, String imageUrl, String fallbackTitle);
}
