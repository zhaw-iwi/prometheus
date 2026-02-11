package ch.zhaw.prometheus.logging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

@Component
public class AgentBehaviourBroadcaster {
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
            Event latest = latestBehaviourEvent(initial.get());
            if (latest != null) {
                sendBehaviour(emitter, latest);
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
            sendBehaviour(emitter, event);
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

    private void sendBehaviour(SseEmitter emitter, Event event) {
        try {
            emitter.send(SseEmitter.event().name("behaviour").data(event));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
