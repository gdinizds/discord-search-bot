package dev.gdinizds.discordsearchbot.domain.service;

import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTextParserTest {

    private final CommandTextParser parser = new CommandTextParser();

    @Test
    void parsesSimpleDotCommand() {
        var parsed = parser.parse(".pesquisar spring boot 4");

        assertThat(parsed.query()).isEqualTo("spring boot 4");
        assertThat(parsed.type()).isEqualTo(SearchType.WEB);
        assertThat(parsed.language()).isNull();
        assertThat(parsed.safeSearch()).isNull();
    }

    @Test
    void parsesKeywordTypePrefix() {
        var parsed = parser.parse(".pesquisar imagem gato persa");

        assertThat(parsed.query()).isEqualTo("gato persa");
        assertThat(parsed.type()).isEqualTo(SearchType.IMAGE);
    }

    @Test
    void parsesFlagsInDotCommand() {
        var parsed = parser.parse(".pesquisar --tipo=arquivo --lang=en-US -s 2 debian iso");

        assertThat(parsed.query()).isEqualTo("debian iso");
        assertThat(parsed.type()).isEqualTo(SearchType.FILE);
        assertThat(parsed.language()).isEqualTo("en-US");
        assertThat(parsed.safeSearch()).isEqualTo(2);
    }

    @Test
    void parsesShortFlags() {
        var parsed = parser.parse(".pesquisar -t noticia -l pt-BR eleições");

        assertThat(parsed.query()).isEqualTo("eleições");
        assertThat(parsed.type()).isEqualTo(SearchType.NEWS);
        assertThat(parsed.language()).isEqualTo("pt-BR");
    }

    @Test
    void handlesEmptyAndBlankCommands() {
        assertThat(parser.parse("").query()).isEmpty();
        assertThat(parser.parse(null).query()).isEmpty();
        assertThat(parser.parse(".pesquisar").query()).isEmpty();
    }
}
