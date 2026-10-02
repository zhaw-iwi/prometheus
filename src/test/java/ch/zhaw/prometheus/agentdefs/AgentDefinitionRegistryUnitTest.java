package ch.zhaw.prometheus.agentdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.Agent;

class AgentDefinitionRegistryUnitTest {
    @Test
    void onlyCoreDefinitionsOptInToCapabilityAwareness() {
        for (AgentDefinition definition : registryWithBuiltIns().list()) {
            boolean core = definition.key().startsWith("core.");
            assertEquals(core, definition.capabilityAwareness(), definition.key());
            assertEquals(core, definition.createAgent().getInteractionProfile().isCapabilityAwareness(), definition.key());
        }
        AgentDefinition reused = new AgentDefinition() {
            public String key() { return "deployment.reused"; }
            public Agent createAgent() {
                return applyDefinitionMetadata(new ch.zhaw.prometheus.agentdefs.core.MultimodalBehaviour().createAgent());
            }
        };
        assertFalse(reused.createAgent().getInteractionProfile().isCapabilityAwareness());
    }

    @Test
    void conversationalDefinitionsDeclareLiveAndEveryReachablePolicyIsSupported() {
        var adapter = new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter();
        for (AgentDefinition definition : registryWithBuiltIns().list()) {
            var agent = definition.createAgent();
            boolean conversational = java.util.Set.of(
                    "core.facial_expression_sensitivity", "core.generic_multimodal_behaviour", "core.live_multimodal", "core.multimodal_behaviour",
                    "core.rock_scissor_paper", "core.role_clarification_guessing_game", "core.social_context_sensitivity",
                    "usecases.healthcare.guessing_game", "usecases.healthcare.guessing_game_user_guess",
                    "usecases.healthcare.healthcare_conversation", "usecases.healthcare.smart_goal_coaching",
                    "usecases.healthcare.therapy_appointment_reminder", "usecases.healthcare.therapy_appointment_reminder_intro")
                    .contains(definition.key());
            assertEquals(conversational, definition.externalRealtimeSpeech(), definition.key());
            assertEquals(conversational, agent.getInteractionProfile().isExternalRealtimeSpeech(), definition.key());
            assertEquals(conversational, adapter.supports(agent), definition.key());
            if (conversational) {
                assertFalse(adapter.instructions(agent).isBlank(), definition.key());
                // Retain outer context while inspecting each reachable inner state.
                var root = agent.getCurrentState();
                for (var state : agent.reachableStates()) {
                    if (root instanceof ch.zhaw.prometheus.model.OuterState && !(state instanceof ch.zhaw.prometheus.model.OuterState)) {
                        org.springframework.test.util.ReflectionTestUtils.setField(root, "innerCurrent", state);
                    } else {
                        org.springframework.test.util.ReflectionTestUtils.setField(agent, "currentState", state);
                    }
                    assertTrue(adapter.supports(agent), definition.key() + ": " + state.getName());
                    assertFalse(adapter.instructions(agent).isBlank(), definition.key() + ": " + state.getName());
                    org.springframework.test.util.ReflectionTestUtils.setField(agent, "currentState", root);
                }
            }
        }
    }

    @Test
    void anotherDefinitionReusingAnOptedInAgentStillDefaultsToNoLive() {
        AgentDefinition deploymentDefinition = new AgentDefinition() {
            public String key() { return "deployment.rps"; }
            public ch.zhaw.prometheus.model.Agent createAgent() {
                return applyDefinitionMetadata(new ch.zhaw.prometheus.agentdefs.core.RockScissorPaper().createAgent());
            }
        };
        assertFalse(deploymentDefinition.createAgent().getInteractionProfile().isExternalRealtimeSpeech());
    }


    private static final List<String> EXPECTED_KEYS = List.of(
            "core.facial_expression_sensitivity",
            "core.generic_multimodal_behaviour",
            "core.live_multimodal",
            "core.multimodal_behaviour",
            "core.rock_scissor_paper",
            "core.rock_scissor_paper_match",
            "core.role_clarification_guessing_game",
            "core.social_context_sensitivity",
            "core.talk_to_me",
            "usecases.healthcare.guessing_game",
            "usecases.healthcare.guessing_game_user_guess",
            "usecases.healthcare.healthcare_conversation",
            "usecases.healthcare.smart_goal_coaching",
            "usecases.healthcare.therapy_appointment_reminder",
            "usecases.healthcare.therapy_appointment_reminder_intro");

