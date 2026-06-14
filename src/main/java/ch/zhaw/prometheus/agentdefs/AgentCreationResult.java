package ch.zhaw.prometheus.agentdefs;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

public record AgentCreationResult(Agent agent, Event starterEvent) {

    public AgentCreationResult {
        if (agent == null) {
            throw new IllegalArgumentException("agent must not be null");
        }
    }

    public static AgentCreationResult created(Agent agent) {
        return new AgentCreationResult(agent, null);
    }

    public static AgentCreationResult started(Agent agent, Event starterEvent) {
        return new AgentCreationResult(agent, starterEvent);
    }
}
