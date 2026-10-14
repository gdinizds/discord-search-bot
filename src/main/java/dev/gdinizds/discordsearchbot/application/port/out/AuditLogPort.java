package dev.gdinizds.discordsearchbot.application.port.out;

import dev.gdinizds.discordsearchbot.domain.model.AuditLogEntry;

public interface AuditLogPort {
    void record(AuditLogEntry entry);
}
