package ch.zhaw.prometheus.definition.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.compiled.CompiledFinalState;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.ComponentAuthoringExposure;
import ch.zhaw.prometheus.definition.component.ComponentKey;
import ch.zhaw.prometheus.definition.component.builtin.TypedChoicesResourceComponent;
import ch.zhaw.prometheus.definition.document.AtomicStateDefinition;
import ch.zhaw.prometheus.definition.document.ComponentEnvelope;
import ch.zhaw.prometheus.definition.document.CompositeStateDefinition;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;

class BundledDefinitionCatalogUnitTest {
    private static final List<String> EXPECTED_KEYS = List.of(
            "core.facial_expression_sensitivity",
            "core.multimodal_behaviour",
            "core.rock_scissor_paper",
            "core.role_clarification_guessing_game",
            "core.social_context_sensitivity",
            "core.talk_to_me",
            "usecases.healthcare.guessing_game",
            "usecases.healthcare.guessing_game_user_guess",
            "usecases.healthcare.healthcare_conversation",
            "usecases.healthcare.smart_goal_coaching",
            "usecases.healthcare.therapy_appointment_reminder",
            "usecases.healthcare.therapy_appointment_reminder_intro");

    @Test
    void mainCatalogLoadsTwelveSortedSchemaAndCompilerValidatedDefinitions() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();

