package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.application.live.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class CoreReceptionUnitTest {
    static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    static Event presence(int count, Instant at) { return Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
            "{\"humanCount\":" + count + ",\"ts\":\"" + at + "\"}"); }
    static CoreTaskPolicy policy(Agent agent) { return (CoreTaskPolicy) agent.getCurrentState().ownPolicy(); }
    static void put(Storage storage, String key, String value) { storage.put(key, new JsonPrimitive(value)); }
    static void task(Storage storage, boolean bound) {
        storage.put(TaskMemory.SPEC, JsonParser.parseString(CoreTaskVoiceUnitTest.SPEC.replace("\"sessionBound\":true", "\"sessionBound\":" + bound)));
        put(storage, TaskMemory.PHASE, "RUNNING"); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
        put(storage, TaskMemory.AFTER, Instant.EPOCH.toString());
    }
    @Test void lifecycleIsExplicitAndDoesNotChangeGenericOrDefaultSpace() {
        for (var agent : List.of(new GenericMultimodalBehaviour().createAgent(), new GenericMultimodalBehaviourAtSocialSpace().createAgent())) {
            assertFalse(agent.getInteractionProfile().isPresenceControlledLive());
            policy(agent).beginLiveConversation(new EventHistory());
            assertEquals(0, policy(agent).liveDepartureGraceMs()); assertEquals("", policy(agent).liveOpeningInstructions());
        }
        var agent = new GenericMultimodalBehaviourAtSocialSpace().createAgent(true);
        assertTrue(agent.getInteractionProfile().isPresenceControlledLive()); assertEquals(2000, policy(agent).liveDepartureGraceMs());
        assertTrue(policy(agent).liveOpeningInstructions().contains("never activates or resumes"));
        assertDoesNotThrow(() -> new LiveVoicePolicyAdapter().instructions(agent));
    }
    @Test void freshSourceOrderAndAbsenceAreCheckedBeforeAnyMutation() {
        var agent = new GenericMultimodalBehaviourAtSocialSpace().createAgent(true); var s = policy(agent).storage(); task(s, true);
        var events = new EventHistory(); events.appendEvent(presence(1, NOW.minusSeconds(16)));
        assertThrows(IllegalStateException.class, () -> CoreReception.begin(s, events, NOW)); assertEquals("RUNNING", TaskMemory.phase(s));
        events.appendEvent(presence(1, NOW)); events.appendEvent(presence(0, NOW.minusSeconds(3)));
        assertTrue(CoreReception.occupied(events, NOW), "Late old absence must not overwrite newer occupied evidence");
        events.appendEvent(presence(0, NOW)); assertFalse(CoreReception.occupied(events, NOW));
        events.appendEvent(presence(1, NOW.plusSeconds(3))); assertFalse(CoreReception.occupied(events, NOW));
        events.appendEvent(presence(1, NOW.plusMillis(1))); CoreReception.begin(s, events, NOW);
        assertEquals("PAUSED", TaskMemory.phase(s)); assertEquals(1, s.get(TaskMemory.ACTIONS).getAsInt());
    }
    @Test void encounterDropsOldDialogueAndInvitationsButKeepsAgreementAndWeatherAge() {
        var agent = new GenericMultimodalBehaviourAtSocialSpace().createAgent(true); var p = policy(agent); var s = p.storage();
        task(s, true); s.put(TaskMemory.DRAFT, s.get(TaskMemory.SPEC).deepCopy());
        put(s, CoreTaskVoice.INVITATION, NOW.toString()); put(s, CoreTaskVoice.SPOKEN_PROMPT, "old-invite");
        put(s, SensoryRemarks.CORRECTION, "old visitor correction");
        var events = agent.getEventHistory(); events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Previous visitor private dialogue"));
        events.appendEvent(Event.observation(Event.TYPE_WEATHER_CURRENT, "sensor", "{\"condition\":\"rain\",\"observed_at\":\"" + NOW.minusSeconds(500) + "\"}"));
        events.appendEvent(presence(1, NOW)); int size = events.toList().size();
        CoreReception.begin(s, events, NOW);
        assertEquals(size, p.liveHistoryStartIndex()); assertEquals(size, events.toList().size());
        assertEquals("PAUSED", TaskMemory.phase(s)); assertTrue(s.containsKey(TaskMemory.DRAFT)); assertTrue(s.containsKey(TaskMemory.SPEC));
        assertFalse(s.containsKey(CoreTaskVoice.INVITATION)); assertFalse(s.containsKey(SensoryRemarks.CORRECTION));
        assertFalse(p.liveResolvedControlReply("yes", events, new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch())));
        var snapshot = new LiveContextProjection(new PromptMessageAssembler(), new LiveVoicePolicyAdapter(), Clock.fixed(NOW, ZoneOffset.UTC)).project(agent);
        assertFalse(snapshot.startupInput().toString().contains("Previous visitor"));
        var weather = snapshot.items().stream().filter(i -> i.type().equals(Event.TYPE_WEATHER_CURRENT)).findFirst().orElseThrow();
        assertEquals(NOW.minusSeconds(500), weather.observedAt()); assertEquals(NOW.plusSeconds(100), weather.expiresAt());
        agent.reset(); assertEquals(0, p.liveHistoryStartIndex()); assertTrue(CoreReception.enabled(s));
    }
    @Test void autonomousTaskContinuesOnlyAfterItsPriorResponseWasConfirmed() {
        for (String status : List.of("PENDING", "CAPTURED", "STALE")) {
            boolean confirmed = status.equals("CAPTURED");
            var agent = new GenericMultimodalBehaviourAtSocialSpace().createAgent(true); var s = policy(agent).storage(); task(s, false);
            org.springframework.test.util.ReflectionTestUtils.setField(agent, "currentState", agent.reachableStates().stream()
                    .filter(state -> state.getName().equals("Generic multimodal execution")).findFirst().orElseThrow());
            var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
            TaskResponse.expect(s, owner, UUID.randomUUID(), "Task armed and waiting", NOW.minusSeconds(2));
            if (!status.equals("PENDING")) {
                var r = s.get(TaskResponse.KEY).getAsJsonObject().deepCopy(); r.addProperty("status", "CAPTURED");
                if (status.equals("STALE")) r.addProperty("revision", 99);
                s.put(TaskResponse.KEY, r);
            }
            agent.getEventHistory().appendEvent(presence(1, NOW)); CoreReception.begin(s, agent.getEventHistory(), NOW);
            assertEquals(confirmed ? "RUNNING" : "PAUSED", TaskMemory.phase(s)); assertEquals(1, s.get(TaskMemory.ACTIONS).getAsInt());
            if (!confirmed) { assertEquals("UNCERTAIN", TaskResponse.status(s)); continue; }
            assertFalse(s.containsKey(TaskResponse.KEY));
            var gateway = mock(LanguageModelGateway.class); when(gateway.infer(any())).thenReturn("{\"motion\":{\"handSign\":\"paper\"}}");
            var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
            agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{\"sign\":\"rock\",\"confidence\":1,\"ts\":\"" + Instant.now() + "\"}"), runtime);
            assertEquals(2, s.get(TaskMemory.ACTIONS).getAsInt(), "New owner must not leave an autonomous task stuck behind an old caption gate");
        }
    }
}
