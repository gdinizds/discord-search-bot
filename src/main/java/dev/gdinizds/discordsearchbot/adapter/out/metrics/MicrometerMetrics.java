package dev.gdinizds.discordsearchbot.adapter.out.metrics;

import dev.gdinizds.discordsearchbot.application.port.out.SearchMetricsPort;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

public class MicrometerMetrics implements SearchMetricsPort {

    private final MeterRegistry registry;

    public MicrometerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordRequest(String type, String outcome) {
        registry.counter("discord.searchbot.requests",
                "type", type != null ? type.toLowerCase() : "unknown",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }

    @Override
    public void recordLatency(String type, Duration duration) {
        if (duration != null) {
            registry.timer("discord.searchbot.latency",
                    "type", type != null ? type.toLowerCase() : "unknown"
            ).record(duration);
        }
    }

    @Override
    public void recordSearxngQuery(String category, String outcome) {
        registry.counter("discord.searchbot.searxng.queries",
                "category", category != null ? category.toLowerCase() : "general",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }

    @Override
    public void recordImageStored(String outcome) {
        registry.counter("discord.searchbot.images.stored",
                "outcome", outcome != null ? outcome.toLowerCase() : "success"
        ).increment();
    }

    @Override
    public void recordNavigationClick(String action, String outcome) {
        registry.counter("discord.searchbot.navigation.clicks",
                "action", action != null ? action.toLowerCase() : "unknown",
                "outcome", outcome != null ? outcome.toLowerCase() : "unknown"
        ).increment();
    }
}
