package dev.gdinizds.discordsearchbot.adapter.out.persistence;

import dev.gdinizds.discordsearchbot.config.Resilience;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcSafeSearchConfigAdapterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private JdbcClient jdbc;

    private Resilience resilience;
    private JdbcSafeSearchConfigAdapter adapter;

    @BeforeEach
    void setUp() {
        resilience = new Resilience(
                CircuitBreakerRegistry.ofDefaults(),
                RetryRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),
                BulkheadRegistry.ofDefaults(),
                Executors.newSingleThreadExecutor()
        );
        adapter = new JdbcSafeSearchConfigAdapter(jdbc, resilience);
    }

    @Test
    void resolveScopeLimitReturnsChannelOverrideWhenPresent() {
        when(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "chan-1")
                .query(Integer.class)
                .optional())
                .thenReturn(Optional.of(0));

        int limit = adapter.resolveScopeLimit("guild-1", "chan-1");
        assertThat(limit).isEqualTo(0);
    }

    @Test
    void resolveScopeLimitFallsBackToGuildDefaultWhenChannelNotConfigured() {
        when(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "chan-1")
                .query(Integer.class)
                .optional())
                .thenReturn(Optional.empty());

        when(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "")
                .query(Integer.class)
                .optional())
                .thenReturn(Optional.of(1));

        int limit = adapter.resolveScopeLimit("guild-1", "chan-1");
        assertThat(limit).isEqualTo(1);
    }

    @Test
    void resolveScopeLimitFallsBackToStrictWhenNeitherConfigured() {
        when(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "chan-1")
                .query(Integer.class)
                .optional())
                .thenReturn(Optional.empty());

        when(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "")
                .query(Integer.class)
                .optional())
                .thenReturn(Optional.empty());

        int limit = adapter.resolveScopeLimit("guild-1", "chan-1");
        assertThat(limit).isEqualTo(2);
    }

    @Test
    void saveStoresInCacheAndExecutesUpsert() {
        adapter.save("guild-1", "chan-1", 0);

        int limit = adapter.resolveScopeLimit("guild-1", "chan-1");
        assertThat(limit).isEqualTo(0);

        verify(jdbc.sql(anyString())
                .param("guildId", "guild-1")
                .param("channelId", "chan-1")
                .param("level", 0))
                .update();
    }
}
