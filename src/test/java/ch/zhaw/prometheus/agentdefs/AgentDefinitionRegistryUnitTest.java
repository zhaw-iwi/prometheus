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
            "basic.four_states_circular",
            "basic.four_states_linear",
            "basic.single_state_co_creation",
            "basic.single_state_guessing_game",
            "basic.single_state_micro_coaching",
            "multimodal.single_state_in",
            "multimodal.single_state_in_out",
            "multimodal.single_state_out");

    private static final Map<String, String> EXPECTED_LANGUAGE_BY_KEY = Map.ofEntries(
            Map.entry("basic.single_state_guessing_game", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_micro_coaching", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.single_state_co_creation", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_circular", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("basic.four_states_linear", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_out", AgentDefinition.LANGUAGE_GERMAN),
            Map.entry("multimodal.single_state_in_out", AgentDefinition.LANGUAGE_GERMAN));

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
        AgentDefinition duplicate = new ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame();

        assertThrows(IllegalArgumentException.class, () -> new AgentDefinitionRegistry(List.of(duplicate, duplicate)));
    }

    private static AgentDefinitionRegistry registryWithBuiltIns() {
        return new AgentDefinitionRegistry(List.of(
                new ch.zhaw.prometheus.agentdefs.basic.SingleStateGuessingGame(),
                new ch.zhaw.prometheus.agentdefs.basic.SingleStateMicroCoaching(),
                new ch.zhaw.prometheus.agentdefs.basic.SingleStateCoCreation(),
                new ch.zhaw.prometheus.agentdefs.basic.FourStatesCircular(),
                new ch.zhaw.prometheus.agentdefs.basic.FourStatesLinear(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalIn(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalOut(),
                new ch.zhaw.prometheus.agentdefs.multimodal.SingleStateMultimodalInOut()));
    }
}
