package dev.gdinizds.discordsearchbot.application.port.out;

import dev.gdinizds.discordsearchbot.domain.model.ImageHit;
import dev.gdinizds.discordsearchbot.domain.model.SearchHit;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;

import java.util.List;

public interface SearchEnginePort {
    List<SearchHit> searchWeb(String query, String language, Integer safeSearch, int limit);
    List<ImageHit> searchImages(String query, String language, Integer safeSearch, int limit);
    List<SearchHit> searchFiles(String query, String language, Integer safeSearch, int limit);
    List<SearchHit> searchNews(String query, String language, Integer safeSearch, int limit);
}
