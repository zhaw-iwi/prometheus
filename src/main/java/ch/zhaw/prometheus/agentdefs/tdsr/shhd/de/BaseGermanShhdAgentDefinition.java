package ch.zhaw.prometheus.agentdefs.tdsr.shhd.de;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdAgentFactory;
import ch.zhaw.prometheus.model.Agent;

abstract class BaseGermanShhdAgentDefinition implements AgentDefinition {
    private final String key;
    private final String agentName;
    private final String agentDescription;
    private final String stateName;
    private final TdsrShhdAgentFactory.ShhdPrompts prompts;

    BaseGermanShhdAgentDefinition(String key, String agentName, String agentDescription, String stateName,
            TdsrShhdAgentFactory.ShhdPrompts prompts) {
        this.key = key;
        this.agentName = agentName;
        this.agentDescription = agentDescription;
        this.stateName = stateName;
        this.prompts = prompts;
    }

    @Override
    public final String key() {
        return this.key;
    }

    @Override
    public final String languageCode() {
        return LANGUAGE_GERMAN;
    }

    @Override
    public final Agent createAgent() {
        return this.applyDefinitionMetadata(TdsrShhdAgentFactory.socialTourAgent(
                this.prompts,
                this.agentName,
                this.agentDescription,
                this.stateName,
                this.stateName + " Abschluss",
                "outcome"));
    }

    @Override
    public final AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
