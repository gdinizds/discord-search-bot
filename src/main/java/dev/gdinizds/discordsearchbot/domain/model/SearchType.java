package dev.gdinizds.discordsearchbot.domain.model;

import java.util.Locale;

public enum SearchType {
    WEB("web", "general"),
    IMAGE("imagem", "images"),
    FILE("arquivo", "files"),
    NEWS("noticia", "news");

    private final String alias;
    private final String searxCategory;

    SearchType(String alias, String searxCategory) {
        this.alias = alias;
        this.searxCategory = searxCategory;
    }

    public String alias() {
        return alias;
    }

    public String searxCategory() {
        return searxCategory;
    }

    public static SearchType from(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "imagem", "image", "imagens", "img", "images" -> IMAGE;
            case "arquivo", "file", "arquivos", "files", "doc", "docs", "documento", "documentos" -> FILE;
            case "noticia", "noticias", "news" -> NEWS;
            default -> WEB;
        };
    }
}