    private static final Map<String, String> EXPECTED_LANGUAGE_BY_KEY = Map.ofEntries(
            Map.entry("core.facial_expression_sensitivity", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.generic_multimodal_behaviour", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.live_multimodal", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.multimodal_behaviour", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.rock_scissor_paper", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.rock_scissor_paper_match", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.role_clarification_guessing_game", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.social_context_sensitivity", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("core.talk_to_me", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.guessing_game", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.guessing_game_user_guess", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.healthcare_conversation", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.smart_goal_coaching", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.therapy_appointment_reminder", AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry("usecases.healthcare.therapy_appointment_reminder_intro", AgentDefinition.LANGUAGE_ENGLISH));

    @Test
    void registryExposesExpectedUniqueDefinitionKeys() {
        AgentDefinitionRegistry registry = registryWithBuiltIns();
        List<String> keys = registry.list().stream().map(AgentDefinition::key).toList();

        assertEquals(EXPECTED_KEYS, keys);
        assertEquals(EXPECTED_KEYS.size(), new HashSet<>(keys).size());
        assertEquals(EXPECTED_KEYS.size(), EXPECTED_LANGUAGE_BY_KEY.size());
        for (String key : EXPECTED_KEYS) {
            assertTrue(registry.findByKey(key).isPresent(), "missing definition key " + key);
            assertTrue(EXPECTED_LANGUAGE_BY_KEY.containsKey(key), "missing expected language contract for " + key);
        }
        assertTrue(registry.findByKey("unknown").isEmpty());
        assertTrue(registry.findByKey("").isEmpty());
        assertTrue(registry.findByKey(null).isEmpty());
    }

    @Test
    void everyRegisteredDefinitionCanCreateAgent() {
        AgentDefinitionRegistry registry = registryWithBuiltIns();

        for (AgentDefinition definition : registry.list()) {
            Agent agent = definition.createAgent();

            assertNotNull(agent, definition.key());
            assertNotNull(agent.getName(), definition.key());
            assertFalse(agent.getName().isBlank(), definition.key());
            assertNotNull(agent.getDescription(), definition.key());
            assertNotNull(agent.getCurrentState(), definition.key());
            assertNotNull(agent.getInteractionProfile(), definition.key());
            String expectedLanguage = EXPECTED_LANGUAGE_BY_KEY.get(definition.key());
            assertEquals(expectedLanguage, definition.languageCode(), definition.key());
            assertEquals(expectedLanguage, agent.getLanguageCode(), definition.key());
            assertEquals(agent.getName(), definition.displayName(), definition.key());
            assertEquals(agent.getDescription(), definition.description(), definition.key());
        }
    }

    @Test
    void duplicateDefinitionKeysFailFast() {
        AgentDefinition duplicate = new ch.zhaw.prometheus.agentdefs.core.SocialContextSensitivity();

        assertThrows(IllegalArgumentException.class, () -> new AgentDefinitionRegistry(List.of(duplicate, duplicate)));
    }

    private static AgentDefinitionRegistry registryWithBuiltIns() {
        return new AgentDefinitionRegistry(List.of(
                new ch.zhaw.prometheus.agentdefs.core.FacialExpressionSensitivity(),
                new ch.zhaw.prometheus.agentdefs.core.GenericMultimodalBehaviour(),
                new ch.zhaw.prometheus.agentdefs.core.LiveMultimodal(),
                new ch.zhaw.prometheus.agentdefs.core.MultimodalBehaviour(),
                new ch.zhaw.prometheus.agentdefs.core.RockScissorPaper(),
                new ch.zhaw.prometheus.agentdefs.core.RockScissorPaperMatch(),
                new ch.zhaw.prometheus.agentdefs.core.RoleClarificationGuessingGame(),
                new ch.zhaw.prometheus.agentdefs.core.SocialContextSensitivity(),
                new ch.zhaw.prometheus.agentdefs.core.TalkToMe(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGame(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateGuessingGameUserGuess(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateHealthcareConversation(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateSmartGoalCoaching(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.SingleStateTherapyAppointmentReminder(),
                new ch.zhaw.prometheus.agentdefs.usecases.healthcare.TwoStateTherapyAppointmentReminder()));
    }
}
