package dev.gdinizds.discordsearchbot.domain.model;

import java.util.List;
import java.util.Map;

public record OutboundResponse(
        String responseType,
        String interactionToken,
        String messageId,
        String channelId,
        String correlationId,
        String content,
        List<Map<String, Object>> embeds,
        List<Map<String, Object>> components,
        Boolean finished
) {
    public static OutboundResponse hookReply(String interactionToken, String correlationId, String content,
                                             List<Map<String, Object>> embeds, List<Map<String, Object>> components, boolean finished) {
        return new OutboundResponse("REPLY", interactionToken, null, null, correlationId, content, embeds, components, finished);
    }

    public static OutboundResponse updateMessage(String interactionToken, String channelId, String originMessageId, String correlationId,
                                                 String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return new OutboundResponse("UPDATE_MESSAGE", interactionToken, originMessageId, channelId, correlationId, content, embeds, components, null);
    }

    public static OutboundResponse channelReply(String channelId, String originMessageId, String correlationId,
                                                String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return new OutboundResponse("REPLY", null, originMessageId, channelId, correlationId, content, embeds, components, null);
    }

    public static OutboundResponse ephemeralReply(String interactionToken, String correlationId, String content, boolean finished) {
        return new OutboundResponse("EPHEMERAL_REPLY", interactionToken, null, null, correlationId, content, null, null, finished);
    }
}
