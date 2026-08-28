package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;

public class Aisha implements AgentDefinition {
    public static final String KEY = "tdsr.aisha.invest_qatar_qa";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_ARABIC;
    }

    @Override
    public Agent createAgent() {
        State conversation = new State(
                "Aisha Invest Qatar Q&A",
                new AishaCatalogPolicy(),
                List.of());
        Agent agent = new Agent(
                "Aisha - Invest Qatar",
                "Arabic Invest Qatar stage host for approved catalog questions, spoken replies, and semantic gestures.",
                conversation,
                new Storage());
        agent.setInteractionProfile(AishaInteractionProfiles.verbalCatalogQa());
        return this.applyDefinitionMetadata(agent);
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
