package dev.gdinizds.discordsearchbot.adapter.out.s3;

import dev.gdinizds.discordsearchbot.application.port.out.ImageStoragePort;
import dev.gdinizds.discordsearchbot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

public class S3ImageStore implements ImageStoragePort {

    static final String INSTANCE = "s3";

    private static final Logger log = LoggerFactory.getLogger(S3ImageStore.class);

    private final SafeImageDownloader downloader;
    private final S3Client s3Client;
    private final Resilience resilience;
    private final String endpoint;
    private final String bucket;
    private final String keyPrefix;

    public S3ImageStore(SafeImageDownloader downloader, S3Client s3Client, Resilience resilience,
                        String endpoint, String bucket, String keyPrefix) {
        this.downloader = downloader;
        this.s3Client = s3Client;
        this.resilience = resilience;
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public String storeImage(String guildId, String correlationId, String imageUrl, String fallbackTitle) {
        try {
            SafeImageDownloader.DownloadedImage downloaded = downloader.download(imageUrl);
            String safeGuild = (guildId == null || guildId.isBlank()) ? "global" : guildId;
            String safeCorr = (correlationId == null || correlationId.isBlank()) ? UUID.randomUUID().toString() : correlationId;
            String filename = "img-" + UUID.randomUUID().toString().substring(0, 8) + "." + downloaded.format().extension();
            String key = keyPrefix + "/" + safeGuild + "/" + safeCorr + "/" + filename;

            resilience.run(INSTANCE, () -> s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(downloaded.format().mimeType())
                            .contentLength((long) downloaded.data().length)
                            .build(),
                    RequestBody.fromBytes(downloaded.data())));

            return endpoint + "/" + bucket + "/" + key;
        } catch (Exception e) {
            log.warn("Failed to download and store image from {}: {}", imageUrl, e.getMessage());
            return imageUrl;
        }
    }
}
