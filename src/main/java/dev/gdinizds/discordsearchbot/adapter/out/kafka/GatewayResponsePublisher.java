package dev.gdinizds.discordsearchbot.adapter.out.kafka;

import dev.gdinizds.discordsearchbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordsearchbot.config.SearchBotProperties;
import dev.gdinizds.discordsearchbot.domain.model.OutboundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class GatewayResponsePublisher implements ReplyPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(GatewayResponsePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public GatewayResponsePublisher(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper,
                                    SearchBotProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = properties.topics().outboundResponses();
    }

    @Override
    public void publish(OutboundResponse response) {
        try {
            List<GatewayResponse.Attachment> attachments = null;
            if (response.attachments() != null && !response.attachments().isEmpty()) {
                attachments = response.attachments().stream()
                        .map(a -> new GatewayResponse.Attachment(a.url(), a.name(), a.description()))
                        .toList();
            }

            GatewayResponse gatewayPayload = new GatewayResponse(
                    response.responseType(),
                    response.interactionToken(),
                    response.messageId(),
                    response.channelId(),
                    response.correlationId(),
                    response.content(),
                    response.embeds(),
                    attachments,
                    response.finished(),
                    response.components()
            );
            String json = objectMapper.writeValueAsString(gatewayPayload);
            String key = response.correlationId() != null ? response.correlationId() : response.interactionToken();
            kafkaTemplate.send(topic, key, json);
        } catch (Exception e) {
            log.error("Failed to publish GatewayResponse [correlationId={}]: {}", response.correlationId(), e.getMessage());
            throw new IllegalStateException("Failed to publish Gateway response", e);
        }
    }
}
