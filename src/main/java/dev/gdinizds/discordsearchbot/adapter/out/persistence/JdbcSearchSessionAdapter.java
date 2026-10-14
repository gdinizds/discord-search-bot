package dev.gdinizds.discordsearchbot.adapter.out.persistence;

import dev.gdinizds.discordsearchbot.application.port.out.SearchSessionPort;
import dev.gdinizds.discordsearchbot.config.Resilience;
import dev.gdinizds.discordsearchbot.domain.model.HitItem;
import dev.gdinizds.discordsearchbot.domain.model.SearchSession;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class JdbcSearchSessionAdapter implements SearchSessionPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcSearchSessionAdapter.class);

    private static final String UPSERT = """
            INSERT INTO search_bot.search_sessions
                   (id, correlation_id, user_id, guild_id, channel_id, message_id,
                    interaction_token, query, search_type, current_index, total_results,
                    results_json, created_at, updated_at, expires_at)
            VALUES (:id, :correlationId, :userId, :guildId, :channelId, :messageId,
                    :interactionToken, :query, :searchType, :currentIndex, :totalResults,
                    CAST(:resultsJson AS jsonb), :createdAt, :updatedAt, :expiresAt)
            ON CONFLICT (id) DO UPDATE SET
                   current_index = EXCLUDED.current_index,
                   updated_at = EXCLUDED.updated_at,
                   message_id = COALESCE(EXCLUDED.message_id, search_sessions.message_id)
            """;

    private static final String FIND_BY_ID = """
            SELECT id, correlation_id, user_id, guild_id, channel_id, message_id,
                   interaction_token, query, search_type, current_index, total_results,
                   results_json, created_at, updated_at, expires_at
              FROM search_bot.search_sessions
             WHERE id = :id
               AND expires_at > CURRENT_TIMESTAMP
            """;

    private static final String UPDATE_INDEX = """
            UPDATE search_bot.search_sessions
               SET current_index = :newIndex,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = :id
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public JdbcSearchSessionAdapter(JdbcClient jdbc, ObjectMapper objectMapper, Resilience resilience) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resilience = resilience;
    }

    @Override
    public void save(SearchSession session) {
        String json = toJson(session.items());
        resilience.run("database", () -> jdbc.sql(UPSERT)
                .param("id", session.id())
                .param("correlationId", session.correlationId())
                .param("userId", session.userId())
                .param("guildId", session.guildId())
                .param("channelId", session.channelId())
                .param("messageId", session.messageId())
                .param("interactionToken", session.interactionToken())
                .param("query", session.query())
                .param("searchType", session.searchType().name())
                .param("currentIndex", session.currentIndex())
                .param("totalResults", session.totalResults())
                .param("resultsJson", json)
                .param("createdAt", Timestamp.from(session.createdAt()))
                .param("updatedAt", Timestamp.from(session.updatedAt()))
                .param("expiresAt", Timestamp.from(session.expiresAt()))
                .update());
    }

    @Override
    public Optional<SearchSession> findById(String sessionId) {
        return resilience.call("database", () -> jdbc.sql(FIND_BY_ID)
                .param("id", sessionId)
                .query((rs, rowNum) -> {
                    String json = rs.getString("results_json");
                    List<HitItem> items = fromJson(json);
                    return new SearchSession(
                            rs.getString("id"),
                            rs.getString("correlation_id"),
                            rs.getString("user_id"),
                            rs.getString("guild_id"),
                            rs.getString("channel_id"),
                            rs.getString("message_id"),
                            rs.getString("interaction_token"),
                            rs.getString("query"),
                            SearchType.valueOf(rs.getString("search_type")),
                            rs.getInt("current_index"),
                            rs.getInt("total_results"),
                            items,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant()
                    );
                })
                .optional());
    }

    @Override
    public void updateIndex(String sessionId, int newIndex) {
        resilience.run("database", () -> jdbc.sql(UPDATE_INDEX)
                .param("id", sessionId)
                .param("newIndex", newIndex)
                .update());
    }

    private String toJson(List<HitItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.error("Failed to serialize search hits: {}", e.getMessage());
            return "[]";
        }
    }

    private List<HitItem> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<HitItem>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize search hits: {}", e.getMessage());
            return List.of();
        }
    }
}
