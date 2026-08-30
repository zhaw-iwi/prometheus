package ch.zhaw.prometheus.definition.preview;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.runtime.AgentInstanceSnapshot;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeInstance;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeResult;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimeStorageChange;

/** Bounded, expiring, persistence-free runtime sessions for Valerian Designer. */
@Service
public class DesignerPreviewService {
    private static final long FIRST_SYNTHETIC_REVISION_ID = 1_000_000_000L;
    private static final PreviewDiagnostic EXECUTION_FAILED = new PreviewDiagnostic(
            "PREVIEW_EXECUTION_FAILED", "Preview execution failed at a trusted component boundary",
            "Review the definition and component/provider availability, then retry");

    private final AgentDefinitionJson definitionJson;
    private final DefinitionCompiler compiler;
    private final AgentRuntimeEngine engine;
    private final RuntimeModelGateway modelGateway;
    private final Clock clock;
    private final Supplier<RandomGenerator> randoms;
    private final Duration ttl;
    private final int maxSessions;
    private final int maxOperations;
    private final int maxEventPayloadChars;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final AtomicLong syntheticRevisionIds = new AtomicLong(FIRST_SYNTHETIC_REVISION_ID);

    @Autowired
    public DesignerPreviewService(AgentDefinitionJson definitionJson, DefinitionCompiler compiler,
            AgentRuntimeEngine engine, RuntimeModelGateway modelGateway,
            @Value("${prometheus.designer.preview.ttl:PT15M}") Duration ttl,
            @Value("${prometheus.designer.preview.max-sessions:32}") int maxSessions,
            @Value("${prometheus.designer.preview.max-operations:256}") int maxOperations,
            @Value("${prometheus.designer.preview.max-event-payload-chars:65536}") int maxEventPayloadChars) {
        this(definitionJson, compiler, engine, modelGateway, Clock.systemUTC(), RandomGenerator::getDefault,
                ttl, maxSessions, maxOperations, maxEventPayloadChars);
    }

    DesignerPreviewService(AgentDefinitionJson definitionJson, DefinitionCompiler compiler,
            AgentRuntimeEngine engine, RuntimeModelGateway modelGateway, Clock clock,
            Supplier<RandomGenerator> randoms, Duration ttl, int maxSessions, int maxOperations,
            int maxEventPayloadChars) {
        if (definitionJson == null || compiler == null || engine == null || modelGateway == null || clock == null
                || randoms == null || ttl == null || ttl.isZero() || ttl.isNegative() || maxSessions < 1
                || maxOperations < 2 || maxEventPayloadChars < 1) {
            throw new IllegalArgumentException("Invalid designer preview configuration");
        }
        this.definitionJson = definitionJson;
        this.compiler = compiler;
        this.engine = engine;
        this.modelGateway = modelGateway;
        this.clock = clock;
        this.randoms = randoms;
        this.ttl = ttl;
        this.maxSessions = maxSessions;
        this.maxOperations = maxOperations;
        this.maxEventPayloadChars = maxEventPayloadChars;
    }

