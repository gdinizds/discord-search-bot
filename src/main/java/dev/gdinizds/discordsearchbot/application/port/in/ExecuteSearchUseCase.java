package dev.gdinizds.discordsearchbot.application.port.in;

import dev.gdinizds.discordsearchbot.domain.model.SearchCommand;

public interface ExecuteSearchUseCase {
    void execute(SearchCommand command);
}
