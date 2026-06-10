package ch.zhaw.prometheus.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ch.zhaw.prometheus.model.event.EventHistory;

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
    void behaviourHeartbeatUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        AgentBehaviourBroadcaster broadcaster = new AgentBehaviourBroadcaster();
        UUID agentId = UUID.fromString("abababab-abab-abab-abab-abababababab");
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("heartbeat failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = readField(broadcaster, "emittersByAgent");
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);
        emittersByAgent.put(agentId, emitters);

        assertDoesNotThrow(broadcaster::heartbeat);
        assertTrue(emitters.isEmpty());
        assertNull(emittersByAgent.get(agentId));
    }

    @Test
    void behaviourReplayReturnsLatestEventWhenCursorIsMissing() {
        Event first = eventWithId("11111111-1111-1111-1111-111111111111", "first");
        Event observation = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "user");
        Event second = eventWithId("22222222-2222-2222-2222-222222222222", "second");
        Agent agent = agentWithEvents(first, observation, second);

        List<Event> replay = AgentBehaviourBroadcaster.replayBehaviourEvents(agent, null);

        assertEquals(List.of(second), replay);
    }

    @Test
    void behaviourReplayReturnsEventsAfterKnownCursor() {
        Event first = eventWithId("33333333-3333-3333-3333-333333333333", "first");
        Event second = eventWithId("44444444-4444-4444-4444-444444444444", "second");
        Event third = eventWithId("55555555-5555-5555-5555-555555555555", "third");
        Agent agent = agentWithEvents(first, second, third);

        List<Event> replay = AgentBehaviourBroadcaster.replayBehaviourEvents(agent, first.getId().toString());

        assertEquals(List.of(second, third), replay);
    }

    @Test
    void behaviourReplayFallsBackToLatestEventWhenCursorIsUnknown() {
        Event first = eventWithId("66666666-6666-6666-6666-666666666666", "first");
        Event second = eventWithId("77777777-7777-7777-7777-777777777777", "second");
        Agent agent = agentWithEvents(first, second);

        List<Event> replay = AgentBehaviourBroadcaster.replayBehaviourEvents(agent,
                "88888888-8888-8888-8888-888888888888");

        assertEquals(List.of(second), replay);
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
    void monitorHeartbeatUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        AgentMonitorBroadcaster broadcaster = new AgentMonitorBroadcaster();
        UUID agentId = UUID.fromString("bcbcbcbc-bcbc-bcbc-bcbc-bcbcbcbcbcbc");
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("heartbeat failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByAgent = readField(broadcaster, "emittersByAgent");
        CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
        emitters.add(emitter);
        emittersByAgent.put(agentId, emitters);

        assertDoesNotThrow(broadcaster::heartbeat);
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

    @Test
    void logHeartbeatUnsubscribesFailedEmitterAndDoesNotThrow() throws Exception {
        LogStreamBroadcaster broadcaster = new LogStreamBroadcaster();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new AssertionError("heartbeat failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        CopyOnWriteArrayList<SseEmitter> emitters = readField(broadcaster, "emitters");
        emitters.add(emitter);

        assertDoesNotThrow(broadcaster::heartbeat);
        assertTrue(emitters.isEmpty());
    }

    private static Agent agentWithEvents(Event... events) {
        Agent agent = mock(Agent.class);
        EventHistory history = mock(EventHistory.class);
        when(history.toList()).thenReturn(List.of(events));
        when(agent.getEventHistory()).thenReturn(history);
        return agent;
    }

    private static Event eventWithId(String id, String speech) {
        Event event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"" + speech + "\"}");
        setId(event, UUID.fromString(id));
        return event;
    }

    private static void setId(Event event, UUID id) {
        try {
            Field field = Event.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(event, id);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}

