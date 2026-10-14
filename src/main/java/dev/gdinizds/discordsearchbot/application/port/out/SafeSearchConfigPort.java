package dev.gdinizds.discordsearchbot.application.port.out;

import java.util.Optional;

public interface SafeSearchConfigPort {
    void save(String guildId, String channelId, int level);
    Optional<Integer> findLevel(String guildId, String channelId);
    int resolveScopeLimit(String guildId, String channelId);
}
