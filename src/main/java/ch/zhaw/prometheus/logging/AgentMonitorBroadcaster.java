package ch.zhaw.prometheus.logging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.controllers.views.AgentMonitorSnapshotView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;

@Component
public class AgentMonitorBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentMonitorBroadcaster.class);

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = new ConcurrentHashMap<>();
    private final AtomicLong sendFailureCount = new AtomicLong(0L);

    public SseEmitter subscribe(UUID agentId, Supplier<Optional<Agent>> lookup) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArrayList<SseEmitter> emitters = this.emittersByAgent.computeIfAbsent(agentId,
                id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> unsubscribe(agentId, emitters, emitter));
        emitter.onTimeout(() -> unsubscribe(agentId, emitters, emitter));
        emitter.onError(e -> unsubscribe(agentId, emitters, emitter));

        Optional<Agent> initial = lookup.get();
        if (initial.isPresent()) {
            sendInitialSnapshot(agentId, emitters, emitter, initial.get());
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
            sendSnapshot(agent.getId(), emitters, emitter, agent);
        }
    }

    private void sendInitialSnapshot(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Agent agent) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(toSnapshot(agent)));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribe(agentId, emitters, emitter);
        }
    }

    private void sendSnapshot(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Agent agent) {
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(toSnapshot(agent)));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribe(agentId, emitters, emitter);
        }
    }

    private void unsubscribe(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter) {
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            this.emittersByAgent.remove(agentId, emitters);
        }
    }

    private void recordSendFailure(UUID agentId, Throwable failure) {
        long failures = this.sendFailureCount.incrementAndGet();
        if (failures == 1 || failures % 100 == 0) {
            LOGGER.debug("SSE monitor send failed; agentId={}, failures={}", agentId, failures, failure);
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
