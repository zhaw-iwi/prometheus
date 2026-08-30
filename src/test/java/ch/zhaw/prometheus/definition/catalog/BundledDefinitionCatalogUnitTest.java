package ch.zhaw.prometheus.definition.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.core.FacialExpressionSensitivity;
import ch.zhaw.prometheus.agentdefs.core.MultimodalBehaviour;
import ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame;
import ch.zhaw.prometheus.agentdefs.core.SocialContextSensitivity;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGame;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGameUserGuess;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateHealthcareConversation;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateSmartGoalCoaching;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateTherapyAppointmentReminder;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.TwoStateTherapyAppointmentReminder;
import ch.zhaw.prometheus.definition.compiled.CompiledAgentDefinition;
import ch.zhaw.prometheus.definition.compiled.CompiledAtomicState;
import ch.zhaw.prometheus.definition.compiled.CompiledCompositeState;
import ch.zhaw.prometheus.definition.compiled.CompiledFinalState;
import ch.zhaw.prometheus.definition.compiled.DefinitionCompiler;
import ch.zhaw.prometheus.definition.component.BuiltInComponentCatalog;
import ch.zhaw.prometheus.definition.component.builtin.TypedChoicesResourceComponent;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeContext;
import ch.zhaw.prometheus.definition.runtime.AgentRuntimeEngine;
import ch.zhaw.prometheus.definition.runtime.BuiltInRuntimeComponentExecutor;
import ch.zhaw.prometheus.definition.runtime.RuntimeBehaviour;
import ch.zhaw.prometheus.definition.runtime.RuntimeEvent;
import ch.zhaw.prometheus.definition.runtime.RuntimeInvocation;
import ch.zhaw.prometheus.definition.runtime.RuntimeModelGateway;
import ch.zhaw.prometheus.definition.runtime.RuntimePromptBundle;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

class BundledDefinitionCatalogUnitTest {
    private static final List<AgentDefinition> LEGACY_ORACLES = List.of(
            new FacialExpressionSensitivity(),
            new MultimodalBehaviour(),
            new RoleClarificationGuessingGame(),
            new SocialContextSensitivity(),
            new SingleStateGuessingGame(),
            new SingleStateGuessingGameUserGuess(),
            new SingleStateHealthcareConversation(),
            new SingleStateSmartGoalCoaching(),
            new SingleStateTherapyAppointmentReminder(),
            new TwoStateTherapyAppointmentReminder());

    private static final List<String> EXPECTED_KEYS = LEGACY_ORACLES.stream()
            .map(AgentDefinition::key).sorted().toList();

    @Test
    void mainCatalogLoadsTenSortedSchemaAndCompilerValidatedDefinitions() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();

        assertEquals(EXPECTED_KEYS, catalog.definitions().stream()
                .map(definition -> definition.document().key()).toList());
        assertEquals(10, catalog.definitions().size());
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
    void metadataAndInteractionProfilesMatchCurrentPublicDefinitions() {
        BundledDefinitionCatalog catalog = BundledDefinitionCatalog.loadMainCatalog();

        for (AgentDefinition oracle : LEGACY_ORACLES) {
            Agent current = oracle.createAgent();
            CompiledAgentDefinition migrated = catalog.require(oracle.key()).compiled();
            AgentInteractionProfile currentProfile = current.getInteractionProfile();

            assertEquals(current.getName(), migrated.metadata().displayName(), oracle.key());
            assertEquals(current.getDescription(), migrated.metadata().description(), oracle.key());
            assertEquals(oracle.languageCode(), migrated.metadata().languageCode(), oracle.key());
            assertEquals(String.join(".", oracle.packagePath()), migrated.metadata().categoryPath(), oracle.key());
            assertEquals(currentProfile.getSupportedObservations(), migrated.interaction().supportedObservations(),
                    oracle.key());
            assertEquals(currentProfile.getSupportedBehaviourModalities(),
                    migrated.interaction().supportedBehaviourModalities(), oracle.key());
            assertEquals(currentProfile.getProfileTags(), migrated.interaction().profileTags(), oracle.key());
            assertEquals(currentProfile.getProfileTags(), migrated.metadata().tags(), oracle.key());
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
            assertNotNull(creation.startup().behaviour(), bundled.document().key());
            assertEquals("deterministic speech", creation.startup().behaviour().speech(), bundled.document().key());
            assertEquals(1, gateway.generated, bundled.document().key());
            assertFalse(gateway.lastPrompts.responsePrompt().isBlank(), bundled.document().key());
        }
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
}