    public PreviewSnapshot create(String json, PreviewSource source, Long storedRevisionId) {
        if (source == null || source == PreviewSource.SAVED && (storedRevisionId == null || storedRevisionId < 1)
                || source == PreviewSource.UNSAVED && storedRevisionId != null) {
            throw new IllegalArgumentException("Preview source does not match its stored revision identity");
        }
        cleanupExpired();
        if (this.sessions.size() >= this.maxSessions) {
            throw new PreviewLimitException("The preview session limit has been reached");
        }
        CompiledAgentDefinition compiled = this.compiler.compile(this.definitionJson.parse(json));
        long runtimeRevisionId = storedRevisionId == null ? this.syntheticRevisionIds.getAndIncrement()
                : storedRevisionId;
        AgentRuntimeContext context = new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(this.modelGateway),
                this.randoms.get());
        var creation = createRuntime(runtimeRevisionId, compiled, context);
        Instant now = this.clock.instant();
        UUID id = UUID.randomUUID();
        Session session = new Session(id, source, storedRevisionId, compiled, context, creation.instance(), now,
                now.plus(this.ttl));
        session.operations.add(operation(1, "CREATE", now, null, creation.startup(), List.of()));
        synchronized (this.sessions) {
            cleanupExpired();
            if (this.sessions.size() >= this.maxSessions) {
                throw new PreviewLimitException("The preview session limit has been reached");
            }
            this.sessions.put(id, session);
        }
        return snapshot(session);
    }

    public PreviewSnapshot inspect(UUID id) {
        Session session = require(id);
        synchronized (session) {
            requireCurrent(session);
            touch(session);
            return snapshot(session);
        }
    }

    public PreviewSnapshot acknowledge(UUID id, RuntimeEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("A preview event is required");
        }
        if (event.payload() != null && event.payload().length() > this.maxEventPayloadChars) {
            throw new PreviewLimitException("The preview event payload is too large");
        }
        return execute(id, "EVENT", event, session -> this.engine.acknowledge(session.runtime, event,
                session.context));
    }

    public PreviewSnapshot generate(UUID id) {
        return execute(id, "GENERATE", null,
                session -> this.engine.generate(session.runtime, session.context));
    }

    public PreviewSnapshot reset(UUID id) {
        return execute(id, "RESET", null,
                session -> this.engine.reset(session.runtime, session.context));
    }

    public void close(UUID id) {
        Session session = require(id);
        synchronized (session) {
            requireCurrent(session);
            this.sessions.remove(id, session);
        }
    }

    @Scheduled(fixedDelayString = "${prometheus.designer.preview.cleanup-delay-ms:60000}")
    public void cleanupExpired() {
        Instant now = this.clock.instant();
        this.sessions.forEach((id, session) -> {
            synchronized (session) {
                if (!now.isBefore(session.expiresAt)) {
                    this.sessions.remove(id, session);
                }
            }
        });
    }

    int sessionCount() {
        cleanupExpired();
        return this.sessions.size();
    }

    private PreviewSnapshot execute(UUID id, String kind, RuntimeEvent input, RuntimeOperation operation) {
        Session session = require(id);
        synchronized (session) {
            requireCurrent(session);
            if (session.operations.size() >= this.maxOperations) {
                throw new PreviewLimitException("The preview operation limit has been reached");
            }
            AgentInstanceSnapshot before = session.runtime.snapshot();
            try {
                AgentRuntimeResult result = operation.execute(session);
                Instant now = this.clock.instant();
                session.operations.add(operation(session.operations.size() + 1, kind, now, input, result, List.of()));
                touch(session, now);
            } catch (RuntimeException failure) {
                session.runtime = this.engine.restore(before.definitionRevisionId(), session.definition,
                        before.activeLeafStateId(), session.runtime.initialStorageSnapshot(), before.storage(),
                        before.history(), before.started());
                Instant now = this.clock.instant();
                session.diagnostics.add(EXECUTION_FAILED);
                session.operations.add(new PreviewOperation(session.operations.size() + 1, kind, now, input,
                        before.activeStatePath(), Map.of(), List.of(), null, List.of(EXECUTION_FAILED)));
                touch(session, now);
            }
            return snapshot(session);
        }
    }

    private ch.zhaw.prometheus.definition.runtime.AgentRuntimeCreation createRuntime(long revisionId,
            CompiledAgentDefinition definition, AgentRuntimeContext context) {
        try {
            return this.engine.create(revisionId, definition, context);
        } catch (RuntimeException failure) {
            throw new PreviewExecutionException(failure);
        }
    }

    private Session require(UUID id) {
        if (id == null) {
            throw new PreviewNotFoundException();
        }
        cleanupExpired();
        Session session = this.sessions.get(id);
        if (session == null) {
            throw new PreviewNotFoundException();
        }
        return session;
    }

    private void requireCurrent(Session session) {
        if (this.sessions.get(session.id) != session || !this.clock.instant().isBefore(session.expiresAt)) {
            this.sessions.remove(session.id, session);
            throw new PreviewNotFoundException();
        }
    }

    private void touch(Session session) {
        touch(session, this.clock.instant());
    }

    private void touch(Session session, Instant now) {
        session.lastAccessedAt = now;
        session.expiresAt = now.plus(this.ttl);
    }

    private static PreviewOperation operation(long sequence, String kind, Instant at, RuntimeEvent input,
            AgentRuntimeResult result, List<PreviewDiagnostic> diagnostics) {
        return new PreviewOperation(sequence, kind, at, input, result.after().activeStatePath(),
                storageChanges(result.storageChanges()), result.acceptedTransitionIds(), behaviour(result.behaviour()),
                diagnostics);
    }

    private static PreviewSnapshot snapshot(Session session) {
        AgentInstanceSnapshot runtime = session.runtime.snapshot();
        return new PreviewSnapshot(session.id, session.source, session.storedRevisionId, runtime.definitionKey(),
                runtime.definitionRevision(), session.createdAt, session.lastAccessedAt, session.expiresAt,
                runtime.activeStatePath(), storage(runtime.storage()), runtime.history(), runtime.started(),
                runtime.active(), List.copyOf(session.operations), List.copyOf(session.diagnostics));
    }

    private static Map<String, JsonNode> storage(Map<String, ImmutableJson> values) {
        Map<String, JsonNode> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, value.value()));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, PreviewStorageChange> storageChanges(Map<String, RuntimeStorageChange> changes) {
        Map<String, PreviewStorageChange> copy = new LinkedHashMap<>();
        changes.forEach((key, change) -> copy.put(key, new PreviewStorageChange(
                change.before() == null ? null : change.before().value(),
                change.after() == null ? null : change.after().value())));
        return Collections.unmodifiableMap(copy);
    }

    private static PreviewBehaviour behaviour(RuntimeBehaviour behaviour) {
        if (behaviour == null) {
            return null;
        }
        return new PreviewBehaviour(behaviour.speech(), value(behaviour.nonVerbal()), value(behaviour.motion()),
                value(behaviour.display()));
    }

    private static JsonNode value(ImmutableJson value) {
        return value == null ? null : value.value();
    }

    @FunctionalInterface
    private interface RuntimeOperation {
        AgentRuntimeResult execute(Session session);
    }

    private static final class Session {
        private final UUID id;
        private final PreviewSource source;
        private final Long storedRevisionId;
        private final CompiledAgentDefinition definition;
        private final AgentRuntimeContext context;
        private AgentRuntimeInstance runtime;
        private final Instant createdAt;
        private Instant lastAccessedAt;
        private Instant expiresAt;
        private final List<PreviewOperation> operations = new ArrayList<>();
        private final List<PreviewDiagnostic> diagnostics = new ArrayList<>();

        private Session(UUID id, PreviewSource source, Long storedRevisionId, CompiledAgentDefinition definition,
                AgentRuntimeContext context, AgentRuntimeInstance runtime, Instant createdAt, Instant expiresAt) {
            this.id = id;
            this.source = source;
            this.storedRevisionId = storedRevisionId;
            this.definition = definition;
            this.context = context;
            this.runtime = runtime;
            this.createdAt = createdAt;
            this.lastAccessedAt = createdAt;
            this.expiresAt = expiresAt;
        }
    }

    public enum PreviewSource {
        UNSAVED,
        SAVED
    }

    public record PreviewSnapshot(UUID id, PreviewSource source, Long storedRevisionId, String definitionKey,
            int definitionRevision, Instant createdAt, Instant lastAccessedAt, Instant expiresAt,
            List<String> activeStatePath, Map<String, JsonNode> storage, List<RuntimeEvent> history,
            boolean started, boolean active, List<PreviewOperation> transcript,
            List<PreviewDiagnostic> diagnostics) {
    }

    public record PreviewOperation(long sequence, String kind, Instant at, RuntimeEvent input,
            List<String> activeStatePath, Map<String, PreviewStorageChange> storageChanges,
            List<String> acceptedTransitionIds, PreviewBehaviour behaviour, List<PreviewDiagnostic> diagnostics) {
    }

    public record PreviewStorageChange(JsonNode before, JsonNode after) {
    }

    public record PreviewBehaviour(String speech, JsonNode nonVerbal, JsonNode motion, JsonNode display) {
    }

    public record PreviewDiagnostic(String code, String message, String hint) {
    }
}
