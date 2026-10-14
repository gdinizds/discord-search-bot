package dev.gdinizds.discordsearchbot.adapter.out.kafka;

import dev.gdinizds.discordsearchbot.config.SearchBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CommandRegistrar.class);

    static final String SLASH_PESQUISAR = """
            {
              "prefix": "SLASH",
              "name": "pesquisar",
              "description": "Pesquise na internet via SearXNG com suporte a imagens, arquivos e páginas",
              "parameters": [
                { "name": "termo", "description": "Termo a ser pesquisado", "type": "STRING", "required": true },
                { "name": "tipo", "description": "Tipo de pesquisa: web, imagem, arquivo, noticia", "type": "STRING", "required": false },
                { "name": "localizacao", "description": "Idioma/região (ex: pt-BR, en-US)", "type": "STRING", "required": false },
                { "name": "safesearch", "description": "Filtro seguro: 0 (off), 1 (moderado), 2 (estrito)", "type": "INTEGER", "required": false }
              ],
              "is_deleted": false
            }
            """;

    static final String DOT_PESQUISAR = """
            {
              "prefix": "DOT",
              "name": "pesquisar",
              "description": "Pesquise na internet: .pesquisar termo ou .pesquisar --tipo=imagem termo",
              "is_deleted": false
            }
            """;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public CommandRegistrar(KafkaTemplate<String, String> kafkaTemplate, SearchBotProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.topics().gatewayCommands();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            kafkaTemplate.send(topic, "pesquisar", SLASH_PESQUISAR);
            kafkaTemplate.send(topic, "pesquisar", DOT_PESQUISAR);
            log.info("Registered Discord commands: /pesquisar, .pesquisar");
        } catch (Exception e) {
            log.error("Failed to register Discord commands: {}", e.getMessage());
        }
    }
}
