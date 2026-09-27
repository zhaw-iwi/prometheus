package ch.zhaw.prometheus.application;

import java.util.UUID;

/** Identity-only notification; consumers must reload committed state and check the epoch. */
public record AgentCommitted(UUID agentId, UUID epoch) {}
