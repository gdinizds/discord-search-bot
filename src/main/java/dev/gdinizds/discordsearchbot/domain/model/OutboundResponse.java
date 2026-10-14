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
        List<Attachment> attachments,
        Boolean finished
) {
    public record Attachment(String url, String name, String description) {}

    public static OutboundResponse deferredReply(String interactionToken, String correlationId, String content,
                                                 List<Map<String, Object>> embeds, List<Map<String, Object>> components,
                                                 List<Attachment> attachments, boolean finished) {
        return new OutboundResponse("DEFERRED_REPLY", interactionToken, null, null, correlationId, content, embeds, components, attachments, finished);
    }

    public static OutboundResponse hookReply(String interactionToken, String correlationId, String content,
                                             List<Map<String, Object>> embeds, List<Map<String, Object>> components,
                                             List<Attachment> attachments, boolean finished) {
        return new OutboundResponse("DEFERRED_REPLY", interactionToken, null, null, correlationId, content, embeds, components, attachments, finished);
    }

    public static OutboundResponse hookReply(String interactionToken, String correlationId, String content,
                                             List<Map<String, Object>> embeds, List<Map<String, Object>> components, boolean finished) {
        return hookReply(interactionToken, correlationId, content, embeds, components, null, finished);
    }

    public static OutboundResponse updateMessage(String interactionToken, String channelId, String originMessageId, String correlationId,
                                                 String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components,
                                                 List<Attachment> attachments) {
        return new OutboundResponse("UPDATE_MESSAGE", interactionToken, originMessageId, channelId, correlationId, content, embeds, components, attachments, true);
    }

    public static OutboundResponse updateMessage(String interactionToken, String channelId, String originMessageId, String correlationId,
                                                 String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return updateMessage(interactionToken, channelId, originMessageId, correlationId, content, embeds, components, null);
    }

    public static OutboundResponse channelReply(String channelId, String originMessageId, String correlationId,
                                                String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components,
                                                List<Attachment> attachments) {
        return new OutboundResponse("REPLY", null, originMessageId, channelId, correlationId, content, embeds, components, attachments, null);
    }

    public static OutboundResponse channelReply(String channelId, String originMessageId, String correlationId,
                                                String content, List<Map<String, Object>> embeds, List<Map<String, Object>> components) {
        return channelReply(channelId, originMessageId, correlationId, content, embeds, components, null);
    }

    public static OutboundResponse ephemeralReply(String interactionToken, String correlationId, String content, boolean finished) {
        return new OutboundResponse("EPHEMERAL_REPLY", interactionToken, null, null, correlationId, content, null, null, null, finished);
    }
}
