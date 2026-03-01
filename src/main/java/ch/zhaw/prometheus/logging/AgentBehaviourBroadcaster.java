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

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

@Component
public class AgentBehaviourBroadcaster {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentBehaviourBroadcaster.class);

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
            Event latest = latestBehaviourEvent(initial.get());
            if (latest != null) {
                sendInitialBehaviour(agentId, emitters, emitter, latest);
            }
        }
        return emitter;
    }

    public void publish(UUID agentId, Event event) {
        if (agentId == null || event == null) {
            return;
        }
        if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) {
            return;
        }
        CopyOnWriteArrayList<SseEmitter> emitters = this.emittersByAgent.get(agentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendBehaviour(agentId, emitters, emitter, event);
        }
    }

    private static Event latestBehaviourEvent(Agent agent) {
        List<Event> events = agent.getEventHistory().toList();
        for (int i = events.size() - 1; i >= 0; i--) {
            Event current = events.get(i);
            if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(current.getType())) {
                return current;
            }
        }
        return null;
    }

    private void sendInitialBehaviour(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name("behaviour").data(event));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribeAndComplete(agentId, emitters, emitter);
        }
    }

    private void sendBehaviour(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name("behaviour").data(event));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribeAndComplete(agentId, emitters, emitter);
        }
    }

    private void unsubscribe(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter) {
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            this.emittersByAgent.remove(agentId, emitters);
        }
    }

    private void unsubscribeAndComplete(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter) {
        unsubscribe(agentId, emitters, emitter);
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Throwable ignored) {
        }
    }

    private void recordSendFailure(UUID agentId, Throwable failure) {
        long failures = this.sendFailureCount.incrementAndGet();
        if (failures == 1 || failures % 100 == 0) {
            LOGGER.debug("SSE behaviour send failed; agentId={}, failures={}", agentId, failures, failure);
        }
    }
}
