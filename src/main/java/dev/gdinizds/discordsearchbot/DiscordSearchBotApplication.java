package dev.gdinizds.discordsearchbot;

import dev.gdinizds.discordsearchbot.config.NativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ImportRuntimeHints(NativeRuntimeHints.class)
public class DiscordSearchBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscordSearchBotApplication.class, args);
    }
}
