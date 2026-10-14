package dev.gdinizds.discordsearchbot.domain.service;

import dev.gdinizds.discordsearchbot.domain.model.SearchType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandTextParser {

    private static final Pattern TYPE_FLAG_PATTERN = Pattern.compile("(?:--tipo=|-t\\s+)(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_FLAG_PATTERN = Pattern.compile("(?:--lang=|-l\\s+|--loc=)(\\S+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAFE_FLAG_PATTERN = Pattern.compile("(?:--safe=|-s\\s+)(\\d+)", Pattern.CASE_INSENSITIVE);

    public record ParsedCommand(
            String query,
            SearchType type,
            String language,
            Integer safeSearch
    ) {
    }

    public ParsedCommand parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ParsedCommand("", SearchType.WEB, null, null);
        }

        String text = rawText.trim();
        if (text.startsWith(".pesquisar")) {
            text = text.substring(".pesquisar".length()).trim();
        } else if (text.startsWith("/pesquisar")) {
            text = text.substring("/pesquisar".length()).trim();
        }

        SearchType type = null;
        String language = null;
        Integer safeSearch = null;

        Matcher typeMatcher = TYPE_FLAG_PATTERN.matcher(text);
        if (typeMatcher.find()) {
            type = SearchType.from(typeMatcher.group(1));
            text = typeMatcher.replaceFirst("").trim();
        }

        Matcher langMatcher = LANG_FLAG_PATTERN.matcher(text);
        if (langMatcher.find()) {
            language = langMatcher.group(1);
            text = langMatcher.replaceFirst("").trim();
        }

        Matcher safeMatcher = SAFE_FLAG_PATTERN.matcher(text);
        if (safeMatcher.find()) {
            try {
                safeSearch = Integer.parseInt(safeMatcher.group(1));
            } catch (NumberFormatException ignored) {
            }
            text = safeMatcher.replaceFirst("").trim();
        }

        if (type == null) {
            String[] tokens = text.split("\\s+", 2);
            if (tokens.length > 0) {
                String firstToken = tokens[0].toLowerCase();
                if (isTypeKeyword(firstToken)) {
                    type = SearchType.from(firstToken);
                    text = tokens.length > 1 ? tokens[1].trim() : "";
                }
            }
        }

        return new ParsedCommand(
                text,
                type == null ? SearchType.WEB : type,
                language,
                safeSearch
        );
    }

    private static boolean isTypeKeyword(String token) {
        return switch (token) {
            case "imagem", "image", "imagens", "img",
                 "arquivo", "file", "arquivos", "files", "doc", "documento",
                 "noticia", "noticias", "news",
                 "site", "web" -> true;
            default -> false;
        };
    }
}
