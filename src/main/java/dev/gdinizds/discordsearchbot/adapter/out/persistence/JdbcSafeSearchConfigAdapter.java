package dev.gdinizds.discordsearchbot.adapter.out.persistence;

import dev.gdinizds.discordsearchbot.application.port.out.SafeSearchConfigPort;
import dev.gdinizds.discordsearchbot.config.Resilience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class JdbcSafeSearchConfigAdapter implements SafeSearchConfigPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcSafeSearchConfigAdapter.class);

    private static final String UPSERT = """
            INSERT INTO search_bot.safesearch_config (guild_id, channel_id, level, updated_at)
            VALUES (:guildId, :channelId, :level, CURRENT_TIMESTAMP)
            ON CONFLICT (guild_id, channel_id) DO UPDATE SET
                level = EXCLUDED.level,
                updated_at = CURRENT_TIMESTAMP
            """;

    private static final String FIND = """
            SELECT level FROM search_bot.safesearch_config
            WHERE guild_id = :guildId AND channel_id = :channelId
            """;

    private final JdbcClient jdbc;
    private final Resilience resilience;
    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();

    public JdbcSafeSearchConfigAdapter(JdbcClient jdbc, Resilience resilience) {
        this.jdbc = jdbc;
        this.resilience = resilience;
    }

    @Override
    public void save(String guildId, String channelId, int level) {
        String safeChannelId = channelId != null ? channelId.trim() : "";
        cache.put(key(guildId, safeChannelId), level);
        try {
            resilience.run("database", () -> jdbc.sql(UPSERT)
                    .param("guildId", guildId)
                    .param("channelId", safeChannelId)
                    .param("level", level)
                    .update());
        } catch (Exception e) {
            log.error("Failed to persist safesearch config [guild={}, channel={}, level={}]: {}", guildId, safeChannelId, level, e.getMessage());
        }
    }

    @Override
    public Optional<Integer> findLevel(String guildId, String channelId) {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        String safeChannelId = channelId != null ? channelId.trim() : "";
        String k = key(guildId, safeChannelId);
        Integer cached = cache.get(k);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            Optional<Integer> dbResult = resilience.call("database", () -> jdbc.sql(FIND)
                    .param("guildId", guildId)
                    .param("channelId", safeChannelId)
                    .query(Integer.class)
                    .optional());
            dbResult.ifPresent(lvl -> cache.put(k, lvl));
            return dbResult;
        } catch (Exception e) {
            log.warn("Failed to query safesearch config from DB [guild={}, channel={}]: {}", guildId, safeChannelId, e.getMessage());
            return Optional.ofNullable(cache.get(k));
        }
    }

    @Override
    public int resolveScopeLimit(String guildId, String channelId) {
        if (channelId != null && !channelId.isBlank()) {
            Optional<Integer> channelLimit = findLevel(guildId, channelId);
            if (channelLimit.isPresent()) {
                return channelLimit.get();
            }
        }
        if (guildId != null && !guildId.isBlank()) {
            Optional<Integer> guildLimit = findLevel(guildId, "");
            if (guildLimit.isPresent()) {
                return guildLimit.get();
            }
        }
        return 2;
    }

    private static String key(String guildId, String channelId) {
        return guildId + ":" + channelId;
    }
}
