package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.google.gson.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;

class LiveCommittedSpeechServiceUnitTest {
    final UUID id = UUID.randomUUID(), response = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-12T12:00:00Z");
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    final Storage storage = new Storage();
    final CoordinationPolicy policy = new CoordinationPolicy(storage, "Persona");
    final Agent agent = mock(Agent.class);
    final AgentRepository agents = mock(AgentRepository.class);
    final ExternalSpeechOwnership ownership = mock(ExternalSpeechOwnership.class);
    final SpeechSynthesisGateway gateway = mock(SpeechSynthesisGateway.class);
    final LiveSpeechApprovalService.Window window = new LiveSpeechApprovalService.Window();
    final Clock clock = mock(Clock.class);
    final EventHistory history = new EventHistory();
    LiveCommittedSpeechService service;
    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        var turns = mock(AgentApplicationService.class);
        when(turns.serialized(eq(id), any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(1)).get());
        var tx = mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        service = new LiveCommittedSpeechService(turns, agents, ownership, gateway, tx, mock(AgentBehaviourBroadcaster.class),
                mock(org.springframework.context.ApplicationEventPublisher.class), clock);
        when(agent.getId()).thenReturn(id); when(agents.findById(id)).thenReturn(Optional.of(agent));
        var state = mock(State.class); when(agent.getCurrentState()).thenReturn(state); when(state.ownPolicy()).thenReturn(policy);
        when(ownership.current(agent)).thenReturn(owner); when(ownership.taskWorkAllowed(id, owner)).thenReturn(true);
        when(agent.getEventHistory()).thenReturn(history);
        when(agent.recordExternalSpeech(anyString(), any())).thenAnswer(call -> {
            var event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                    ch.zhaw.prometheus.model.behaviour.BehaviourPlan.speechOnly(call.getArgument(0)).toJson());
            event.speechProvenance(call.getArgument(1)); return history.appendEvent(event);
        });
        storage.put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        storage.put(LiveCommittedSpeech.ENABLED, new JsonPrimitive(true));
        var value = new JsonObject(); value.addProperty("id", response.toString()); value.addProperty("session", owner.sessionId().toString());
        value.addProperty("epoch", owner.epoch().toString()); value.addProperty("revision", 0);
        value.addProperty("created", now.minusSeconds(5).toString()); value.addProperty("expires", now.plusSeconds(40).toString());
        value.addProperty("facts", "Confirm that the task is stopped."); value.addProperty("spokenText", "The task is stopped.");
        storage.put("core.task.response", value);
        when(gateway.synthesize(anyString(), eq("alloy"), eq(1.0), eq(SpeechAudioFormat.PCM)))
                .thenAnswer(call -> new SpeechAudio(new byte[48000], SpeechAudioFormat.PCM.contentType()));
    }
    @AfterEach void close() { service.close(); }
    LiveCommittedSpeechService.Result synthesize() { return service.synthesize(id, owner, window, response); }
    boolean release(LiveCommittedSpeechService.Result result) { return service.validate(id, owner, window, response, result.segmentId(), false); }
    boolean played(LiveCommittedSpeechService.Result result) { return service.validate(id, owner, window, response, result.segmentId(), true); }
    @Test void synthesisReleaseAndPlaybackAreSeparateAndNoLostAcknowledgementCanReplay() {
        var result = synthesize(); assertEquals("APPROVED", result.status()); assertEquals("The task is stopped.", result.text());
        Event event = history.toList().getLast(); assertEquals(SpeechProvenance.Origin.SYNTHESIZED, event.speechProvenance().origin());
        assertNull(ConversationProjection.speech(event)); assertFalse(played(result));
        assertEquals("DISCARDED", synthesize().status());
        assertTrue(release(result)); assertFalse(release(result)); assertNull(ConversationProjection.speech(event));
        assertEquals("DISCARDED", service.synthesize(id, owner, new LiveSpeechApprovalService.Window(), response).status());
        assertFalse(service.validate(id, owner, new LiveSpeechApprovalService.Window(), response, result.segmentId(), false));
        assertTrue(played(result)); assertFalse(played(result)); assertEquals(result.text(), ConversationProjection.speech(event));
        verify(gateway, times(1)).synthesize(eq(result.text()), eq("alloy"), eq(1.0), eq(SpeechAudioFormat.PCM));
    }
    @ParameterizedTest @ValueSource(strings = {"input", "revision", "owner", "deadline", "closed"})
    void authorityIsRecheckedAfterProviderWork(String change) {
        when(gateway.synthesize(anyString(), anyString(), anyDouble(), any())).thenAnswer(call -> {
            switch (change) {
                case "input" -> window.interrupt(now);
                case "revision" -> storage.put(TaskMemory.REVISION, new JsonPrimitive(1));
                case "owner" -> when(ownership.current(agent)).thenReturn(null);
                case "deadline" -> when(clock.instant()).thenReturn(now.plusSeconds(41));
                case "closed" -> window.close();
            }
            return new SpeechAudio(new byte[48000], "audio/pcm");
        });
        assertNotEquals("APPROVED", synthesize().status()); assertTrue(history.toList().isEmpty());
    }
    @Test void interruptionAfterReleaseNeverBecomesConversationOrTaskCompletion() {
        var result = synthesize(); assertTrue(release(result)); window.interrupt(now);
        assertFalse(played(result)); assertNull(ConversationProjection.speech(history.toList().getLast()));
        assertEquals("DISCARDED", synthesize().status());
    }
    @Test void aTransientRemarkMustReleaseBeforeItsFreshnessDeadlineButMayFinishWithinTheOriginalPlaybackWindow() {
        storage.remove("core.task.response");
        var p = new JsonObject(); p.addProperty("id", response.toString()); p.addProperty("session", owner.sessionId().toString());
        p.addProperty("epoch", owner.epoch().toString()); p.addProperty("revision", 0); p.addProperty("created", now.minusSeconds(1).toString());
        p.addProperty("expires", now.plusSeconds(5).toString()); p.addProperty("facts", "A current count observation");
        p.addProperty("spokenText", "Two people are visible."); storage.put(ch.zhaw.prometheus.model.behaviour.SensoryRemarks.PENDING, p);
        var result = synthesize(); assertEquals("APPROVED", result.status()); assertTrue(release(result));
        when(clock.instant()).thenReturn(now.plusSeconds(6)); assertTrue(played(result));
        assertFalse(storage.containsKey(ch.zhaw.prometheus.model.behaviour.SensoryRemarks.PENDING));
        assertFalse(storage.containsKey("core.task.spokenPrompt")); assertFalse(storage.containsKey(TaskResponse.KEY));
    }
    @ParameterizedTest @ValueSource(strings = {"failure", "odd", "large", "wrong-format", "cannot-finish"})
    void invalidOrLateAudioIsWithheldWithoutAnotherPaidAttempt(String kind) {
        when(gateway.synthesize(anyString(), anyString(), anyDouble(), any())).thenAnswer(call -> {
            if (kind.equals("failure")) throw new SpeechSynthesisException("Synthetic failure");
            if (kind.equals("cannot-finish")) when(clock.instant()).thenReturn(now.plusSeconds(39));
            return new SpeechAudio(new byte[kind.equals("odd") ? 3 : kind.equals("large") ? LiveCommittedSpeechService.MAX_BYTES + 2
                    : kind.equals("cannot-finish") ? 48000 * 20 : 48000], kind.equals("wrong-format") ? "audio/mpeg" : "audio/pcm");
        });
        assertNotEquals("APPROVED", synthesize().status()); assertEquals("DISCARDED", synthesize().status());
        assertTrue(history.toList().isEmpty()); verify(gateway, times(1)).synthesize(anyString(), anyString(), anyDouble(), any());
    }
    @Test void aTaskContributionIsAssociatedWithoutSpendingAnotherContributionButWaitsForPlayback() {
        storage.put(TaskMemory.PHASE, new JsonPrimitive("RUNNING")); storage.put(TaskMemory.ACTIONS, new JsonPrimitive(1));
        TaskResponse.expect(storage, owner, response, "The task is stopped.", now.minusSeconds(5));
        var result = synthesize(); assertEquals("APPROVED", result.status()); assertTrue(TaskResponse.captured(storage, owner));
        assertTrue(TaskResponse.playbackPending(storage)); assertTrue(release(result)); assertTrue(played(result));
        assertFalse(TaskResponse.playbackPending(storage)); assertEquals(1, storage.get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(1, TaskResponse.firstSample(storage, history.toList(), owner));
    }
}
