package ch.zhaw.prometheus.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

@Component
public class AgentBehaviourBroadcaster {
    public static final String LIVE_EVENT_NAME = "behaviour-live";
    public static final String REPLAY_EVENT_NAME = "behaviour-replay";
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentBehaviourBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = new ConcurrentHashMap<>();
    private final AtomicLong sendFailureCount = new AtomicLong(0L);

    public SseEmitter subscribe(UUID agentId, Supplier<Optional<Agent>> lookup) {
        return this.subscribe(agentId, lookup, null);
    }

    public SseEmitter subscribe(UUID agentId, Supplier<Optional<Agent>> lookup, String lastEventId) {
        SseEmitter emitter = this.createEmitter();
        CopyOnWriteArrayList<SseEmitter> emitters = this.emittersByAgent.computeIfAbsent(agentId,
                id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> unsubscribe(agentId, emitters, emitter));
        emitter.onTimeout(() -> unsubscribe(agentId, emitters, emitter));
        emitter.onError(e -> unsubscribe(agentId, emitters, emitter));

        Optional<Agent> initial = lookup.get();
        if (initial.isPresent()) {
            for (Event event : replayBehaviourEvents(initial.get(), lastEventId)) {
                sendInitialBehaviour(agentId, emitters, emitter, event);
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

    @Scheduled(fixedDelayString = "${prometheus.sse.heartbeat.delay-ms:25000}")
    public void heartbeat() {
        for (var entry : this.emittersByAgent.entrySet()) {
            UUID agentId = entry.getKey();
            CopyOnWriteArrayList<SseEmitter> emitters = entry.getValue();
            for (SseEmitter emitter : emitters) {
                sendHeartbeat(agentId, emitters, emitter);
            }
        }
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(EMITTER_TIMEOUT_MS);
    }

    static List<Event> replayBehaviourEvents(Agent agent, String lastEventId) {
        List<Event> events = agent.getEventHistory().toList();
        List<Event> behaviourEvents = new ArrayList<>();
        for (Event current : events) {
            if (Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(current.getType())) {
                behaviourEvents.add(current);
            }
        }
        if (behaviourEvents.isEmpty()) {
            return List.of();
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            return List.of(behaviourEvents.get(behaviourEvents.size() - 1));
        }
        String cursor = lastEventId.trim();
        List<Event> replay = new ArrayList<>();
        boolean cursorFound = false;
        for (Event current : behaviourEvents) {
            if (!cursorFound) {
                if (cursor.equals(sseEventId(current))) {
                    cursorFound = true;
                }
                continue;
            }
            replay.add(current);
        }
        if (cursorFound) {
            return replay;
        }
        return List.of(behaviourEvents.get(behaviourEvents.size() - 1));
    }

    private void sendInitialBehaviour(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Event event) {
        try {
            emitter.send(behaviourEvent(event, REPLAY_EVENT_NAME));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribeAndComplete(agentId, emitters, emitter);
        }
    }

    private void sendBehaviour(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter, Event event) {
        try {
            emitter.send(behaviourEvent(event, LIVE_EVENT_NAME));
        } catch (Throwable failure) {
            this.recordSendFailure(agentId, failure);
            unsubscribeAndComplete(agentId, emitters, emitter);
        }
    }

    private void sendHeartbeat(UUID agentId, CopyOnWriteArrayList<SseEmitter> emitters, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("heartbeat"));
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

    private static SseEmitter.SseEventBuilder behaviourEvent(Event event, String eventName) {
        SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName).data(event);
        String id = sseEventId(event);
        if (id != null) {
            builder.id(id);
        }
        return builder;
    }

    private static String sseEventId(Event event) {
        if (event == null) {
            return null;
        }
        if (event.getId() != null) {
            return event.getId().toString();
        }
        if (event.getCreatedDate() != null) {
            return event.getCreatedDate().toString();
        }
        return null;
    }

    private void recordSendFailure(UUID agentId, Throwable failure) {
        long failures = this.sendFailureCount.incrementAndGet();
        if (failures == 1 || failures % 100 == 0) {
            LOGGER.debug("SSE behaviour send failed; agentId={}, failures={}", agentId, failures, failure);
        }
    }
}
