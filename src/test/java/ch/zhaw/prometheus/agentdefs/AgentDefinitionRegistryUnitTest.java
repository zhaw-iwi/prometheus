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

    private static final List<String> EXPECTED_KEYS = List.of(
            "basic.single_state_guessing_game",
            "basic.single_state_micro_coaching",
            "basic.single_state_co_creation",
            "basic.four_states_circular",
            "basic.four_states_linear",
            "multimodal.single_state_in",
            "multimodal.single_state_out",
            "multimodal.single_state_in_out",
            "gigitdsr.guessing_game_with_gestures",
            "gigitdsr.social_context_sensitivity",
            "gigitdsr.rock_scissor_paper",
            "gigitdsr.tour_conversation",
            "gigitdsr.tour_conversation_social_context",
            "gigielderlycare.therapy_appointment_reminder",
            "gigielderlycare.guessing_game",
            "gigielderlycare.guessing_game_user_guess",
            "gigielderlycare.smart_goal_coaching");

    private static final Map<String, String> EXPECTED_LANGUAGE_BY_KEY = Map.ofEntries(
            Map.entry("basic.single_state_guessing_game", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_micro_coaching", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_co_creation", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_circular", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_linear", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_out", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in_out", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigitdsr.guessing_game_with_gestures", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigitdsr.social_context_sensitivity", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigitdsr.rock_scissor_paper", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigitdsr.tour_conversation", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigitdsr.tour_conversation_social_context", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigielderlycare.therapy_appointment_reminder", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigielderlycare.guessing_game", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigielderlycare.guessing_game_user_guess", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("gigielderlycare.smart_goal_coaching", AgentDefinition.LANGUAGE_GERMAN));

    @Test
    void registryExposesExpectedUniqueDefinitionKeys() {
        AgentDefinitionRegistry registry = new AgentDefinitionRegistry();
        List<String> keys = registry.list().stream().map(AgentDefinition::key).toList();

        assertEquals(EXPECTED_KEYS, keys);
        assertEquals(EXPECTED_KEYS.size(), new HashSet<>(keys).size());
        assertEquals(EXPECTED_KEYS.size(), EXPECTED_LANGUAGE_BY_KEY.size());
        for (String key : EXPECTED_KEYS) {
            assertTrue(registry.findByKey(key).isPresent(), "missing definition key " + key);
            assertTrue(EXPECTED_LANGUAGE_BY_KEY.containsKey(key), "missing expected language for " + key);
        }
        assertTrue(registry.findByKey("unknown").isEmpty());
        assertTrue(registry.findByKey("").isEmpty());
        assertTrue(registry.findByKey(null).isEmpty());
    }

    @Test
    void everyRegisteredDefinitionCanCreateAgent() {
        AgentDefinitionRegistry registry = new AgentDefinitionRegistry();

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
        AgentDefinition duplicate = new ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame();

        assertThrows(IllegalArgumentException.class, () -> new AgentDefinitionRegistry(List.of(duplicate, duplicate)));
    }
}
