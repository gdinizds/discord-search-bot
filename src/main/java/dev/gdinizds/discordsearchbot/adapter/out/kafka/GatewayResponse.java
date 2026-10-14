package dev.gdinizds.discordsearchbot.adapter.out.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GatewayResponse(
        String responseType,
        String interactionToken,
        String messageId,
        String channelId,
        String correlationId,
        String content,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> embeds,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Attachment> attachments,
        Boolean finished,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Map<String, Object>> components
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Attachment(String url, String name, String description) {}

    public static GatewayResponse deferredReply(String interactionToken, String correlationId, String content, boolean finished) {
        return new GatewayResponse("DEFERRED_REPLY", interactionToken, null, null, correlationId, content, null, null, finished, null);
    }

    public static GatewayResponse hookReply(String interactionToken, String correlationId, String content,
                                            List<Map<String, Object>> embeds, List<Map<String, Object>> components, boolean finished) {
        return new GatewayResponse("REPLY", interactionToken, null, null, correlationId, content, embeds, null, finished, components);
    }

    public static GatewayResponse deferredUpdate(String interactionToken, String correlationId, String content,
                                                 List<Map<String, Object>> embeds, List<Map<String, Object>> components, boolean finished) {
        return new GatewayResponse("DEFERRED_REPLY", interactionToken, null, null, correlationId, content, embeds, null, finished, components);
    }

    public static GatewayResponse updateMessage(String interactionToken, String channelId, String originMessageId, String correlationId,
                                                String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return new GatewayResponse("UPDATE_MESSAGE", interactionToken, originMessageId, channelId, correlationId, content, embeds, null, true, components);
    }

    public static GatewayResponse channelReply(String channelId, String originMessageId, String correlationId,
                                               String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return new GatewayResponse("REPLY", null, originMessageId, channelId, correlationId, content, embeds, null, null, components);
    }

    public static GatewayResponse ephemeralReply(String interactionToken, String correlationId, String content, boolean finished) {
        return new GatewayResponse("EPHEMERAL_REPLY", interactionToken, null, null, correlationId, content, null, null, finished, null);
    }
}
