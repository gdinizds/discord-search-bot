package dev.gdinizds.discordsearchbot.application.service;

import dev.gdinizds.discordsearchbot.application.port.in.ExecuteSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.in.NavigateSearchUseCase;
import dev.gdinizds.discordsearchbot.application.port.out.AuditLogPort;
import dev.gdinizds.discordsearchbot.application.port.out.ImageStoragePort;
import dev.gdinizds.discordsearchbot.application.port.out.ReplyPublisherPort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchEnginePort;
import dev.gdinizds.discordsearchbot.application.port.out.SearchSessionPort;
import dev.gdinizds.discordsearchbot.domain.model.AuditLogEntry;
import dev.gdinizds.discordsearchbot.domain.model.HitItem;
import dev.gdinizds.discordsearchbot.domain.model.ImageHit;
import dev.gdinizds.discordsearchbot.domain.model.NavigationCommand;
import dev.gdinizds.discordsearchbot.domain.model.OutboundResponse;
import dev.gdinizds.discordsearchbot.domain.model.SearchCommand;
import dev.gdinizds.discordsearchbot.domain.model.SearchFilter;
import dev.gdinizds.discordsearchbot.domain.model.SearchHit;
import dev.gdinizds.discordsearchbot.domain.model.SearchSession;
import dev.gdinizds.discordsearchbot.domain.model.SearchType;
import dev.gdinizds.discordsearchbot.domain.service.SearchLayoutBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SearchApplicationService implements ExecuteSearchUseCase, NavigateSearchUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchApplicationService.class);
    private static final long SESSION_TTL_SECONDS = 3600;

    private final SearchEnginePort searchEngine;
    private final ImageStoragePort imageStorage;
    private final SearchSessionPort sessionPort;
    private final ReplyPublisherPort replyPublisher;
    private final AuditLogPort auditLog;
    private final SearchLayoutBuilder layoutBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SearchApplicationService(SearchEnginePort searchEngine,
                                    ImageStoragePort imageStorage,
                                    SearchSessionPort sessionPort,
                                    ReplyPublisherPort replyPublisher,
                                    AuditLogPort auditLog,
                                    SearchLayoutBuilder layoutBuilder,
                                    ObjectMapper objectMapper,
                                    Clock clock) {
        this.searchEngine = searchEngine;
        this.imageStorage = imageStorage;
        this.sessionPort = sessionPort;
        this.replyPublisher = replyPublisher;
        this.auditLog = auditLog;
        this.layoutBuilder = layoutBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void execute(SearchCommand command) {
        long startTime = System.currentTimeMillis();
        String query = command.rawQuery() != null ? command.rawQuery().trim() : "";
        if (query.isBlank()) {
            handleEmptyQuery(command, startTime);
            return;
        }

        try {
            SearchFilter filter = SearchFilter.of(query, command.searchType(), command.language(), command.safeSearch(), 10);
            List<HitItem> items = fetchItems(command.guildId(), command.correlationId(), filter);

            Instant now = clock.instant();
            if (items.isEmpty()) {
                SearchLayoutBuilder.RenderedMessage rendered = layoutBuilder.renderEmpty(query, filter.type());
                OutboundResponse response = buildInitialResponse(command, rendered);
                replyPublisher.publish(response);
                recordAudit(command, response, "EMPTY", null, startTime);
                return;
            }

            String sessionId = UUID.randomUUID().toString();
            SearchSession session = SearchSession.create(
                    sessionId,
                    command.correlationId(),
                    command.userId(),
                    command.guildId(),
                    command.channelId(),
                    command.messageId(),
                    command.interactionToken(),
                    query,
                    filter.type(),
                    items,
                    now,
                    SESSION_TTL_SECONDS
            );
            sessionPort.save(session);

            SearchLayoutBuilder.RenderedMessage rendered = layoutBuilder.render(session, command.username());
            OutboundResponse response = buildInitialResponse(command, rendered);
            replyPublisher.publish(response);
            recordAudit(command, response, "SUCCESS", null, startTime);
        } catch (Exception e) {
            log.error("Search execution failed [query={}, type={}]: {}", query, command.searchType(), e.getMessage(), e);
            SearchLayoutBuilder.RenderedMessage rendered = layoutBuilder.renderError(e.getMessage());
            OutboundResponse response = buildInitialResponse(command, rendered);
            try {
                replyPublisher.publish(response);
            } catch (Exception pubErr) {
                log.warn("Failed to publish error response: {}", pubErr.getMessage());
            }
            recordAudit(command, response, "ERROR", e.getMessage(), startTime);
        }
    }

    @Override
    public void navigate(NavigationCommand command) {
        long startTime = System.currentTimeMillis();
        String sessionId = command.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Navigation command missing sessionId");
            return;
        }

        try {
            Optional<SearchSession> sessionOpt = sessionPort.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                OutboundResponse expired = OutboundResponse.ephemeralReply(
                        command.interactionToken(),
                        command.correlationId(),
                        "⚠️ Esta sessão de busca expirou. Faça uma nova pesquisa usando /pesquisar ou .pesquisar.",
                        true
                );
                replyPublisher.publish(expired);
                recordNavAudit(command, expired, "EXPIRED", "Session expired or not found", startTime);
                return;
            }

            SearchSession session = sessionOpt.get();
            int newIndex = switch (command.action()) {
                case "next" -> session.nextIndex();
                case "prev" -> session.previousIndex();
                case "rand" -> session.randomIndex();
                default -> session.currentIndex();
            };

            sessionPort.updateIndex(session.id(), newIndex);
            SearchSession updatedSession = session.withIndex(newIndex, clock.instant());

            if (updatedSession.searchType() == SearchType.IMAGE) {
                ensureImageUploaded(command.guildId(), command.correlationId(), updatedSession);
            }

            SearchLayoutBuilder.RenderedMessage rendered = layoutBuilder.render(updatedSession, command.username());
            OutboundResponse response = OutboundResponse.updateMessage(
                    command.interactionToken(),
                    command.channelId(),
                    command.messageId(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components()
            );

            replyPublisher.publish(response);
            recordNavAudit(command, response, "SUCCESS", null, startTime);
        } catch (Exception e) {
            log.error("Navigation failed for session {}: {}", sessionId, e.getMessage(), e);
            recordNavAudit(command, null, "ERROR", e.getMessage(), startTime);
        }
    }

    private List<HitItem> fetchItems(String guildId, String correlationId, SearchFilter filter) {
        return switch (filter.type()) {
            case IMAGE -> {
                List<ImageHit> hits = searchEngine.searchImages(filter.query(), filter.language(), filter.safeSearch(), filter.limit());
                List<HitItem> items = new ArrayList<>();
                for (int i = 0; i < hits.size(); i++) {
                    ImageHit hit = hits.get(i);
                    String s3Url = null;
                    if (i == 0) {
                        s3Url = imageStorage.storeImage(guildId, correlationId, hit.imageUrl(), hit.title());
                    }
                    items.add(HitItem.fromImageHit(hit, s3Url));
                }
                yield items;
            }
            case FILE -> {
                List<SearchHit> hits = searchEngine.searchFiles(filter.query(), filter.language(), filter.safeSearch(), filter.limit());
                yield hits.stream().map(h -> HitItem.fromSearchHit(h, SearchType.FILE)).toList();
            }
            case NEWS -> {
                List<SearchHit> hits = searchEngine.searchNews(filter.query(), filter.language(), filter.safeSearch(), filter.limit());
                yield hits.stream().map(h -> HitItem.fromSearchHit(h, SearchType.NEWS)).toList();
            }
            default -> {
                List<SearchHit> hits = searchEngine.searchWeb(filter.query(), filter.language(), filter.safeSearch(), filter.limit());
                yield hits.stream().map(h -> HitItem.fromSearchHit(h, SearchType.WEB)).toList();
            }
        };
    }

    private void ensureImageUploaded(String guildId, String correlationId, SearchSession session) {
        HitItem item = session.currentItem();
        if (item != null && (item.mediaUrl() == null || item.mediaUrl().equals(item.imageUrl()))) {
            String uploaded = imageStorage.storeImage(guildId, correlationId, item.imageUrl(), item.title());
            if (uploaded != null && !uploaded.equals(item.imageUrl())) {
                HitItem updatedItem = new HitItem(
                        item.title(),
                        item.url(),
                        item.snippet(),
                        item.imageUrl(),
                        uploaded,
                        item.engine(),
                        item.resolution(),
                        item.type()
                );
                List<HitItem> mutableItems = new ArrayList<>(session.items());
                mutableItems.set(session.currentIndex(), updatedItem);
                SearchSession enriched = new SearchSession(
                        session.id(),
                        session.correlationId(),
                        session.userId(),
                        session.guildId(),
                        session.channelId(),
                        session.messageId(),
                        session.interactionToken(),
                        session.query(),
                        session.searchType(),
                        session.currentIndex(),
                        session.totalResults(),
                        mutableItems,
                        session.createdAt(),
                        clock.instant(),
                        session.expiresAt()
                );
                sessionPort.save(enriched);
            }
        }
    }

    private OutboundResponse buildInitialResponse(SearchCommand command, SearchLayoutBuilder.RenderedMessage rendered) {
        if (command.isSlash() && command.interactionToken() != null) {
            return OutboundResponse.hookReply(
                    command.interactionToken(),
                    command.correlationId(),
                    rendered.content(),
                    rendered.embeds(),
                    rendered.components(),
                    true
            );
        }
        return OutboundResponse.channelReply(
                command.channelId(),
                command.messageId(),
                command.correlationId(),
                rendered.content(),
                rendered.embeds(),
                rendered.components()
        );
    }

    private void handleEmptyQuery(SearchCommand command, long startTime) {
        SearchLayoutBuilder.RenderedMessage rendered = layoutBuilder.renderError("Por favor, forneça um termo para pesquisar. Exemplo: `/pesquisar termo: docker tipo: web` ou `.pesquisar docker`.");
        OutboundResponse response = buildInitialResponse(command, rendered);
        replyPublisher.publish(response);
        recordAudit(command, response, "INVALID_INPUT", "Query is empty", startTime);
    }

    private void recordAudit(SearchCommand command, OutboundResponse response, String status, String errorMessage, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        String respJson = toJson(response);
        AuditLogEntry entry = AuditLogEntry.create(
                command.correlationId(),
                command.guildId(),
                command.channelId(),
                command.userId(),
                command.username(),
                command.isSlash() ? "INTERACTION_COMMAND" : "MESSAGE_COMMAND",
                "pesquisar",
                command.rawQuery(),
                command.searchType() != null ? command.searchType().name() : null,
                command.rawPayloadJson() != null ? command.rawPayloadJson() : "{}",
                respJson,
                status,
                errorMessage,
                elapsed
        );
        auditLog.record(entry);
    }

    private void recordNavAudit(NavigationCommand command, OutboundResponse response, String status, String errorMessage, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        String respJson = toJson(response);
        AuditLogEntry entry = AuditLogEntry.create(
                command.correlationId(),
                command.guildId(),
                command.channelId(),
                command.userId(),
                command.username(),
                "INTERACTION_BUTTON",
                "pesquisar:" + command.action(),
                command.sessionId(),
                null,
                command.rawPayloadJson() != null ? command.rawPayloadJson() : "{}",
                respJson,
                status,
                errorMessage,
                elapsed
        );
        auditLog.record(entry);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
