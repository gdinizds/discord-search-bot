package dev.gdinizds.discordsearchbot.application.port.in;

import dev.gdinizds.discordsearchbot.domain.model.NavigationCommand;

public interface NavigateSearchUseCase {
    void navigate(NavigationCommand command);
}
