package dev.gdinizds.discordsearchbot.adapter.out.searxng;

import dev.gdinizds.discordsearchbot.application.port.out.SearchEnginePort;
import dev.gdinizds.discordsearchbot.config.Resilience;
import dev.gdinizds.discordsearchbot.domain.model.ImageHit;
import dev.gdinizds.discordsearchbot.domain.model.SearchHit;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SearxngClient implements SearchEnginePort {

    static final String INSTANCE = "searxng";

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Resilience resilience;

    public SearxngClient(RestClient.Builder builder, ObjectMapper objectMapper, Resilience resilience,
                         String baseUrl, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.min(timeout.toMillis(), 5000));
        requestFactory.setReadTimeout((int) timeout.toMillis());

        this.client = builder
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.resilience = resilience;
    }

    @Override
    public List<SearchHit> searchWeb(String query, String language, Integer safeSearch, int limit) {
        return executeSearch(query, "general", language, safeSearch, limit);
    }

    @Override
    public List<ImageHit> searchImages(String query, String language, Integer safeSearch, int limit) {
        String body = resilience.call(INSTANCE, () -> client.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("categories", "images")
                        .queryParam("language", language != null ? language : "pt-BR")
                        .queryParam("safesearch", safeSearch != null ? safeSearch : 1)
                        .build())
                .retrieve()
                .body(String.class));

        List<ImageHit> hits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JsonNode root = parseJson(body);
        for (JsonNode result : root.path("results")) {
            if (hits.size() >= limit) break;
            String imageUrl = result.path("img_src").asString("").trim();
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) continue;
            if (!seen.add(imageUrl)) continue;
            String source = result.path("source").asString("");
            if (source.isBlank()) source = result.path("engine").asString("");
            hits.add(new ImageHit(
                    result.path("title").asString(""),
                    imageUrl,
                    result.path("url").asString(""),
                    source,
                    result.path("resolution").asString(""),
                    null
            ));
        }
        return hits;
    }

    @Override
    public List<SearchHit> searchFiles(String query, String language, Integer safeSearch, int limit) {
        return executeSearch(query, "files", language, safeSearch, limit);
    }

    @Override
    public List<SearchHit> searchNews(String query, String language, Integer safeSearch, int limit) {
        return executeSearch(query, "news", language, safeSearch, limit);
    }

    private List<SearchHit> executeSearch(String query, String category, String language, Integer safeSearch, int limit) {
        String body = resilience.call(INSTANCE, () -> client.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("categories", category)
                        .queryParam("language", language != null ? language : "pt-BR")
                        .queryParam("safesearch", safeSearch != null ? safeSearch : 1)
                        .build())
                .retrieve()
                .body(String.class));

        List<SearchHit> hits = new ArrayList<>();
        JsonNode root = parseJson(body);
        for (JsonNode result : root.path("results")) {
            if (hits.size() >= limit) break;
            String url = result.path("url").asString("").trim();
            if (url.isBlank()) continue;
            String engine = result.path("engine").asString("");
            hits.add(new SearchHit(
                    result.path("title").asString(""),
                    url,
                    result.path("content").asString(""),
                    engine
            ));
        }
        return hits;
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse SearXNG response: " + e.getMessage(), e);
        }
    }
}
