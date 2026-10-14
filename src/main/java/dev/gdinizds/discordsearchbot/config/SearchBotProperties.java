package dev.gdinizds.discordsearchbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "search-bot")
public record SearchBotProperties(
        @DefaultValue Topics topics,
        @DefaultValue Searxng searxng,
        @DefaultValue S3 s3,
        @DefaultValue Images images,
        @DefaultValue Executor executor
) {
    public record Topics(
            @DefaultValue("discord.events.interaction.command") String inboundInteractions,
            @DefaultValue("discord.events.message.command") String inboundCommands,
            @DefaultValue("discord.events.interaction.button") String inboundButtons,
            @DefaultValue("discord.gateway.responses") String outboundResponses,
            @DefaultValue("discord.gateway.commands") String gatewayCommands
    ) {}

    public record Searxng(
            @DefaultValue("http://searxng.searxng.svc.cluster.local:8080") String url,
            @DefaultValue("5s") Duration timeout,
            @DefaultValue("pt-BR") String defaultLanguage,
            @DefaultValue("10") int defaultLimit
    ) {}

    public record S3(
            @DefaultValue("http://garage.garage.svc.cluster.local:3900") String endpoint,
            @DefaultValue("discord-gateway-attachments") String bucket,
            @DefaultValue("") String accessKey,
            @DefaultValue("") String secretKey,
            @DefaultValue("garage") String region,
            @DefaultValue("search-bot") String keyPrefix
    ) {}

    public record Images(
            @DefaultValue("5s") Duration downloadTimeout,
            @DefaultValue("10485760") long maxBytes,
            @DefaultValue("discord-search-bot/1.0 (+https://github.com/gdinizds/discord-search-bot)") String userAgent
    ) {}

    public record Executor(
            @DefaultValue("32") int concurrency,
            @DefaultValue("30s") Duration drainTimeout
    ) {}
}
