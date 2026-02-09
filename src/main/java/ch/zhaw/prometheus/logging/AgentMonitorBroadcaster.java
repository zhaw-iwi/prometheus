package ch.zhaw.prometheus.logging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.controllers.views.AgentMonitorSnapshotView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;

@Component
public class AgentMonitorBroadcaster {
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID agentId, Supplier<Optional<Agent>> lookup) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> emitters = this.emittersByAgent.computeIfAbsent(agentId,
                id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        Optional<Agent> initial = lookup.get();
        if (initial.isPresent()) {
            sendSnapshot(emitter, initial.get());
        }
        return emitter;
    }

    public void publish(Agent agent) {
        if (agent == null || agent.getId() == null) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> emitters = this.emittersByAgent.get(agent.getId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendSnapshot(emitter, agent);
        }
    }

    private void sendSnapshot(SseEmitter emitter, Agent agent) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(toSnapshot(agent)));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private AgentMonitorSnapshotView toSnapshot(Agent agent) {
        State currentState = agent.getCurrentState();
        String stateName = currentState == null ? null : currentState.getName();
        String innerName = null;
        List<String> innerNames = List.of();
        if (currentState instanceof OuterState outerState && outerState.getInnerCurrent() != null) {
            innerName = outerState.getInnerCurrent().getName();
            innerNames = outerState.getInnerCurrentChain();
        }
        List<StorageEntryView> storageEntries = agent.getStorage().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map((entry) -> new StorageEntryView(entry.getKey(),
                        entry.getValue() == null ? "null" : entry.getValue().toString()))
                .toList();
        return new AgentMonitorSnapshotView(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.isActive(),
                stateName,
                innerName,
                innerNames,
                agent.listStates(),
                storageEntries);
    }
}
