package dev.gdinizds.discordsearchbot.application.port.out;

import dev.gdinizds.discordsearchbot.domain.model.OutboundResponse;

public interface ReplyPublisherPort {
    void publish(OutboundResponse response);
}
