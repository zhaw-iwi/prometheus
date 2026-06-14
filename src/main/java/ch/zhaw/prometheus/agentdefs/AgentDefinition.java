package ch.zhaw.prometheus.agentdefs;

import ch.zhaw.prometheus.model.Agent;

public interface AgentDefinition {

    String key();

    Agent createAgent();

    default String displayName() {
        return this.createAgent().getName();
    }

    default String description() {
        return this.createAgent().getDescription();
    }

    default AgentCreationResult createInstance(AgentCreationContext context) {
        return AgentCreationResult.created(this.createAgent());
    }
}
