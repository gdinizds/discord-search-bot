package dev.gdinizds.discordsearchbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(SearchBotProperties properties) {
        var s3 = properties.s3();
        var credentials = AwsBasicCredentials.create(
                s3.accessKey() != null ? s3.accessKey() : "anonymous",
                s3.secretKey() != null ? s3.secretKey() : "anonymous"
        );

        return S3Client.builder()
                .endpointOverride(URI.create(s3.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(s3.region()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(10)))
                .build();
    }
}
