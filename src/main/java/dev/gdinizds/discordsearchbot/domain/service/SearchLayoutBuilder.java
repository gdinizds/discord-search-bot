package dev.gdinizds.discordsearchbot.domain.service;

import dev.gdinizds.discordsearchbot.domain.model.HitItem;
import dev.gdinizds.discordsearchbot.domain.model.OutboundResponse;
import dev.gdinizds.discordsearchbot.domain.model.SearchSession;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchLayoutBuilder {

    private static final int MAX_TITLE_LENGTH = 250;
    private static final int MAX_DESC_LENGTH = 2000;

    public record RenderedMessage(
            String content,
            List<Map<String, Object>> embeds,
            List<Map<String, Object>> components,
            List<OutboundResponse.Attachment> attachments
    ) {
    }

    public RenderedMessage render(SearchSession session, String requesterUsername) {
        HitItem item = session.currentItem();
        if (item == null) {
            return renderEmpty(session.query(), session.searchType());
        }

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", formatTitle(item.title()));
        embed.put("url", item.url());
        embed.put("color", colorForType(session.searchType()));
        embed.put("description", formatDescription(item));

        List<OutboundResponse.Attachment> attachments = new ArrayList<>();
        if (session.searchType() == SearchType.IMAGE) {
            String targetUrl = (item.mediaUrl() != null && !item.mediaUrl().isBlank())
                    ? item.mediaUrl()
                    : item.imageUrl();
            if (targetUrl != null && !targetUrl.isBlank()) {
                if (isInternalAttachment(targetUrl)) {
                    String filename = extractFilename(targetUrl);
                    attachments.add(new OutboundResponse.Attachment(targetUrl, filename, item.title()));
                    embed.put("image", Map.of("url", "attachment://" + filename));
                } else {
                    embed.put("image", Map.of("url", targetUrl));
                }
            }
        } else if (item.imageUrl() != null && !item.imageUrl().isBlank()) {
            embed.put("thumbnail", Map.of("url", item.imageUrl()));
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of(
                "name", "📑 Categoria",
                "value", categoryLabel(session.searchType()),
                "inline", true
        ));
        if (item.engine() != null && !item.engine().isBlank()) {
            fields.add(Map.of(
                "name", "🔍 Motor",
                "value", item.engine().toUpperCase(),
                "inline", true
            ));
        }
        fields.add(Map.of(
                "name", "📊 Posição",
                "value", (session.currentIndex() + 1) + " de " + session.totalResults(),
                "inline", true
        ));
        embed.put("fields", fields);

        String footerText = "SearXNG • " + (requesterUsername != null ? "Solicitado por @" + requesterUsername : "Termo: " + session.query());
        embed.put("footer", Map.of("text", footerText));

        List<Map<String, Object>> components = buildComponents(session, item);

        return new RenderedMessage(null, List.of(embed), components, attachments);
    }

    public RenderedMessage renderEmpty(String query, SearchType type) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "🔍 Nenhum resultado encontrado");
        embed.put("description", "Não foram encontrados resultados para o termo **" + query + "** na categoria **" + categoryLabel(type) + "**.");
        embed.put("color", 0xED4245);
        return new RenderedMessage(null, List.of(embed), List.of(), List.of());
    }

    public RenderedMessage renderError(String errorMessage) {
        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "⚠️ Falha ao executar pesquisa");
        embed.put("description", errorMessage != null && !errorMessage.isBlank() ? errorMessage : "Ocorreu um erro ao consultar o motor de busca.");
        embed.put("color", 0xED4245);
        return new RenderedMessage(null, List.of(embed), List.of(), List.of());
    }

    static boolean isInternalAttachment(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains(".cluster.local")
                || lower.contains("garage")
                || lower.contains("localhost")
                || lower.contains("127.0.0.1")
                || lower.contains("/discord-gateway-attachments/");
    }

    static String extractFilename(String url) {
        if (url == null || url.isBlank()) return "search-image.jpg";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int q = name.indexOf('?');
            if (q > 0) name = name.substring(0, q);
            if (name.contains(".")) return name;
        }
        return "search-image.jpg";
    }

    private List<Map<String, Object>> buildComponents(SearchSession session, HitItem currentItem) {
        List<Map<String, Object>> buttons = new ArrayList<>();

        buttons.add(Map.of(
                "type", 2,
                "style", "SECONDARY",
                "custom_id", "search:prev:" + session.id(),
                "label", "◀ Anterior",
                "disabled", !session.hasPrevious()
        ));

        buttons.add(Map.of(
                "type", 2,
                "style", "SECONDARY",
                "custom_id", "search:rand:" + session.id(),
                "label", "🎲 Aleatório",
                "disabled", session.totalResults() <= 1
        ));

        buttons.add(Map.of(
                "type", 2,
                "style", "PRIMARY",
                "custom_id", "search:next:" + session.id(),
                "label", "Próximo ▶",
                "disabled", !session.hasNext()
        ));

        if (currentItem.url() != null && currentItem.url().startsWith("http")) {
            buttons.add(Map.of(
                    "type", 2,
                    "style", "LINK",
                    "url", currentItem.url(),
                    "label", "🌐 Acessar"
            ));
        }

        return List.of(Map.of(
                "type", 1,
                "components", buttons
        ));
    }

    private String formatTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Sem título";
        }
        String clean = raw.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_TITLE_LENGTH ? clean.substring(0, MAX_TITLE_LENGTH - 1) + "…" : clean;
    }

    private String formatDescription(HitItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.snippet() != null && !item.snippet().isBlank()) {
            String snippet = item.snippet().replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
            if (snippet.length() > MAX_DESC_LENGTH - 100) {
                snippet = snippet.substring(0, MAX_DESC_LENGTH - 100) + "…";
            }
            sb.append(snippet).append("\n\n");
        }
        if (item.url() != null && !item.url().isBlank()) {
            sb.append("🔗 **Link:** ").append(item.url());
        }
        return sb.toString();
    }

    private int colorForType(SearchType type) {
        return switch (type) {
            case WEB -> 0x5865F2;
            case IMAGE -> 0xE67E22;
            case FILE -> 0x9B59B6;
            case NEWS -> 0x3498DB;
        };
    }

    private String categoryLabel(SearchType type) {
        return switch (type) {
            case WEB -> "Web";
            case IMAGE -> "Imagem";
            case FILE -> "Arquivo";
            case NEWS -> "Notícia";
        };
    }
}
