package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionRpsPreparation;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.*;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest(properties = "prometheus.runtime.tick.enabled=false")
class GalaRoundLifecycleIntegrationTest {
    @Autowired AgentRepository agents;
    @Autowired AgentApplicationService turns;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired LiveSpeechCueService cues;
    @Autowired LiveAgentContextService contexts;
    @Autowired ExternalSpeechOwnership ownership;
    @MockitoBean LanguageModelGateway gateway;
    private UUID id;
    private ExternalSpeech owner;

    @BeforeEach void setup() {
        var agent = new GalaDinnerReceptionRpsPreparation().createAgent();
        var memory = (ch.zhaw.prometheus.model.Storage) org.springframework.test.util.ReflectionTestUtils.getField(agent, "storage");
        memory.put("gala.count", new JsonPrimitive("1"));
        memory.put("gala.seen_at", new JsonPrimitive(Instant.now().toString()));
        memory.put("gala.rps_prepared", new JsonPrimitive("true"));
        owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        id = agents.saveAndFlush(agent).getId(); ownership.acquire(id, owner);
        when(gateway.infer(any())).thenReturn("{\"operation\":\"START_RPS\",\"gesture\":\"NONE\",\"expression\":\"attentive\",\"gaze\":\"toward_user\"}");
    }
    @AfterEach void release() { ownership.release(id, owner.sessionId()); }
    @Test void delayedInvitationAdmitsTheBufferedYesOnceEvenWithAPrecedingStatePath() {
        finishRound();
        var input = segment("yes", Speaker.USER, "Ja", 1600, 1800, Instant.now());
        assertTrue(ingress.receipt(id, owner, input.fragments().getFirst()));
        var previousState = List.of("Gala rock-scissor-paper");
        assertEquals("WAITING_CONTEXT", ingress.commit(id, owner, input, previousState).orElseThrow().status());
        var invitation = segment("invite", Speaker.ASSISTANT, "Du gewinnst. Noch eine Runde?", 1000, 1500,
                Instant.ofEpochMilli(input.firstReceivedMs()).minusMillis(100));
        assertTrue(ingress.receipt(id, owner, invitation.fragments().getFirst()));
        assertEquals("COMPLETE", ingress.commit(id, owner, invitation, List.of("Gala reception")).orElseThrow().status());
        assertEquals("RPS_INTRO", mode());
        assertEquals("COMPLETE", ingress.commit(id, owner, input, previousState).orElseThrow().status());
        assertEquals("2", loaded().getStorage().get("gala.rps_round").getAsString());
        assertEquals(1, loaded().getEventHistory().toList().stream().filter(event -> Event.TYPE_USER_UTTERANCE.equals(event.getType()) && "Ja".equals(event.getPayload())).count());
    }
    @Test void aLaterInvitationCannotRetroactivelyAuthorizeAnEarlierYes() {
        finishRound();
        var input = segment("early-yes", Speaker.USER, "Ja", 1100, 1200, Instant.now());
        ingress.receipt(id, owner, input.fragments().getFirst());
        assertEquals("WAITING_CONTEXT", ingress.commit(id, owner, input, List.of("Gala reception")).orElseThrow().status());
        var invitation = segment("late-invite", Speaker.ASSISTANT, "Noch eine Runde?", 1300, 1500,
                Instant.ofEpochMilli(input.firstReceivedMs()).plusMillis(1));
        ingress.receipt(id, owner, invitation.fragments().getFirst());
        ingress.commit(id, owner, invitation, List.of("Gala reception"));
        assertEquals("CLARIFICATION", ingress.commit(id, owner, input, List.of("Gala reception")).orElseThrow().status());
        assertEquals("RECEPTION", mode()); assertEquals("1", loaded().getStorage().get("gala.rps_round").getAsString());
    }
    @Test void stopSupersedesABufferedReplyAndLateCallbacksCannotRestartTheGame() {
        finishRound();
        var input = segment("waiting-yes", Speaker.USER, "Ja", 1600, 1800, Instant.now());
        ingress.receipt(id, owner, input.fragments().getFirst()); ingress.commit(id, owner, input, List.of("Gala reception"));
        var stop = segment("stop", Speaker.USER, "Stopp", 2000, 2200, Instant.now());
        ingress.receipt(id, owner, stop.fragments().getFirst()); ingress.commit(id, owner, stop, List.of("Gala reception"));
        assertEquals("OBSOLETE", ingress.commit(id, owner, input, List.of("Gala reception")).orElseThrow().status());
        var invitation = segment("obsolete-invite", Speaker.ASSISTANT, "Noch eine Runde?", 1000, 1500, Instant.now());
        ingress.receipt(id, owner, invitation.fragments().getFirst()); ingress.commit(id, owner, invitation, List.of("Gala reception"));
        assertEquals("RECEPTION", mode()); assertFalse(loaded().getStorage().containsKey("gala.replay_invitation"));
    }
    @Test void missingCockpitCueExpiresDuringLiveRefreshWithRuntimeTicksDisabled() {
        turns.acknowledge(id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Schere Stein Papier bitte"), OutputProfile.FULL_PLAN);
        assertEquals("RPS_INTRO", mode());
        var agent = loaded();
        var memory = (ch.zhaw.prometheus.model.Storage) org.springframework.test.util.ReflectionTestUtils.getField(agent, "storage");
        var lateCue = new LiveSpeechCueService.Request(UUID.fromString(memory.get("gala.countdown_id").getAsString()),
                UUID.fromString(memory.get("gala.round_id").getAsString()), "complete", List.of("late"));
        var countdown = contexts.refresh(id, owner).orElseThrow().context();
        assertEquals(Instant.parse(memory.get("gala.countdown_at").getAsString()).plusSeconds(30), countdown.activity().refreshAt());
        assertEquals("RPS_INTRO", mode());
        memory.put("gala.countdown_at", new JsonPrimitive(Instant.now().minusSeconds(31).toString()));
        agents.saveAndFlush(agent);
        assertEquals(List.of("Gala reception"), contexts.refresh(id, owner).orElseThrow().context().statePath());
        assertEquals("RECEPTION", mode());
        assertFalse(loaded().getStorage().containsKey("gala.rps_capture_id"));
        assertFalse(cues.accept(id, owner, lateCue));
        contexts.refresh(id, owner).orElseThrow();
        var events = loaded().getEventHistory().toList();
        assertEquals(1, events.stream().filter(e -> e.getType().equals("sys.gala.speech_cue")).count());
        assertEquals(0, events.stream().filter(e -> e.getType().equals(Event.TYPE_SYSTEM_TICK)).count());
        assertTrue(events.stream().filter(e -> e.getType().equals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN))
                .noneMatch(e -> e.getPayload().contains("handSign")));
    }
    @Test void explanatoryMentionAndRepeatedCountdownStillOpenCaptureAndCompleteOneRound() {
        finishRound("Nach meinem Countdown Schere, Stein, Papier – Schere, Stein, Papier!");
        assertEquals("RECEPTION", mode());
        assertEquals(1, loaded().getEventHistory().toList().stream()
                .filter(e -> e.getType().equals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN) && e.getPayload().contains("handSign")).count());
    }
    private void finishRound() { finishRound("Schere, Stein, Papier!"); }
    private void finishRound(String countdown) {
        turns.acknowledge(id, new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Schere Stein Papier bitte"), OutputProfile.FULL_PLAN);
        assertEquals("RPS_INTRO", mode());
        var memory = loaded().getStorage();
        var activity = contexts.refresh(id, owner).orElseThrow().context().activity();
        assertEquals("Schere, Stein, Papier.", activity.continuation().requiredSpeech());
        assertEquals(memory.get("gala.countdown_id").getAsString(), activity.continuation().id().toString());
        assertFalse(activity.facts().contains("winner"));
        String receipt = "count-" + UUID.randomUUID();
        assertTrue(ingress.receipt(id, owner, new Fragment(receipt, Speaker.ASSISTANT, 100L, 500L, Instant.now().toEpochMilli(), 1, countdown)));
        var request = new LiveSpeechCueService.Request(UUID.fromString(memory.get("gala.countdown_id").getAsString()),
                UUID.fromString(memory.get("gala.round_id").getAsString()), "complete", List.of(receipt));
        assertTrue(cues.accept(id, owner, request)); assertEquals("RPS", mode());
        assertFalse(cues.accept(id, owner, request)); // One cue cannot dispatch another hand command.
        String capture = loaded().getStorage().get("gala.rps_capture_id").getAsString();
        turns.acknowledge(id, new EventRequest(Event.TYPE_HAND_SIGN, "sensor", Event.KIND_OBSERVATION,
                "{\"captureId\":\"" + capture + "\",\"sign\":\"paper\",\"confidence\":0.99,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        assertEquals("RECEPTION", mode());
        // Leave room for deterministic synthetic receipt ordering without sleeps.
        var agent = loaded(); agent.getStorage().get("gala.completed_round").getAsJsonObject().addProperty("at", Instant.now().minusSeconds(1).toString());
        agents.saveAndFlush(agent);
    }
    private Segment segment(String receipt, Speaker speaker, String text, long start, long end, Instant received) {
        var fragment = new Fragment(receipt, speaker, start, end, received.toEpochMilli(), start, text);
        return new Segment(UUID.randomUUID(), speaker, List.of(fragment), Closure.COMPLETE, "observed_silence", fragment.receivedMs(), fragment.receivedMs());
    }
    private Agent loaded() { return agents.findById(id).orElseThrow(); }
    private String mode() { return loaded().getStorage().get("gala.mode").getAsString(); }
}
