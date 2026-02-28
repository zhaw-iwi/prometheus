package ch.zhaw.prometheus.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

class SseBroadcasterHardeningUnitTest {

    @Test
    void behaviourPublishUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        AgentBehaviourBroadcaster broadcaster = new AgentBehaviourBroadcaster();
        UUID agentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"hi\"}");
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = readField(broadcaster, "emittersByAgent");
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);
        emittersByAgent.put(agentId, emitters);

        assertDoesNotThrow(() -> broadcaster.publish(agentId, event));
        assertTrue(emitters.isEmpty());
        assertNull(emittersByAgent.get(agentId));
    }

    @Test
    void monitorPublishUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        AgentMonitorBroadcaster broadcaster = new AgentMonitorBroadcaster();
        UUID agentId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getCurrentState()).thenReturn(null);
        when(agent.getStorage()).thenReturn(Map.of());
        when(agent.getName()).thenReturn("agent");
        when(agent.getDescription()).thenReturn("desc");
        when(agent.isActive()).thenReturn(true);
        when(agent.listStates()).thenReturn(List.of());

        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = readField(broadcaster, "emittersByAgent");
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);
        emittersByAgent.put(agentId, emitters);

        assertDoesNotThrow(() -> broadcaster.publish(agent));
        assertTrue(emitters.isEmpty());
        assertNull(emittersByAgent.get(agentId));
    }

    @Test
    void logPublishUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        LogStreamBroadcaster broadcaster = new LogStreamBroadcaster();
        LogEvent event = new LogEvent(System.currentTimeMillis(), "INFO", "logger", "message");

        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        CopyOnWriteArrayList<SseEmitter> emitters = readField(broadcaster, "emitters");
        emitters.add(emitter);

        assertDoesNotThrow(() -> broadcaster.publish(event));
        assertTrue(emitters.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}

