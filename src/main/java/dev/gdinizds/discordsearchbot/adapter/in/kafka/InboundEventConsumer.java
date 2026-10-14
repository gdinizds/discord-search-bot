package dev.gdinizds.discordsearchbot.adapter.in.kafka;

import dev.gdinizds.discordsearchbot.application.port.in.ExecuteSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.in.NavigateSearchUseCase;
import dev.gdinizds.discordsearchbot.domain.model.NavigationCommand;
import dev.gdinizds.discordsearchbot.domain.model.SearchCommand;
import io.github.resilience4j.bulkhead.Bulkhead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InboundEventConsumer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InboundEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final InboundEventMapper mapper;
    private final ExecuteSearchUseCase searchUseCase;
    private final NavigateSearchUseCase navigateUseCase;
    private final Bulkhead bulkhead;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object drained = new Object();
    private Duration drainTimeout = Duration.ofSeconds(30);

    public InboundEventConsumer(ObjectMapper objectMapper,
                                InboundEventMapper mapper,
                                ExecuteSearchUseCase searchUseCase,
                                NavigateSearchUseCase navigateUseCase,
                                Bulkhead bulkhead,
                                @Qualifier("searchExecutor") ExecutorService executor) {
        this.objectMapper = objectMapper;
        this.mapper = mapper;
        this.searchUseCase = searchUseCase;
        this.navigateUseCase = navigateUseCase;
        this.bulkhead = bulkhead;
        this.executor = executor;
    }

    public void setDrainTimeout(Duration drainTimeout) {
        this.drainTimeout = drainTimeout;
    }

    @KafkaListener(topics = "${search-bot.topics.inbound-interactions}", containerFactory = "kafkaListenerContainerFactory")
    public void onInteractionCommand(@Payload String payload,
                                     @Header(name = "command-name", required = false) String commandName) {
        if (!"pesquisar".equals(commandName)) return;
        parse(payload).ifPresent(event -> {
            SearchCommand command = mapper.fromSlash(event, payload);
            dispatch(() -> withMdc(command.correlationId(), command.guildId(), "SLASH", () -> searchUseCase.execute(command)));
        });
    }

    @KafkaListener(topics = "${search-bot.topics.inbound-commands}", containerFactory = "kafkaListenerContainerFactory")
    public void onMessageCommand(@Payload String payload,
                                 @Header(name = "command-name", required = false) String commandName) {
        if (!"pesquisar".equals(commandName)) return;
        parse(payload).ifPresent(event -> {
            SearchCommand command = mapper.fromDotCommand(event, payload);
            dispatch(() -> withMdc(command.correlationId(), command.guildId(), "DOT", () -> searchUseCase.execute(command)));
        });
    }

    @KafkaListener(topics = "${search-bot.topics.inbound-buttons}", containerFactory = "kafkaListenerContainerFactory")
    public void onButtonInteraction(@Payload String payload) {
        parse(payload).ifPresent(event -> {
            if (event.rawPayload() == null) return;
            String compId = event.rawPayload().path("componentId").asString("");
            if (compId.isBlank()) compId = event.rawPayload().path("custom_id").asString("");
            if (!compId.startsWith("search:")) return;

            NavigationCommand command = mapper.fromButton(event, payload);
            dispatch(() -> withMdc(command.correlationId(), command.guildId(), "BUTTON", () -> navigateUseCase.navigate(command)));
        });
    }

    private void dispatch(Runnable task) {
        if (!bulkhead.tryAcquirePermission()) {
            log.warn("Bulkhead rejected search request due to high load");
            return;
        }
        submit(() -> {
            try {
                task.run();
            } finally {
                bulkhead.onComplete();
            }
        });
    }

    private void submit(Runnable task) {
        inFlight.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    release();
                }
            });
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    private void release() {
        if (inFlight.decrementAndGet() == 0) {
            synchronized (drained) {
                drained.notifyAll();
            }
        }
    }

    private Optional<InboundEvent> parse(String payload) {
        try {
            return Optional.of(objectMapper.readValue(payload, InboundEvent.class));
        } catch (Exception e) {
            log.warn("Discarding malformed inbound event: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static void withMdc(String correlationId, String guildId, String trigger, Runnable task) {
        if (correlationId != null) MDC.put("correlation_id", correlationId);
        if (guildId != null) MDC.put("guild_id", guildId);
        MDC.put("trigger", trigger);
        try {
            task.run();
        } catch (Exception e) {
            log.error("Unhandled failure during task execution", e);
        } finally {
            MDC.clear();
        }
    }

    @Override
    public void destroy() {
        long deadline = System.nanoTime() + drainTimeout.toNanos();
        synchronized (drained) {
            while (inFlight.get() > 0) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMs <= 0) {
                    log.warn("Shutting down with {} request(s) still in flight", inFlight.get());
                    return;
                }
                log.info("Waiting for {} request(s) to finish before shutdown", inFlight.get());
                try {
                    drained.wait(remainingMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
