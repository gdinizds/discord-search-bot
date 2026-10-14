package dev.gdinizds.discordsearchbot.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"imagem", "IMAGE", "imagens", "img", "images"})
    void parsesImageAliases(String input) {
        assertThat(SearchType.from(input)).isEqualTo(SearchType.IMAGE);
        assertThat(SearchType.IMAGE.searxCategory()).isEqualTo("images");
    }

    @ParameterizedTest
    @ValueSource(strings = {"arquivo", "FILE", "arquivos", "files", "doc", "docs", "documento", "documentos"})
    void parsesFileAliases(String input) {
        assertThat(SearchType.from(input)).isEqualTo(SearchType.FILE);
        assertThat(SearchType.FILE.searxCategory()).isEqualTo("files");
    }

    @ParameterizedTest
    @ValueSource(strings = {"noticia", "NOTICIAS", "news"})
    void parsesNewsAliases(String input) {
        assertThat(SearchType.from(input)).isEqualTo(SearchType.NEWS);
        assertThat(SearchType.NEWS.searxCategory()).isEqualTo("news");
    }

    @ParameterizedTest
    @ValueSource(strings = {"web", "SITE", "geral", "unknown", ""})
    void parsesWebOrUnknownAsWeb(String input) {
        assertThat(SearchType.from(input)).isEqualTo(SearchType.WEB);
        assertThat(SearchType.WEB.searxCategory()).isEqualTo("general");
    }

    @Test
    void nullInputReturnsWeb() {
        assertThat(SearchType.from(null)).isEqualTo(SearchType.WEB);
    }
}
