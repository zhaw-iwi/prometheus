package ch.zhaw.prometheus.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** Volatile session ownership; never silently survives an application restart or reset. */
@Component
public class ExternalSpeechOwnership {
    private final ConcurrentHashMap<UUID, ExternalSpeech> owners = new ConcurrentHashMap<>();
    public void acquire(UUID agentId, ExternalSpeech owner) {
        if (owners.putIfAbsent(agentId, owner) != null) throw new LiveSessionUnavailableException(true);
    }
    public ExternalSpeech current(Agent agent) {
        ExternalSpeech owner = owners.get(agent.getId());
        if (owner != null && !owner.epoch().equals(agent.executionEpoch())) { owners.remove(agent.getId(), owner); return null; }
        return owner;
    }
    public void release(UUID agentId, UUID sessionId) {
        owners.computeIfPresent(agentId, (id, owner) -> owner.sessionId().equals(sessionId) ? null : owner);
    }
    public void revoke(UUID agentId) { owners.remove(agentId); }
}
