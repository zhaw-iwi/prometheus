package ch.zhaw.prometheus.application;

import java.util.UUID;

/** Identity-only notification; consumers must reload committed state and check the epoch. */
public record AgentCommitted(UUID agentId, UUID epoch, UUID narrationId, boolean activityKnown) {
    public AgentCommitted(UUID agentId, UUID epoch, UUID narrationId) { this(agentId, epoch, narrationId, true); }
    public AgentCommitted(UUID agentId, UUID epoch) { this(agentId, epoch, null, false); }
}
