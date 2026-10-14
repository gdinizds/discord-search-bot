package dev.gdinizds.discordsearchbot.adapter.in.kafka;

import dev.gdinizds.discordsearchbot.application.port.in.ExecuteSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.in.NavigateSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.out.SafeSearchConfigPort;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.Executors;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InboundEventConsumerTest {

    @Mock
    private InboundEventMapper mapper;

    @Mock
    private ExecuteSearchUseCase searchUseCase;

    @Mock
    private NavigateSearchUseCase navigateUseCase;

    @Mock
    private SafeSearchConfigPort safeSearchConfigPort;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Bulkhead bulkhead = Bulkhead.ofDefaults("test");

    private InboundEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new InboundEventConsumer(
                objectMapper,
                mapper,
                searchUseCase,
                navigateUseCase,
                safeSearchConfigPort,
                bulkhead,
                Executors.newSingleThreadExecutor()
        );
    }

    @Test
    void onConfigUpdatedSavesGuildSafeSearch() {
        String json = """
                {
                  "eventType": "CONFIG_UPDATED",
                  "correlationId": "corr-cfg-1",
                  "guildId": "g123",
                  "settingKey": "safesearch_guild",
                  "level": 1
                }
                """;

        consumer.onConfigUpdated(json);

        verify(safeSearchConfigPort).save("g123", null, 1);
    }

    @Test
    void onConfigUpdatedSavesChannelSafeSearch() {
        String json = """
                {
                  "eventType": "CONFIG_UPDATED",
                  "correlationId": "corr-cfg-2",
                  "guildId": "g123",
                  "channelId": "ch456",
                  "settingKey": "safesearch_channel",
                  "level": 0
                }
                """;

        consumer.onConfigUpdated(json);

        verify(safeSearchConfigPort).save("g123", "ch456", 0);
    }

    @Test
    void onConfigUpdatedIgnoresOtherSettingKeys() {
        String json = """
                {
                  "eventType": "CONFIG_UPDATED",
                  "correlationId": "corr-cfg-3",
                  "guildId": "g123",
                  "settingKey": "other_key",
                  "level": 0
                }
                """;

        consumer.onConfigUpdated(json);

        verify(safeSearchConfigPort, never()).save("g123", null, 0);
    }
}
