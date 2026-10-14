package dev.gdinizds.discordsearchbot.domain.service;

import dev.gdinizds.discordsearchbot.domain.model.HitItem;
import dev.gdinizds.discordsearchbot.domain.model.SearchSession;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchLayoutBuilderTest {

    private final SearchLayoutBuilder layoutBuilder = new SearchLayoutBuilder();

    @Test
    void rendersWebSearchResultWithButtons() {
        List<HitItem> items = List.of(
                new HitItem("Spring Boot Docs", "https://spring.io", "Official docs for Spring", null, null, "google", null, SearchType.WEB),
                new HitItem("Spring Boot Tutorial", "https://baeldung.com", "Tutorial for Spring", null, null, "bing", null, SearchType.WEB)
        );

        SearchSession session = SearchSession.create(
                "sess-123", "corr-123", "u1", "g1", "ch1", "m1", "tok1", "spring boot", SearchType.WEB, items, Instant.now(), 3600
        );

        var rendered = layoutBuilder.render(session, "guilherme");

        assertThat(rendered.embeds()).hasSize(1);
        Map<String, Object> embed = rendered.embeds().get(0);
        assertThat(embed.get("title")).isEqualTo("Spring Boot Docs");
        assertThat(embed.get("url")).isEqualTo("https://spring.io");
        assertThat(embed.get("description").toString()).contains("Official docs for Spring");
        assertThat(embed.get("description").toString()).contains("https://spring.io");

        assertThat(rendered.components()).hasSize(1);
        var row = rendered.components().get(0);
        List<?> buttons = (List<?>) row.get("components");
        assertThat(buttons).hasSize(4);

        Map<?, ?> prevBtn = (Map<?, ?>) buttons.get(0);
        assertThat(prevBtn.get("custom_id")).isEqualTo("search:prev:sess-123");
        assertThat(prevBtn.get("disabled")).isEqualTo(true);

        Map<?, ?> randBtn = (Map<?, ?>) buttons.get(1);
        assertThat(randBtn.get("custom_id")).isEqualTo("search:rand:sess-123");
        assertThat(randBtn.get("disabled")).isEqualTo(false);

        Map<?, ?> nextBtn = (Map<?, ?>) buttons.get(2);
        assertThat(nextBtn.get("custom_id")).isEqualTo("search:next:sess-123");
        assertThat(nextBtn.get("disabled")).isEqualTo(false);

        Map<?, ?> linkBtn = (Map<?, ?>) buttons.get(3);
        assertThat(linkBtn.get("url")).isEqualTo("https://spring.io");
    }

    @Test
    void rendersImageSearchWithMediaUrl() {
        List<HitItem> items = List.of(
                new HitItem("Cat Picture", "https://origin.com/cat.html", "Cute cat", "https://origin.com/cat.jpg", "https://s3.local/cat.jpg", "qwant", "1920x1080", SearchType.IMAGE)
        );

        SearchSession session = SearchSession.create(
                "sess-img", "corr-img", "u1", "g1", "ch1", "m1", "tok1", "cat", SearchType.IMAGE, items, Instant.now(), 3600
        );

        var rendered = layoutBuilder.render(session, "guilherme");
        Map<String, Object> embed = rendered.embeds().get(0);
        assertThat(embed.get("image")).isEqualTo(Map.of("url", "https://s3.local/cat.jpg"));
    }

    @Test
    void rendersEmptyAndErrorMessages() {
        var empty = layoutBuilder.renderEmpty("nothing", SearchType.WEB);
        assertThat(empty.embeds().get(0).get("title")).isEqualTo("🔍 Nenhum resultado encontrado");

        var error = layoutBuilder.renderError("Timeout connecting to search engine");
        assertThat(error.embeds().get(0).get("title")).isEqualTo("⚠️ Falha ao executar pesquisa");
    }
}
