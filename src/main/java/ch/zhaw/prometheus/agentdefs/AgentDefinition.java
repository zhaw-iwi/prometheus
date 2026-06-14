package ch.zhaw.prometheus.agentdefs;

import ch.zhaw.prometheus.model.Agent;

public interface AgentDefinition {

    String LANGUAGE_ENGLISH = "en";
    String LANGUAGE_GERMAN = "de";

    String key();

    Agent createAgent();

    default String languageCode() {
        return null;
    }

    default Agent applyDefinitionMetadata(Agent agent) {
        if (agent != null && !isPresent(agent.getLanguageCode()) && isPresent(this.languageCode())) {
            agent.setLanguageCode(this.languageCode());
        }
        return agent;
    }

    default String displayName() {
        return this.createAgent().getName();
    }

    default String description() {
        return this.createAgent().getDescription();
    }

    default AgentCreationResult createInstance(AgentCreationContext context) {
        return AgentCreationResult.created(this.applyDefinitionMetadata(this.createAgent()));
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
