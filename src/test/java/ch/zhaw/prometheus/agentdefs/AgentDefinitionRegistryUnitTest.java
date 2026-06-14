package ch.zhaw.prometheus.agentdefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

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
            "gigielderlycare.therapy_appointment_reminder",
            "gigielderlycare.guessing_game",
            "gigielderlycare.guessing_game_user_guess",
            "gigielderlycare.smart_goal_coaching");

    @Test
    void registryExposesExpectedUniqueDefinitionKeys() {
        AgentDefinitionRegistry registry = new AgentDefinitionRegistry();
        List<String> keys = registry.list().stream().map(AgentDefinition::key).toList();

        assertEquals(EXPECTED_KEYS, keys);
        assertEquals(EXPECTED_KEYS.size(), new HashSet<>(keys).size());
        for (String key : EXPECTED_KEYS) {
            assertTrue(registry.findByKey(key).isPresent(), "missing definition key " + key);
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