        assertEquals(EXPECTED_KEYS, catalog.definitions().stream()
                .map(definition -> definition.document().key()).toList());
        assertEquals(12, catalog.definitions().size());
        DefinitionCompiler compiler = new DefinitionCompiler(BuiltInComponentCatalog.createRegistry());
        for (BundledAgentDefinition bundled : catalog.definitions()) {
            assertEquals(1, bundled.document().schemaVersion());
            assertEquals(1, bundled.document().revision());
            assertEquals(bundled.document().key(), bundled.compiled().key());
            assertEquals(bundled.document().revision(), bundled.compiled().revision());
            assertEquals(64, bundled.compiled().contentHash().length());
            assertTrue(bundled.resource().endsWith("revision-1.json"));
            assertEquals(List.of(), compiler.validate(bundled.document()).diagnostics(), bundled.document().key());
        }
        assertThrows(IllegalArgumentException.class, () -> catalog.require("missing.definition"));
    }

    @Test
    void productionJsonContainsNoImplementationNamesBeansOrScripts() throws IOException {
        for (BundledAgentDefinition bundled : BundledDefinitionCatalog.loadMainCatalog().definitions()) {
            String resource = "/agent-definitions/catalog/main/" + bundled.resource();
            try (InputStream input = BundledDefinitionCatalogUnitTest.class.getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(json.matches("(?is).*\\\"(?:class|className|bean|beanName|script|scripts|sourceCode)\\\".*"),
                        bundled.document().key());
                assertFalse(json.contains("ch.zhaw.prometheus"), bundled.document().key());
                assertFalse(json.contains(".java"), bundled.document().key());
            }
        }
    }

    @Test
    void everyComponentUsedByTheTwelveDefinitionsHasSafeV2AuthoringCoverage() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();
        var registry = BuiltInComponentCatalog.createRegistry();
        Set<ComponentKey> used = new HashSet<>();

        for (BundledAgentDefinition bundled : catalog.definitions()) {
            var document = bundled.document();
            document.lifecycle().initializers().forEach(component -> add(used, component));
            document.resources().forEach(resource -> used.add(new ComponentKey(resource.kind(), resource.version())));
            document.states().forEach(state -> {
                if (state instanceof AtomicStateDefinition atomic) {
                    add(used, atomic.eventSelector());
                    add(used, atomic.policy());
                } else if (state instanceof CompositeStateDefinition composite) {
                    add(used, composite.eventSelector());
                    add(used, composite.policy());
                }
            });
            document.transitions().forEach(transition -> {
                transition.decisions().forEach(component -> add(used, component));
                transition.actions().forEach(component -> add(used, component));
            });
        }

        assertEquals(Set.of(
                new ComponentKey("prometheus.action.extract", 1),
                new ComponentKey("prometheus.action.prompt-behaviour", 1),
                new ComponentKey("prometheus.action.rps-evaluate-round", 1),
                new ComponentKey("prometheus.action.rps-select-sign", 1),
                new ComponentKey("prometheus.decision.latest-event-type", 1),
                new ComponentKey("prometheus.decision.prompt", 1),
                new ComponentKey("prometheus.initializer.random-choice", 1),
                new ComponentKey("prometheus.policy.exact-text", 1),
                new ComponentKey("prometheus.policy.prompt", 1),
                new ComponentKey("prometheus.policy.rps-result", 1),
                new ComponentKey("prometheus.policy.rps-reveal", 1),
                new ComponentKey("prometheus.resource.typed-choices", 1),
                new ComponentKey("prometheus.selector.state-path", 1)), used);

        for (ComponentKey key : used) {
            var metadata = registry.find(key.kind(), key.version()).orElseThrow().uiMetadata();
            if (metadata.exposure() == ComponentAuthoringExposure.GUIDED) {
                assertFalse(metadata.capabilityGroup().isBlank(), key.toString());
            } else {
                assertFalse(metadata.advancedReason().isBlank(), key.toString());
            }
        }
    }

    @Test
    void representativeTopologiesPreserveCompositeBranchingAndReactionLoops() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();

        CompiledAgentDefinition signal = catalog.require("core.facial_expression_sensitivity").compiled();
        assertTrue(signal.state("context") instanceof CompiledCompositeState);
        assertTrue(signal.state("task") instanceof CompiledAtomicState);
        assertTrue(signal.state("done") instanceof CompiledFinalState);
        assertEquals(List.of("context", "task"), signal.pathTo("task").stream().map(state -> state.id()).toList());
        assertEquals(List.of("task_end", "task_react_obs_emotion_face"),
                signal.transitionsFrom("task").stream().map(transition -> transition.id()).toList());

        CompiledAgentDefinition role = catalog.require("core.role_clarification_guessing_game").compiled();
        assertEquals(List.of("role_end", "role_to_valerian_guesses", "role_to_user_guesses"),
                role.transitionsFrom("role_clarification").stream().map(transition -> transition.id()).toList());

        CompiledAgentDefinition intro = catalog
                .require("usecases.healthcare.therapy_appointment_reminder_intro").compiled();
        assertEquals(List.of("context", "introduction"),
                intro.pathTo("introduction").stream().map(state -> state.id()).toList());
        assertEquals(List.of("therapy_end", "therapy_social_reaction"),
                intro.transitionsFrom("therapy_reminder").stream().map(transition -> transition.id()).toList());
    }

    @Test
    void everyCatalogDefinitionStartsThroughCompiledRuntimeWithDeterministicFake() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();
        AgentRuntimeEngine engine = new AgentRuntimeEngine();

        long revisionId = 100;
        for (BundledAgentDefinition bundled : catalog.definitions()) {
            RecordingGateway gateway = new RecordingGateway();
            var creation = engine.create(revisionId++, bundled.compiled(),
                    new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(gateway), new Random(41)));

            assertTrue(creation.instance().isActive(), bundled.document().key());
            assertTrue(creation.instance().snapshot().started(), bundled.document().key());
            if ("core.talk_to_me".equals(bundled.document().key())) {
                assertNull(creation.startup().behaviour());
                assertEquals(0, gateway.generated);
                continue;
            }
            assertNotNull(creation.startup().behaviour(), bundled.document().key());
            assertEquals("deterministic speech", creation.startup().behaviour().speech(), bundled.document().key());
            assertEquals(1, gateway.generated, bundled.document().key());
            assertFalse(gateway.lastPrompts.responsePrompt().isBlank(), bundled.document().key());
        }
    }

    @Test
    void talkToMeRuntimeEmitsSubmittedTextExactlyWithoutModelGeneration() {
        CompiledAgentDefinition definition = BundledDefinitionCatalog.loadMainCatalog()
                .require("core.talk_to_me").compiled();
        AgentRuntimeEngine engine = new AgentRuntimeEngine();
        RecordingGateway gateway = new RecordingGateway();
        AgentRuntimeContext context = new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(gateway),
                new Random(1));
        var creation = engine.create(1, definition, context);
        String text = "Grüezi, \"Zürich\"!\nLine two 🌍";

        var result = engine.acknowledge(creation.instance(),
                new RuntimeEvent("obs.user_utterance", "user", "observation", text, List.of("talk")), context);

        assertEquals(List.of("repeat"), result.acceptedTransitionIds());
        assertEquals(text, result.behaviour().speech());
        assertEquals(0, gateway.generated);
    }

    @Test
    void rpsRuntimeCoversRepeatAndFinalPathsWithDeterministicComponents() {
        CompiledAgentDefinition definition = BundledDefinitionCatalog.loadMainCatalog()
                .require("core.rock_scissor_paper").compiled();
        AgentRuntimeEngine engine = new AgentRuntimeEngine();
        ScriptedRpsGateway gateway = new ScriptedRpsGateway();
        AgentRuntimeContext context = new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(gateway),
                new Random(1));
        var creation = engine.create(1, definition, context);

        var reveal = engine.acknowledge(creation.instance(), userEvent("ready", "start"), context);
        assertEquals(List.of("start_to_reveal"), reveal.acceptedTransitionIds());
        assertEquals("Rock, scissor, paper", reveal.behaviour().speech());
        assertEquals("rock", reveal.behaviour().motion().value().path("handSign").asText());

        var firstResult = engine.acknowledge(creation.instance(), handEvent("scissor", "reveal"), context);
        assertEquals(List.of("reveal_to_result"), firstResult.acceptedTransitionIds());
        assertEquals("agent", firstResult.after().storage().get("rps_last_round").value().path("winner").asText());
        assertEquals(1, firstResult.after().storage().get("rps_rounds").value().size());
        assertTrue(firstResult.behaviour().speech().startsWith("I win: rock beats scissor"));

        var secondReveal = engine.acknowledge(creation.instance(), userEvent("again", "result"), context);
        assertEquals(List.of("result_to_reveal"), secondReveal.acceptedTransitionIds());
        assertEquals("scissor", secondReveal.behaviour().motion().value().path("handSign").asText());

        var secondResult = engine.acknowledge(creation.instance(), handEvent("rock", "reveal"), context);
        assertEquals("user", secondResult.after().storage().get("rps_last_round").value().path("winner").asText());
        assertEquals(2, secondResult.after().storage().get("rps_rounds").value().size());

        var finished = engine.acknowledge(creation.instance(), userEvent("stop", "result"), context);
        assertEquals(List.of("context_end"), finished.acceptedTransitionIds());
        assertFalse(creation.instance().isActive());
        assertTrue(gateway.lastPrompts.responsePrompt().contains("rock-scissor-paper lab game is finished"));
        assertEquals(2, gateway.generated);
    }

    @Test
    void therapyContextComesFromTypedResourceAndInjectedRandomGenerator() {
        CompiledAgentDefinition definition = BundledDefinitionCatalog.loadMainCatalog()
                .require("usecases.healthcare.therapy_appointment_reminder").compiled();
        AgentRuntimeEngine engine = new AgentRuntimeEngine();

        var first = engine.create(1, definition, context(7));
        var second = engine.create(2, definition, context(7));
        JsonNode selected = first.instance().snapshot().storage().get("therapyAppointmentContext").value();

        assertEquals(selected, second.instance().snapshot().storage().get("therapyAppointmentContext").value());
        assertEquals("occupational_therapy", selected.path("type").asText());
        TypedChoicesResourceComponent choices = (TypedChoicesResourceComponent) definition.resources().getFirst()
                .component();
        assertEquals(3, choices.values().size());
        assertTrue(choices.values().stream().anyMatch(value -> value.value().equals(selected)));
        assertEquals(Map.of(), first.instance().snapshot().storage().entrySet().stream()
                .filter(entry -> !List.of("therapyAppointmentContext").contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test
    void finalTransitionExtractionAndPromptBehaviourExecuteThroughRuntime() {
        CompiledAgentDefinition definition = BundledDefinitionCatalog.loadMainCatalog()
                .require("core.facial_expression_sensitivity").compiled();
        AgentRuntimeEngine engine = new AgentRuntimeEngine();
        RecordingGateway gateway = new RecordingGateway();
        gateway.decision = true;
        AgentRuntimeContext context = new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(gateway),
                new Random(11));
        var creation = engine.create(1, definition, context);

        var result = engine.acknowledge(creation.instance(),
                new RuntimeEvent("obs.user_utterance", "user", "observation", "stop", List.of("context", "task")),
                context);

        assertEquals(List.of("context_end"), result.acceptedTransitionIds());
        assertFalse(creation.instance().isActive());
        assertEquals("deterministic speech", result.behaviour().speech());
        assertTrue(gateway.lastPrompts.responsePrompt().contains("Facial Expression Sensitivity demo is finished"));
        assertEquals(JsonNodeFactory.instance.objectNode(),
                creation.instance().snapshot().storage().get("outcome").value());
    }

    private static AgentRuntimeContext context(long seed) {
        return new AgentRuntimeContext(new BuiltInRuntimeComponentExecutor(new RecordingGateway()), new Random(seed));
    }

    private static void add(Set<ComponentKey> used, ComponentEnvelope component) {
        if (component != null) {
            used.add(new ComponentKey(component.kind(), component.version()));
        }
    }

    private static RuntimeEvent userEvent(String payload, String leaf) {
        return new RuntimeEvent("obs.user_utterance", "user", "observation", payload, List.of("context", leaf));
    }

    private static RuntimeEvent handEvent(String sign, String leaf) {
        return new RuntimeEvent("obs.hand.sign", "sensor", "observation", "{\"sign\":\"" + sign + "\"}",
                List.of("context", leaf));
    }

    private static final class RecordingGateway implements RuntimeModelGateway {
        private int generated;
        private RuntimePromptBundle lastPrompts;
        private boolean decision;

        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            this.generated++;
            this.lastPrompts = prompts;
            return RuntimeBehaviour.speechOnly("deterministic speech");
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            return this.decision;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private static final class ScriptedRpsGateway implements RuntimeModelGateway {
        private int generated;
        private RuntimePromptBundle lastPrompts;

        @Override
        public RuntimeBehaviour generate(RuntimePromptBundle prompts, RuntimeInvocation invocation) {
            this.generated++;
            this.lastPrompts = prompts;
            return RuntimeBehaviour.speechOnly("model speech " + this.generated);
        }

        @Override
        public boolean decide(String prompt, RuntimeInvocation invocation) {
            String payload = invocation.history().isEmpty() ? "" : invocation.history().getLast().payload();
            if ("stop".equals(payload)) {
                return true;
            }
            if ("ready".equals(payload)) {
                return prompt.contains("ready to start a round");
            }
            if ("again".equals(payload)) {
                return prompt.contains("play another round");
            }
            return false;
        }

        @Override
        public JsonNode extract(String prompt, JsonNode outputSchema, RuntimeInvocation invocation) {
            throw new AssertionError("RPS has no extraction action");
        }
    }
}
