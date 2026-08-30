package ch.zhaw.prometheus.definition.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.gson.JsonElement;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.CompiledAction;
import ch.zhaw.prometheus.definition.component.CompiledDecision;
import ch.zhaw.prometheus.definition.component.CompiledPolicy;
import ch.zhaw.prometheus.definition.component.CompiledSelector;
import ch.zhaw.prometheus.definition.component.builtin.IncrementActionComponent;
import ch.zhaw.prometheus.definition.document.AgentDefinitionJson;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AgentRuntimeEngineUnitTest {
    private AgentRuntimeEngine engine;
    private CompiledAgentDefinition definition;
    private FakeModelGateway model;
    private RecordingExecutor components;
    private AgentRuntimeContext context;

    @BeforeEach
    void setUp() {
        AgentDefinitionJson json = new AgentDefinitionJson();
        try (InputStream input = getClass().getResourceAsStream("/agent-definitions/valid/runtime-flow.json")) {
            this.definition = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), json)
                    .compile(json.parse(input));
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
        this.engine = new AgentRuntimeEngine();
        this.model = new FakeModelGateway();
        this.components = new RecordingExecutor(new BuiltInRuntimeComponentExecutor(this.model));
        this.context = new AgentRuntimeContext(this.components, new Random(17));
    }

    @Test
    void startAndGenerateComposeOuterToLeafPolicyAndRecordStableStatePaths() {
        AgentRuntimeCreation creation = this.engine.create(101, this.definition, this.context);
        AgentRuntimeInstance instance = creation.instance();
        assertNull(creation.startup().behaviour());

        AgentRuntimeResult start = this.engine.start(instance, this.context);
        AgentRuntimeResult generate = this.engine.generate(instance, this.context);

        assertEquals("start:main", start.behaviour().speech());
        assertEquals("response:main", generate.behaviour().speech());
        assertEquals("Outer policy.\n\nInner policy.", this.model.prompts.getFirst().responsePrompt());
        assertEquals("Start main.", this.model.prompts.getFirst().starterPrompt());
        assertEquals(List.of("root", "main"), instance.activeStatePath());
        assertEquals(2, instance.history().size());
        instance.history().forEach(event -> assertEquals(List.of("root", "main"), event.statePath()));
    }

    @Test
    void creationRunsInitializersAndNormalStartWhenLifecycleRequestsIt() {
        AgentDefinitionJson json = new AgentDefinitionJson();
        CompiledAgentDefinition automatic;
        try (InputStream input = getClass().getResourceAsStream(
                "/agent-definitions/valid/deterministic-components.json")) {
            automatic = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry(), json).compile(json.parse(input));
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }

        AgentRuntimeCreation creation = this.engine.create(102, automatic, this.context);

        assertTrue(creation.instance().started());
        assertEquals(0, creation.instance().storage().get("round_count").asInt());
        assertEquals("start:round", creation.startup().behaviour().speech());
        assertEquals(1, creation.startup().appendedEvents().size());
    }

    @Test
    void compositeTransitionPrecedesInnerAndTransitionOrderIsNumeric() {
        AgentRuntimeInstance outer = createInstance();
        this.engine.start(outer, this.context);
        AgentRuntimeResult outerResult = this.engine.acknowledge(outer,
                event("obs.user_utterance", "route"), this.context);

        assertEquals("outer_win", outer.activeLeafStateId());
        assertEquals(List.of("outer-route"), outerResult.acceptedTransitionIds());

        AgentRuntimeInstance ordered = createInstance();
        AgentRuntimeResult orderedResult = this.engine.acknowledge(ordered,
                event("obs.emotion.face", "order"), this.context);
        assertEquals("ordered", ordered.activeLeafStateId());
        assertEquals(List.of("order-early"), orderedResult.acceptedTransitionIds());
    }

    @Test
    void selfTransitionRunsActionsInOrderAndResetRestoresOnlyInstanceState() {
        AgentRuntimeInstance first = createInstance();
        AgentRuntimeInstance second = createInstance();
        assertSame(first.definition(), second.definition());

        AgentRuntimeResult transition = this.engine.acknowledge(first, event("obs.human.presence", "self"), this.context);

        assertEquals(List.of(0L, 1L), this.components.incrementValuesBeforeExecution);
        assertEquals(2, first.storage().get("count").asInt());
        assertEquals(0, second.storage().get("count").asInt());
        assertEquals(List.of("self"), transition.acceptedTransitionIds());
        assertEquals("start:main", transition.behaviour().speech());
        assertEquals(2, transition.storageChanges().get("count").after().value().asInt());

        AgentRuntimeResult reset = this.engine.reset(first, this.context);
        assertEquals(0, first.storage().get("count").asInt());
        assertEquals("main", first.activeLeafStateId());
        assertTrue(first.history().isEmpty());
        assertFalse(first.started());
        assertFalse(reset.removedEvents().isEmpty());
    }

    @Test
    void reprocessEntryChainsToFinalAndFinalIsInactive() {
        AgentRuntimeInstance instance = createInstance();

        AgentRuntimeResult chained = this.engine.acknowledge(instance, event("obs.hand.sign", "chain"), this.context);
        AgentRuntimeResult afterFinal = this.engine.acknowledge(instance, event("obs.user_utterance", "ignored"), this.context);
        AgentRuntimeResult generated = this.engine.generate(instance, this.context);

        assertEquals(List.of("to-relay", "relay-done"), chained.acceptedTransitionIds());
        assertEquals("done", instance.activeLeafStateId());
        assertFalse(instance.isActive());
        assertTrue(afterFinal.acceptedTransitionIds().isEmpty());
        assertNull(afterFinal.behaviour());
        assertNull(generated.behaviour());
    }

    @Test
    void decisionsAreAndedAndShortCircuitAfterFirstRejection() {
        AgentRuntimeInstance instance = createInstance();
        this.model.decisions.add(false);
        this.model.decisions.add(true);

        AgentRuntimeResult result = this.engine.acknowledge(instance, event("obs.weather.current", "and"), this.context);

        assertTrue(result.acceptedTransitionIds().isEmpty());
        assertEquals("main", instance.activeLeafStateId());
        assertEquals(1, this.model.decisionCalls);
    }

    @Test
    void obliviousReentryRemovesOnlyEventsWhosePathContainsThatState() {
        AgentRuntimeInstance instance = createInstance();
        this.engine.start(instance, this.context);
        this.engine.acknowledge(instance, event("obs.social.grouping", "first"), this.context);
        this.engine.acknowledge(instance, event("obs.social.context", "back"), this.context);

        AgentRuntimeResult secondEntry = this.engine.acknowledge(instance, event("obs.social.grouping", "second"), this.context);

        assertEquals("forget", instance.activeLeafStateId());
        assertFalse(secondEntry.removedEvents().isEmpty());
        assertTrue(secondEntry.removedEvents().stream().allMatch(event -> event.statePath().contains("forget")));
        assertTrue(instance.history().stream().anyMatch(event -> "first".equals(event.payload())
                && event.statePath().equals(List.of("root", "main"))));
    }

    @Test
    void representativeOuterTransitionTraceMatchesLegacyEngine() {
        AgentRuntimeInstance declarative = createInstance();
        this.engine.start(declarative, this.context);
        this.engine.acknowledge(declarative, event("obs.user_utterance", "route"), this.context);

        State oldOuterWin = new State("outer_win", new FixedPolicy(), List.of());
        State oldInnerLose = new State("inner_lose", new FixedPolicy(), List.of());
        State oldMain = new State("main", new FixedPolicy(),
                List.of(new Transition(new LatestEventTypeDecision("obs.user_utterance"), oldInnerLose)));
        OuterState oldRoot = new OuterState("outer", "root",
                List.of(new Transition(new LatestEventTypeDecision("obs.user_utterance"), oldOuterWin)), oldMain);
        Agent legacy = new Agent("legacy", "trace", oldRoot);
        PolicyRuntime oldRuntime = new PolicyRuntime(new PromptMessageAssembler(), new NoOpLanguageModelGateway());
        legacy.start(oldRuntime);
        legacy.acknowledge(Event.observation("obs.user_utterance", "user", "route"), oldRuntime);

        List<String> legacyTypes = legacy.getEventHistory().toList().stream().map(Event::getType).toList();
        List<List<String>> legacyPaths = legacy.getEventHistory().toList().stream().map(Event::getStatePath).toList();
        assertEquals(legacyTypes, declarative.history().stream().map(RuntimeEvent::type).toList());
        assertEquals(legacyPaths, declarative.history().stream().map(RuntimeEvent::statePath).toList());
        assertEquals(legacy.getCurrentState().getName(), declarative.activeLeafStateId());
    }

    private AgentRuntimeInstance createInstance() {
        return this.engine.create(101, this.definition, this.context).instance();
    }

    private static RuntimeEvent event(String type, String payload) {
        return new RuntimeEvent(type, "user", "observation", payload);
    }

    private static final class FakeModelGateway implements RuntimeModelGateway {
        private final List<RuntimePromptBundle> prompts = new ArrayList<>();
        private final Deque<Boolean> decisions = new ArrayDeque<>();
        private int decisionCalls;

        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            this.prompts.add(prompts);
            return RuntimeBehaviour.speechOnly((prompts.starting() ? "start:" : "response:")
                    + invocation.stateId());
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            this.decisionCalls++;
            return this.decisions.isEmpty() ? false : this.decisions.removeFirst();
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            return JsonNodeFactory.instance.textNode("extracted");
        }
    }

    private static final class RecordingExecutor implements RuntimeComponentExecutor {
        private final RuntimeComponentExecutor delegate;
        private final List<Long> incrementValuesBeforeExecution = new ArrayList<>();

        private RecordingExecutor(RuntimeComponentExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public RuntimeBehaviour start(List<CompiledPolicy> policies, RuntimeInvocation invocation) {
            return this.delegate.start(policies, invocation);
        }

        @Override
        public RuntimeBehaviour generate(List<CompiledPolicy> policies, RuntimeInvocation invocation) {
            return this.delegate.generate(policies, invocation);
        }

        @Override
        public boolean decide(CompiledDecision decision, RuntimeInvocation invocation) {
            return this.delegate.decide(decision, invocation);
        }

        @Override
        public void execute(CompiledAction action, RuntimeInvocation invocation, RuntimeStorage storage) {
            if (action instanceof IncrementActionComponent increment) {
                this.incrementValuesBeforeExecution.add(storage.get(increment.targetStorageKey()).longValue());
            }
            this.delegate.execute(action, invocation, storage);
        }

        @Override
        public boolean selects(CompiledSelector selector, RuntimeEvent event, String evaluatingStateId) {
            return this.delegate.selects(selector, event, evaluatingStateId);
        }
    }

    private static final class FixedPolicy extends Policy {
        @Override
        public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
                LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly("start:" + state.getName());
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
                LanguageModelGateway languageModelGateway) {
            return BehaviourPlan.speechOnly("response:" + state.getName());
        }

        @Override
        public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
                LanguageModelGateway languageModelGateway) {
            return "";
        }

        @Override
        public String describe() {
            return "fixed";
        }
    }

    private static final class NoOpLanguageModelGateway implements LanguageModelGateway {
        @Override public String complete(List<PromptMessage> messages) { return ""; }
        @Override public boolean decide(List<PromptMessage> messages) { return false; }
        @Override public JsonElement extract(List<PromptMessage> messages) { return null; }
        @Override public JsonElement summarise(List<PromptMessage> messages) { return null; }
        @Override public String summariseOffline(List<PromptMessage> messages) { return ""; }
    }
}
