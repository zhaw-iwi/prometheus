package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class AishaDefinitionContractTest {

    @Test
    void definitionExposesArabicCatalogAgentContract() {
        Aisha definition = new Aisha();
        Agent agent = definition.createAgent();
        AgentInteractionProfile profile = agent.getInteractionProfile();

        assertEquals("tdsr.aisha.invest_qatar_qa", definition.key());
        assertEquals(AgentDefinition.LANGUAGE_ARABIC, definition.languageCode());
        assertEquals(AgentDefinition.LANGUAGE_ARABIC, agent.getLanguageCode());
        assertEquals(List.of("tdsr", "aisha"), definition.packagePath());
        assertEquals(List.of(AgentInteractionProfile.OBS_USER_UTTERANCE),
                profile.getSupportedObservations());
        assertEquals(List.of(
                AgentInteractionProfile.MODALITY_SPEECH,
                AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE),
                profile.getSupportedBehaviourModalities());
        assertEquals(List.of(
                AishaInteractionProfiles.TAG_AISHA,
                AishaInteractionProfiles.TAG_INVEST_QATAR,
                AishaInteractionProfiles.TAG_CATALOG_QA),
                profile.getProfileTags());
        assertTrue(agent.isActive());
    }

    @Test
    void instanceStartsWithPersistableArabicBehaviourPlan() {
        Aisha definition = new Aisha();
        AgentCreationResult result = definition.createInstance(new AgentCreationContext(
                new PromptMessageAssembler(), new NoOpGateway()));

        assertNotNull(result.agent());
        assertNotNull(result.starterEvent());
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, result.starterEvent().getType());
        BehaviourPlan plan = BehaviourPlan.fromJson(result.starterEvent().getPayload());
        assertEquals(AishaCatalogPolicy.GREETING, plan.getSpeech());
        assertEquals("POLITE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertEquals(1, result.agent().getEventHistory().toList().size());
    }

    private static final class NoOpGateway implements LanguageModelGateway {
        @Override
        public String complete(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return false;
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            return null;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return null;
        }
    }
}
