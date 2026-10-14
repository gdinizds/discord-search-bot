package dev.gdinizds.discordsearchbot.application.port.out;

import dev.gdinizds.discordsearchbot.domain.model.SearchSession;

import java.util.Optional;

public interface SearchSessionPort {
    void save(SearchSession session);
    Optional<SearchSession> findById(String sessionId);
    void updateIndex(String sessionId, int newIndex);
}
