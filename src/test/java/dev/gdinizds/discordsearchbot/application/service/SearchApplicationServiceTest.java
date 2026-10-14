package dev.gdinizds.discordsearchbot.application.service;

import dev.gdinizds.discordsearchbot.application.port.out.AuditLogPort;
import dev.gdinizds.discordsearchbot.application.port.out.ImageStoragePort;
import dev.gdinizds.discordsearchbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchEnginePort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchSessionPort;
import dev.gdinizds.discordsearchbot.domain.model.AuditLogEntry;
import dev.gdinizds.discordsearchbot.domain.model.HitItem;
import dev.gdinizds.discordsearchbot.domain.model.ImageHit;
import dev.gdinizds.discordsearchbot.domain.model.NavigationCommand;
import dev.gdinizds.discordsearchbot.domain.model.OutboundResponse;
import dev.gdinizds.discordsearchbot.domain.model.SearchCommand;
import dev.gdinizds.discordsearchbot.domain.model.SearchHit;
import dev.gdinizds.discordsearchbot.domain.model.SearchSession;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import dev.gdinizds.discordsearchbot.domain.service.SearchLayoutBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchApplicationServiceTest {

    @Mock
    private SearchEnginePort searchEngine;

    @Mock
    private ImageStoragePort imageStorage;

    @Mock
    private SearchSessionPort sessionPort;

    @Mock
    private ReplyPublisherPort replyPublisher;

    @Mock
    private AuditLogPort auditLog;

    private final SearchLayoutBuilder layoutBuilder = new SearchLayoutBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC);

    private SearchApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SearchApplicationService(
                searchEngine,
                imageStorage,
                sessionPort,
                replyPublisher,
                auditLog,
                layoutBuilder,
                objectMapper,
                clock
        );
    }

    @Test
    void executesWebSearchSuccessfully() {
        when(searchEngine.searchWeb(eq("java 25"), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new SearchHit("Java 25 Release", "https://openjdk.org/25", "Features of Java 25", "google"),
                        new SearchHit("GraalVM 25", "https://graalvm.org", "Oracle GraalVM 25", "bing")
                ));

        SearchCommand command = new SearchCommand(
                "corr-1", "g1", "ch1", "u1", "guilherme", "tok-1", null,
                "java 25", SearchType.WEB, "pt-BR", 1, true, "{}"
        );

        service.execute(command);

        verify(sessionPort).save(any(SearchSession.class));

        var responseCaptor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(responseCaptor.capture());
        OutboundResponse response = responseCaptor.getValue();
        assertThat(response.embeds()).isNotEmpty();
        assertThat(response.components()).isNotEmpty();

        var auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLog).record(auditCaptor.capture());
        AuditLogEntry audit = auditCaptor.getValue();
        assertThat(audit.status()).isEqualTo("SUCCESS");
        assertThat(audit.correlationId()).isEqualTo("corr-1");
        assertThat(audit.query()).isEqualTo("java 25");
    }

    @Test
    void executesImageSearchAndStoresToS3() {
        when(searchEngine.searchImages(eq("capivara"), any(), any(), anyInt()))
                .thenReturn(List.of(
                        new ImageHit("Capivara 1", "https://origin.com/1.jpg", "https://site.com/1", "bing", "800x600", null),
                        new ImageHit("Capivara 2", "https://origin.com/2.jpg", "https://site.com/2", "google", "1024x768", null)
                ));
        when(imageStorage.storeImage(anyString(), anyString(), eq("https://origin.com/1.jpg"), anyString()))
                .thenReturn("https://garage.s3.local/capivara-1.jpg");

        SearchCommand command = new SearchCommand(
                "corr-2", "g1", "ch1", "u1", "guilherme", "tok-2", null,
                "capivara", SearchType.IMAGE, "pt-BR", 1, true, "{}"
        );

        service.execute(command);

        verify(imageStorage).storeImage("g1", "corr-2", "https://origin.com/1.jpg", "Capivara 1");

        var sessionCaptor = ArgumentCaptor.forClass(SearchSession.class);
        verify(sessionPort).save(sessionCaptor.capture());
        SearchSession saved = sessionCaptor.getValue();
        assertThat(saved.items().get(0).mediaUrl()).isEqualTo("https://garage.s3.local/capivara-1.jpg");

        var responseCaptor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(responseCaptor.capture());
        OutboundResponse response = responseCaptor.getValue();
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0).url()).isEqualTo("https://garage.s3.local/capivara-1.jpg");
        assertThat(response.attachments().get(0).name()).isEqualTo("capivara-1.jpg");
        assertThat(response.embeds().get(0).get("image")).isEqualTo(Map.of("url", "attachment://capivara-1.jpg"));
    }

    @Test
    void handlesEmptySearchResults() {
        when(searchEngine.searchWeb(eq("nothing"), any(), any(), anyInt()))
                .thenReturn(List.of());

        SearchCommand command = new SearchCommand(
                "corr-3", "g1", "ch1", "u1", "guilherme", null, "msg-1",
                "nothing", SearchType.WEB, "pt-BR", 1, false, "{}"
        );

        service.execute(command);

        var auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLog).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo("EMPTY");
    }

    @Test
    void navigatesToNextResult() {
        List<HitItem> items = List.of(
                new HitItem("Item 1", "https://1.com", "Desc 1", null, null, "google", null, SearchType.WEB),
                new HitItem("Item 2", "https://2.com", "Desc 2", null, null, "bing", null, SearchType.WEB)
        );
        SearchSession existing = SearchSession.create(
                "sess-1", "corr-nav", "u1", "g1", "ch1", "msg-10", "tok-nav", "query", SearchType.WEB, items, clock.instant(), 3600
        );
        when(sessionPort.findById("sess-1")).thenReturn(Optional.of(existing));

        NavigationCommand nav = new NavigationCommand(
                "corr-nav", "g1", "ch1", "u1", "guilherme", "tok-nav", "msg-10", "next", "sess-1", "{}"
        );

        service.navigate(nav);

        verify(sessionPort).updateIndex("sess-1", 1);

        var responseCaptor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(responseCaptor.capture());
        assertThat(responseCaptor.getValue().responseType()).isEqualTo("UPDATE_MESSAGE");

        var auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLog).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo("SUCCESS");
    }

    @Test
    void handlesExpiredSessionOnNavigation() {
        when(sessionPort.findById("expired-id")).thenReturn(Optional.empty());

        NavigationCommand nav = new NavigationCommand(
                "corr-exp", "g1", "ch1", "u1", "guilherme", "tok-exp", "msg-10", "next", "expired-id", "{}"
        );

        service.navigate(nav);

        var responseCaptor = ArgumentCaptor.forClass(OutboundResponse.class);
        verify(replyPublisher).publish(responseCaptor.capture());
        assertThat(responseCaptor.getValue().content()).contains("expirou");

        var auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLog).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo("EXPIRED");
    }
}
