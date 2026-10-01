package ch.zhaw.prometheus.application;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** Volatile session ownership; never silently survives an application restart or reset. */
@Component
public class ExternalSpeechOwnership {
    private record Grant(ExternalSpeech owner, UUID scope, java.util.concurrent.atomic.AtomicBoolean input) {}
    private final ConcurrentHashMap<UUID, Grant> owners = new ConcurrentHashMap<>();
    private ch.zhaw.prometheus.repositories.AccessCodeAgentRepository links;
    @org.springframework.beans.factory.annotation.Autowired
    void scopes(ch.zhaw.prometheus.repositories.AccessCodeAgentRepository links) {
        this.links = links;
    }
    public void acquire(UUID agentId, ExternalSpeech owner) {
        acquire(agentId, owner, null);
    }
    public void acquire(UUID agentId, ExternalSpeech owner, UUID scope) {
        if (owners.putIfAbsent(agentId, new Grant(owner, scope, new java.util.concurrent.atomic.AtomicBoolean(true))) != null)
            throw new LiveSessionUnavailableException(true);
    }
    public boolean isCurrent(UUID id, ExternalSpeech owner) {
        Grant grant = owners.get(id);
        if (grant == null || !grant.owner.equals(owner)) return false;
        if (grant.scope != null && !links.existsByAccessCode_IdAndAccessCode_EnabledTrueAndAgent_Id(grant.scope, id)) {
            owners.remove(id, grant); return false;
        }
        return true;
    }
    public void pauseInput(UUID id, UUID session) {
        Grant grant = owners.get(id);
        if (grant != null && grant.owner.sessionId().equals(session)) grant.input.set(false);
    }
    public boolean acceptingInput(UUID id, ExternalSpeech owner) {
        Grant grant = owners.get(id);
        return grant != null && grant.input.get() && isCurrent(id, owner);
    }
    public ExternalSpeech current(Agent agent) {
        Grant grant = owners.get(agent.getId());
        if (grant == null) return null;
        if (!grant.owner.epoch().equals(agent.executionEpoch())) { owners.remove(agent.getId(), grant); return null; }
        return isCurrent(agent.getId(), grant.owner) ? grant.owner : null;
    }
    public void release(UUID agentId, UUID sessionId) {
        owners.computeIfPresent(agentId, (id, grant) -> grant.owner.sessionId().equals(sessionId) ? null : grant);
    }
    public void revoke(UUID agentId) { owners.remove(agentId); }
}
