package ch.zhaw.prometheus.definition.preview;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.compiled.ImmutableJson;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.definition.document.VerificationExpectation;
import ch.zhaw.prometheus.definition.document.VerificationScenario;
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
        return create(json, source, storedRevisionId, this.randoms.get(), Map.of());
    }

    public ScenarioExecution executeScenario(String json, int scenarioIndex) {
        var document = this.definitionJson.parse(json);
        if (document.verification() == null || scenarioIndex < 0
                || scenarioIndex >= document.verification().scenarios().size()) {
            throw new IllegalArgumentException("Scenario index is outside the definition's verification document");
        }
        VerificationScenario scenario = document.verification().scenarios().get(scenarioIndex);
        if (scenario.events().size() + 1 > this.maxOperations) {
            throw new PreviewLimitException("The scenario exceeds the preview operation limit");
        }
        long seed = scenario.initializerSeed() == null ? 0L : scenario.initializerSeed();
        PreviewSnapshot created = create(json, PreviewSource.UNSAVED, null, new Random(seed),
                scenario.initialStorage());
        try {
            PreviewSnapshot current = created;
            for (var event : scenario.events()) {
                current = acknowledge(created.id(), new RuntimeEvent(event.type(), event.actor(), event.kind(),
                        event.payload()));
            }
            return evaluateScenario(scenarioIndex, scenario, current);
        } finally {
            this.sessions.remove(created.id());
        }
    }

    private PreviewSnapshot create(String json, PreviewSource source, Long storedRevisionId, RandomGenerator random,
            Map<String, JsonNode> initialStorage) {
        if (source == null || source == PreviewSource.SAVED && (storedRevisionId == null || storedRevisionId < 1)
                || source == PreviewSource.UNSAVED && storedRevisionId != null || random == null
                || initialStorage == null) {
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
                random);
        var creation = createRuntime(runtimeRevisionId, compiled, context, initialStorage);
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
            CompiledAgentDefinition definition, AgentRuntimeContext context, Map<String, JsonNode> overrides) {
        try {
            return this.engine.create(revisionId, definition, context, overrides);
        } catch (RuntimeException failure) {
            throw new PreviewExecutionException(failure);
        }
    }

    private static ScenarioExecution evaluateScenario(int scenarioIndex, VerificationScenario scenario,
            PreviewSnapshot snapshot) {
        VerificationExpectation expected = scenario.expected();
        List<ScenarioExpectationResult> expectations = new ArrayList<>();
        List<String> transitions = snapshot.transcript().stream()
                .flatMap(operation -> operation.acceptedTransitionIds().stream()).toList();
        List<ScenarioStorageChange> changes = snapshot.transcript().stream().flatMap(operation ->
                operation.storageChanges().entrySet().stream().map(entry -> new ScenarioStorageChange(
                        operation.sequence(), entry.getKey(), entry.getValue().before(), entry.getValue().after())))
                .toList();
        List<JsonNode> behaviours = snapshot.transcript().stream().map(PreviewOperation::behaviour)
                .filter(java.util.Objects::nonNull).<JsonNode>map(DesignerPreviewService::behaviourNode).toList();
        List<String> modalities = emittedModalities(snapshot.transcript());

        if (expected != null && !expected.activeStatePath().isEmpty()) {
            JsonNode expectedPath = stringArray(expected.activeStatePath());
            JsonNode actualPath = stringArray(snapshot.activeStatePath());
            boolean passed = expectedPath.equals(actualPath);
            expectations.add(new ScenarioExpectationResult("active-state-path", "Active situation path", passed,
                    expectedPath, actualPath, pathExplanation(passed, snapshot.activeStatePath(), transitions)));
        }
        if (expected != null) {
            expected.storage().forEach((key, value) -> {
                JsonNode actual = snapshot.storage().getOrDefault(key, JsonNodeFactory.instance.nullNode());
                boolean passed = sameValue(value, actual);
                boolean changed = changes.stream().anyMatch(change -> change.key().equals(key));
                String explanation = passed
                        ? "The final value matched after " + (changed ? "a recorded storage change." : "no recorded change.")
                        : "The final value did not match; " + (changed ? "the trace records an update." : "the trace records no update.");
                expectations.add(new ScenarioExpectationResult("storage-" + key, "Data: " + key, passed,
                        value, actual, explanation));
            });
            for (int index = 0; index < expected.behaviourFragments().size(); index++) {
                JsonNode fragment = expected.behaviourFragments().get(index);
                boolean passed = behaviours.stream().anyMatch(behaviour -> containsFragment(behaviour, fragment));
                String explanation = passed
                        ? "A recorded behaviour contains this fragment. Emitted: " + joined(modalities) + "."
                        : "No recorded behaviour contains this fragment. Emitted: " + joined(modalities) + ".";
                expectations.add(new ScenarioExpectationResult("behaviour-" + index,
                        "Behaviour fragment " + (index + 1), passed, fragment, jsonArray(behaviours), explanation));
            }
        }
        boolean passed = snapshot.diagnostics().isEmpty()
                && expectations.stream().allMatch(ScenarioExpectationResult::passed);
        return new ScenarioExecution(scenarioIndex, scenario.name(), passed, List.copyOf(expectations),
                snapshot.activeStatePath(), snapshot.storage(), transitions, changes, modalities,
                snapshot.transcript(), snapshot.diagnostics(), true);
    }

    private static List<String> emittedModalities(List<PreviewOperation> operations) {
        Set<String> result = new LinkedHashSet<>();
        operations.stream().map(PreviewOperation::behaviour).filter(java.util.Objects::nonNull).forEach(behaviour -> {
            if (behaviour.speech() != null && !behaviour.speech().isBlank()) result.add("speech");
            addStructuredModalities(result, "nonVerbal", behaviour.nonVerbal());
            addStructuredModalities(result, "motion", behaviour.motion());
            if (behaviour.display() != null && !behaviour.display().isNull()) result.add("display");
        });
        return List.copyOf(result);
    }

    private static void addStructuredModalities(Set<String> target, String prefix, JsonNode value) {
        if (value == null || value.isNull()) return;
        if (value.isObject() && !value.isEmpty()) value.fieldNames().forEachRemaining(key -> target.add(prefix + "." + key));
        else target.add(prefix);
    }

    private static ObjectNode behaviourNode(PreviewBehaviour behaviour) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (behaviour.speech() != null) result.put("speech", behaviour.speech());
        if (behaviour.nonVerbal() != null) result.set("nonVerbal", behaviour.nonVerbal());
        if (behaviour.motion() != null) result.set("motion", behaviour.motion());
        if (behaviour.display() != null) result.set("display", behaviour.display());
        return result;
    }

    private static boolean containsFragment(JsonNode actual, JsonNode expected) {
        if (expected.isObject()) {
            if (!actual.isObject()) return false;
            var fields = expected.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!actual.has(field.getKey()) || !containsFragment(actual.get(field.getKey()), field.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) return expected.equals(actual);
        if (sameValue(expected, actual)) return true;
        if (actual.isContainerNode()) {
            for (JsonNode child : actual) if (containsFragment(child, expected)) return true;
        }
        return false;
    }

    private static boolean sameValue(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        return expected.equals(actual);
    }

    private static ArrayNode stringArray(List<String> values) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.forEach(result::add);
        return result;
    }

    private static ArrayNode jsonArray(List<JsonNode> values) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        values.forEach(result::add);
        return result;
    }

    private static String pathExplanation(boolean passed, List<String> path, List<String> transitions) {
        return (passed ? "The active path matched" : "The active path ended at " + joined(path))
                + "; accepted transitions: " + joined(transitions) + ".";
    }

    private static String joined(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
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

    public record ScenarioExecution(int scenarioIndex, String name, boolean passed,
            List<ScenarioExpectationResult> expectations, List<String> activeStatePath,
            Map<String, JsonNode> storage, List<String> acceptedTransitionIds,
            List<ScenarioStorageChange> storageChanges, List<String> emittedModalities,
            List<PreviewOperation> transcript, List<PreviewDiagnostic> diagnostics,
            boolean discarded) {
        public ScenarioExecution {
            expectations = List.copyOf(expectations);
            activeStatePath = List.copyOf(activeStatePath);
            storage = Collections.unmodifiableMap(new LinkedHashMap<>(storage));
            acceptedTransitionIds = List.copyOf(acceptedTransitionIds);
            storageChanges = List.copyOf(storageChanges);
            emittedModalities = List.copyOf(emittedModalities);
            transcript = List.copyOf(transcript);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record ScenarioExpectationResult(String id, String label, boolean passed,
            JsonNode expected, JsonNode actual, String explanation) {
    }

    public record ScenarioStorageChange(long sequence, String key, JsonNode before, JsonNode after) {
    }

    public record PreviewDiagnostic(String code, String message, String hint) {
    }
}
