package ch.zhaw.prometheus.agentdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            tdsrKey("de", "guessing_game_with_gestures"),
            tdsrKey("de", "social_context_sensitivity"),
            tdsrKey("de", "rock_scissor_paper"),
            tdsrKey("de", "tour_conversation"),
            tdsrKey("de", "tour_conversation_social_context"),
            tdsrKey("fr", "guessing_game_with_gestures"),
            tdsrKey("fr", "social_context_sensitivity"),
            tdsrKey("fr", "rock_scissor_paper"),
            tdsrKey("fr", "tour_conversation"),
            tdsrKey("fr", "tour_conversation_social_context"),
            tdsrKey("it", "guessing_game_with_gestures"),
            tdsrKey("it", "social_context_sensitivity"),
            tdsrKey("it", "rock_scissor_paper"),
            tdsrKey("it", "tour_conversation"),
            tdsrKey("it", "tour_conversation_social_context"),
            tdsrKey("en", "guessing_game_with_gestures"),
            tdsrKey("en", "social_context_sensitivity"),
            tdsrKey("en", "rock_scissor_paper"),
            tdsrKey("en", "tour_conversation"),
            tdsrKey("en", "tour_conversation_social_context"),
            tdsrKey("babylon", "guessing_game_with_gestures"),
            tdsrKey("babylon", "social_context_sensitivity"),
            tdsrKey("babylon", "rock_scissor_paper"),
            tdsrKey("babylon", "tour_conversation"),
            tdsrKey("babylon", "tour_conversation_social_context"),
            tdsrShhdKey("de", "epfl_active"),
            tdsrShhdKey("de", "furka"),
            tdsrShhdKey("de", "interviewing_people"),
            tdsrShhdKey("de", "supsi_active"),
            tdsrShhdKey("de", "unis_student"),
            "elderlycare.therapy_appointment_reminder",
            "elderlycare.guessing_game",
            "elderlycare.guessing_game_user_guess",
            "elderlycare.smart_goal_coaching");

    private static final Map<String, String> EXPECTED_LANGUAGE_BY_KEY = Map.ofEntries(
            Map.entry("basic.single_state_guessing_game", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_micro_coaching", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_co_creation", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_circular", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_linear", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_out", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in_out", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("de", "guessing_game_with_gestures"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("de", "social_context_sensitivity"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("de", "rock_scissor_paper"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("de", "tour_conversation"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("de", "tour_conversation_social_context"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrKey("fr", "guessing_game_with_gestures"), AgentDefinition.LANGUAGE_FRENCH),
            Map.entry(tdsrKey("fr", "social_context_sensitivity"), AgentDefinition.LANGUAGE_FRENCH),
            Map.entry(tdsrKey("fr", "rock_scissor_paper"), AgentDefinition.LANGUAGE_FRENCH),
            Map.entry(tdsrKey("fr", "tour_conversation"), AgentDefinition.LANGUAGE_FRENCH),
            Map.entry(tdsrKey("fr", "tour_conversation_social_context"), AgentDefinition.LANGUAGE_FRENCH),
            Map.entry(tdsrKey("it", "guessing_game_with_gestures"), AgentDefinition.LANGUAGE_ITALIAN),
            Map.entry(tdsrKey("it", "social_context_sensitivity"), AgentDefinition.LANGUAGE_ITALIAN),
            Map.entry(tdsrKey("it", "rock_scissor_paper"), AgentDefinition.LANGUAGE_ITALIAN),
            Map.entry(tdsrKey("it", "tour_conversation"), AgentDefinition.LANGUAGE_ITALIAN),
            Map.entry(tdsrKey("it", "tour_conversation_social_context"), AgentDefinition.LANGUAGE_ITALIAN),
            Map.entry(tdsrKey("en", "guessing_game_with_gestures"), AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry(tdsrKey("en", "social_context_sensitivity"), AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry(tdsrKey("en", "rock_scissor_paper"), AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry(tdsrKey("en", "tour_conversation"), AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry(tdsrKey("en", "tour_conversation_social_context"), AgentDefinition.LANGUAGE_ENGLISH),
            Map.entry(tdsrShhdKey("de", "epfl_active"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrShhdKey("de", "furka"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrShhdKey("de", "interviewing_people"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrShhdKey("de", "supsi_active"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry(tdsrShhdKey("de", "unis_student"), AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("elderlycare.therapy_appointment_reminder", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("elderlycare.guessing_game", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("elderlycare.guessing_game_user_guess", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("elderlycare.smart_goal_coaching", AgentDefinition.LANGUAGE_GERMAN));

    private static final Set<String> EXPECTED_LANGUAGELESS_KEYS = Set.of(
            tdsrKey("babylon", "guessing_game_with_gestures"),
            tdsrKey("babylon", "social_context_sensitivity"),
            tdsrKey("babylon", "rock_scissor_paper"),
            tdsrKey("babylon", "tour_conversation"),
            tdsrKey("babylon", "tour_conversation_social_context"));

    @Test
    void registryExposesExpectedUniqueDefinitionKeys() {
        AgentDefinitionRegistry registry = new AgentDefinitionRegistry();
        List<String> keys = registry.list().stream().map(AgentDefinition::key).toList();

        assertEquals(EXPECTED_KEYS, keys);
        assertEquals(EXPECTED_KEYS.size(), new HashSet<>(keys).size());
        assertEquals(EXPECTED_KEYS.size(), EXPECTED_LANGUAGE_BY_KEY.size() + EXPECTED_LANGUAGELESS_KEYS.size());
        for (String key : EXPECTED_KEYS) {
            assertTrue(registry.findByKey(key).isPresent(), "missing definition key " + key);
            assertTrue(EXPECTED_LANGUAGE_BY_KEY.containsKey(key) || EXPECTED_LANGUAGELESS_KEYS.contains(key),
                    "missing expected language contract for " + key);
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
            if (EXPECTED_LANGUAGELESS_KEYS.contains(definition.key())) {
                assertNull(definition.languageCode(), definition.key());
                assertNull(agent.getLanguageCode(), definition.key());
            } else {
                String expectedLanguage = EXPECTED_LANGUAGE_BY_KEY.get(definition.key());
                assertEquals(expectedLanguage, definition.languageCode(), definition.key());
                assertEquals(expectedLanguage, agent.getLanguageCode(), definition.key());
            }
            assertEquals(agent.getName(), definition.displayName(), definition.key());
            assertEquals(agent.getDescription(), definition.description(), definition.key());
        }
    }

    @Test
    void duplicateDefinitionKeysFailFast() {
        AgentDefinition duplicate = new ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame();

        assertThrows(IllegalArgumentException.class, () -> new AgentDefinitionRegistry(List.of(duplicate, duplicate)));
    }

    private static String tdsrKey(String language, String agentName) {
        return "tdsr.core." + language + "." + agentName;
    }

    private static String tdsrShhdKey(String language, String agentName) {
        return "tdsr.shhd." + language + "." + agentName;
    }
}
