package dev.gdinizds.discordsearchbot.config;

import dev.gdinizds.discordsearchbot.adapter.in.kafka.InboundEventConsumer;
import dev.gdinizds.discordsearchbot.adapter.in.kafka.InboundEventMapper;
import dev.gdinizds.discordsearchbot.adapter.out.kafka.GatewayResponsePublisher;
import dev.gdinizds.discordsearchbot.adapter.out.persistence.JdbcAuditLogAdapter;
import dev.gdinizds.discordsearchbot.adapter.out.persistence.JdbcSafeSearchConfigAdapter;
import dev.gdinizds.discordsearchbot.adapter.out.persistence.JdbcSearchSessionAdapter;
import dev.gdinizds.discordsearchbot.adapter.out.s3.S3ImageStore;
import dev.gdinizds.discordsearchbot.adapter.out.s3.SafeImageDownloader;
import dev.gdinizds.discordsearchbot.adapter.out.searxng.SearxngClient;
import dev.gdinizds.discordsearchbot.application.port.in.ExecuteSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.in.NavigateSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.out.AuditLogPort;
import dev.gdinizds.discordsearchbot.application.port.out.ImageStoragePort;
import dev.gdinizds.discordsearchbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordsearchbot.application.port.out.SafeSearchConfigPort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchEnginePort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchSessionPort;
import dev.gdinizds.discordsearchbot.application.service.SearchApplicationService;
import dev.gdinizds.discordsearchbot.domain.service.CommandTextParser;
import dev.gdinizds.discordsearchbot.domain.service.SearchLayoutBuilder;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CommandTextParser commandTextParser() {
        return new CommandTextParser();
    }

    @Bean
    public SearchLayoutBuilder searchLayoutBuilder() {
        return new SearchLayoutBuilder();
    }

    @Bean
    public SafeImageDownloader safeImageDownloader(SearchBotProperties properties) {
        var img = properties.images();
        return new SafeImageDownloader(img.downloadTimeout(), img.maxBytes(), img.userAgent());
    }

    @Bean
    public ImageStoragePort imageStoragePort(SafeImageDownloader downloader, S3Client s3Client,
                                            Resilience resilience, SearchBotProperties properties) {
        var s3 = properties.s3();
        return new S3ImageStore(downloader, s3Client, resilience, s3.endpoint(), s3.bucket(), s3.keyPrefix());
    }

    @Bean
    public SearchEnginePort searchEnginePort(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                            Resilience resilience, SearchBotProperties properties) {
        var sx = properties.searxng();
        return new SearxngClient(restClientBuilder, objectMapper, resilience, sx.url(), sx.timeout());
    }

    @Bean
    public AuditLogPort auditLogPort(JdbcClient jdbc, Resilience resilience) {
        return new JdbcAuditLogAdapter(jdbc, resilience);
    }

    @Bean
    public SearchSessionPort searchSessionPort(JdbcClient jdbc, ObjectMapper objectMapper, Resilience resilience) {
        return new JdbcSearchSessionAdapter(jdbc, objectMapper, resilience);
    }

    @Bean
    public ReplyPublisherPort replyPublisherPort(KafkaTemplate<String, String> kafkaTemplate,
                                                 ObjectMapper objectMapper,
                                                 SearchBotProperties properties) {
        return new GatewayResponsePublisher(kafkaTemplate, objectMapper, properties);
    }

    @Bean
    public InboundEventMapper inboundEventMapper(CommandTextParser commandTextParser) {
        return new InboundEventMapper(commandTextParser);
    }

    @Bean
    public SafeSearchConfigPort safeSearchConfigPort(JdbcClient jdbc, Resilience resilience) {
        return new JdbcSafeSearchConfigAdapter(jdbc, resilience);
    }

    @Bean
    public SearchApplicationService searchApplicationService(SearchEnginePort searchEngine,
                                                             ImageStoragePort imageStorage,
                                                             SearchSessionPort sessionPort,
                                                             ReplyPublisherPort replyPublisher,
                                                             AuditLogPort auditLog,
                                                             SafeSearchConfigPort safeSearchConfigPort,
                                                             SearchLayoutBuilder layoutBuilder,
                                                             ObjectMapper objectMapper,
                                                             Clock clock) {
        return new SearchApplicationService(searchEngine, imageStorage, sessionPort, replyPublisher,
                auditLog, safeSearchConfigPort, layoutBuilder, objectMapper, clock);
    }

    @Bean
    public InboundEventConsumer inboundEventConsumer(ObjectMapper objectMapper,
                                                     InboundEventMapper mapper,
                                                     ExecuteSearchUseCase searchUseCase,
                                                     NavigateSearchUseCase navigateUseCase,
                                                     SafeSearchConfigPort safeSearchConfigPort,
                                                     Bulkhead searchBulkhead,
                                                     @Qualifier("searchExecutor") ExecutorService executor,
                                                     SearchBotProperties properties) {
        var consumer = new InboundEventConsumer(objectMapper, mapper, searchUseCase, navigateUseCase,
                safeSearchConfigPort, searchBulkhead, executor);
        consumer.setDrainTimeout(properties.executor().drainTimeout());
        return consumer;
    }
}
