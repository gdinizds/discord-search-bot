package dev.gdinizds.discordsearchbot.adapter.in.kafka;

import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import dev.gdinizds.discordsearchbot.domain.service.CommandTextParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class InboundEventMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InboundEventMapper mapper = new InboundEventMapper(new CommandTextParser());

    @Test
    void mapsSlashCommandWithAllParameters() throws Exception {
        String json = """
                {
                  "eventType": "INTERACTION_COMMAND",
                  "correlationId": "corr-101",
                  "guild": { "id": "111", "name": "Guild" },
                  "channelId": "222",
                  "user": { "id": "333", "username": "user1" },
                  "interactionToken": "tok-999",
                  "rawPayload": {
                    "args": {
                      "termo": "quarkus vs spring",
                      "tipo": "noticia",
                      "localizacao": "en-US",
                      "safesearch": 2
                    }
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        var command = mapper.fromSlash(event, json);

        assertThat(command.correlationId()).isEqualTo("corr-101");
        assertThat(command.rawQuery()).isEqualTo("quarkus vs spring");
        assertThat(command.searchType()).isEqualTo(SearchType.NEWS);
        assertThat(command.language()).isEqualTo("en-US");
        assertThat(command.safeSearch()).isEqualTo(2);
        assertThat(command.isSlash()).isTrue();
        assertThat(command.interactionToken()).isEqualTo("tok-999");
    }

    @Test
    void mapsDotCommandWithFlags() throws Exception {
        String json = """
                {
                  "eventType": "MESSAGE_COMMAND",
                  "correlationId": "corr-202",
                  "guild": { "id": "111", "name": "Guild" },
                  "channelId": "222",
                  "user": { "id": "333", "username": "user1" },
                  "messageId": "msg-888",
                  "rawPayload": {
                    "content": ".pesquisar --tipo=imagem alpine linux"
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        var command = mapper.fromDotCommand(event, json);

        assertThat(command.correlationId()).isEqualTo("corr-202");
        assertThat(command.rawQuery()).isEqualTo("alpine linux");
        assertThat(command.searchType()).isEqualTo(SearchType.IMAGE);
        assertThat(command.isSlash()).isFalse();
        assertThat(command.messageId()).isEqualTo("msg-888");
    }

    @Test
    void mapsButtonInteraction() throws Exception {
        String json = """
                {
                  "eventType": "INTERACTION_BUTTON",
                  "correlationId": "corr-303",
                  "guild": { "id": "111", "name": "Guild" },
                  "channelId": "222",
                  "user": { "id": "333", "username": "user1" },
                  "interactionToken": "btn-tok",
                  "messageId": "msg-777",
                  "rawPayload": {
                    "componentId": "search:next:session-abc"
                  }
                }
                """;

        InboundEvent event = objectMapper.readValue(json, InboundEvent.class);
        var nav = mapper.fromButton(event, json);

        assertThat(nav.correlationId()).isEqualTo("corr-303");
        assertThat(nav.action()).isEqualTo("next");
        assertThat(nav.sessionId()).isEqualTo("session-abc");
        assertThat(nav.interactionToken()).isEqualTo("btn-tok");
    }
}
