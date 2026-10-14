package dev.gdinizds.discordsearchbot.adapter.in.kafka;

import dev.gdinizds.discordsearchbot.domain.model.NavigationCommand;
import dev.gdinizds.discordsearchbot.domain.model.SearchCommand;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import dev.gdinizds.discordsearchbot.domain.service.CommandTextParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.List;

public class InboundEventMapper {

    private final CommandTextParser textParser;

    public InboundEventMapper(CommandTextParser textParser) {
        this.textParser = textParser;
    }

    public SearchCommand fromSlash(InboundEvent event, String rawJson) {
        JsonNode raw = raw(event);
        JsonNode args = raw.path("args");
        if (args.isMissingNode() || args.isNull()) {
            args = raw.path("parameters");
        }
        if (args.isMissingNode() || args.isNull()) {
            args = raw.path("options");
        }

        String termo = text(args.path("termo"));
        if (termo == null) {
            termo = text(args.path("query"));
        }
        if (termo == null) {
            termo = "";
        }

        String tipoRaw = text(args.path("tipo"));
        if (tipoRaw == null) {
            tipoRaw = text(args.path("type"));
        }

        String loc = text(args.path("localizacao"));
        if (loc == null) {
            loc = text(args.path("language"));
        }
        if (loc == null) {
            loc = text(args.path("lang"));
        }

        Integer safeSearch = null;
        JsonNode safeNode = args.path("safesearch");
        if (safeNode.isNumber()) {
            safeSearch = safeNode.intValue();
        } else if (!safeNode.isMissingNode()) {
            try {
                safeSearch = Integer.parseInt(safeNode.asString());
            } catch (NumberFormatException ignored) {}
        }

        return new SearchCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.username(),
                event.interactionToken(),
                event.messageId(),
                termo,
                SearchType.from(tipoRaw),
                loc,
                safeSearch,
                true,
                rawJson
        );
    }

    public SearchCommand fromDotCommand(InboundEvent event, String rawJson) {
        JsonNode raw = raw(event);
        String content = text(raw.path("content"));
        if (content == null) {
            List<String> args = new ArrayList<>();
            for (JsonNode arg : raw.path("args")) {
                args.add(arg.asString());
            }
            content = String.join(" ", args);
        }

        CommandTextParser.ParsedCommand parsed = textParser.parse(content);

        return new SearchCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.username(),
                event.interactionToken(),
                event.messageId(),
                parsed.query(),
                parsed.type(),
                parsed.language(),
                parsed.safeSearch(),
                false,
                rawJson
        );
    }

    public NavigationCommand fromButton(InboundEvent event, String rawJson) {
        JsonNode raw = raw(event);
        String componentId = text(raw.path("componentId"));
        if (componentId == null) {
            componentId = text(raw.path("custom_id"));
        }
        if (componentId == null) {
            componentId = "";
        }

        String action = "";
        String sessionId = "";

        String[] parts = componentId.split(":", 3);
        if (parts.length >= 3 && "search".equals(parts[0])) {
            action = parts[1];
            sessionId = parts[2];
        }

        return new NavigationCommand(
                event.correlationId(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.username(),
                event.interactionToken(),
                event.messageId(),
                action,
                sessionId,
                rawJson
        );
    }

    private static JsonNode raw(InboundEvent event) {
        return event.rawPayload() == null ? MissingNode.getInstance() : event.rawPayload();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String s = node.asString("").trim();
        return s.isEmpty() ? null : s;
    }
}
