package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.*;

class CoreRpsRoundUnitTest {
    static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    final Storage storage = new Storage();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    final PolicyRuntime live = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);
    static Event sign(String id, Instant at, double confidence) {
        return Event.observation(Event.TYPE_HAND_SIGN, "sensor", "{\"captureId\":\""+id+"\",\"sign\":\"rock\",\"confidence\":"+confidence+",\"ts\":\""+at+"\"}");
    }
    Event speech(String words) {
        var event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", BehaviourPlan.speechOnly(words).toJson());
        event.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                "countdown", SpeechProvenance.Association.NONE, List.of(), true)); return event;
    }
    static EventHistory history(Event... events) { var h = new EventHistory(); for (var e : events) h.appendEvent(e); return h; }
    void start(boolean countdown) { storage.put(CoreRpsRound.COUNTDOWN, new JsonPrimitive(countdown)); CoreRpsRound.start(storage, owner, NOW); }
    String id() { return CoreRpsRound.round(storage).get("id").getAsString(); }
    @Test void countdownRequiresCurrentCompleteSpeechAndPlaybackBeforeOpeningCapture() {
        start(true); String id = id();
        assertEquals("INTRO", CoreRpsRound.phase(storage)); assertFalse(CoreRpsRound.display(storage).get("awaitingUserSign").getAsBoolean());
        assertEquals("rock", storage.get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());
        assertEquals("rps_not_ready", CoreRpsRound.rejection(storage, history(sign(id, NOW, 1)), owner, NOW));
        assertFalse(CoreRpsRound.captured(storage, speech("One, two, three"), owner, NOW.minusSeconds(1), NOW, List.of()));
        assertFalse(CoreRpsRound.captured(storage, speech("One, two, three"), new ExternalSpeech(UUID.randomUUID(), owner.epoch()), NOW, NOW, List.of()));
        var partial = speech("One, two, three"); partial.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), "partial", SpeechProvenance.Association.NONE, List.of(), false));
        assertFalse(CoreRpsRound.captured(storage, partial, owner, NOW, NOW, List.of()));
        assertTrue(CoreRpsRound.captured(storage, speech("Show one hand. One, two, three."), owner, NOW, NOW.plusSeconds(2), List.of()));
        assertEquals("countdown", CoreRpsRound.cue(storage, NOW.plusSeconds(2)).segmentId());
        assertFalse(CoreRpsRound.canOpen(storage, UUID.randomUUID().toString(), owner, NOW.plusSeconds(2)));
        assertTrue(CoreRpsRound.canOpen(storage, id, owner, NOW.plusSeconds(2)));
        CoreRpsRound.open(storage, NOW.plusSeconds(3));
        assertFalse(CoreRpsRound.canOpen(storage, id, owner, NOW.plusSeconds(4))); assertNull(CoreRpsRound.cue(storage, NOW.plusSeconds(4)));
        assertEquals("rps_stale_sign", CoreRpsRound.rejection(storage, history(sign(id, NOW.plusSeconds(2), 1)), owner, NOW.plusSeconds(4)));
        assertNull(CoreRpsRound.rejection(storage, history(sign(id, NOW.plusSeconds(4), .8)), owner, NOW.plusSeconds(4)));
        verifyNoInteractions(gateway);
    }
    @Test void freshnessConfidenceOrderAndCameraStabilityAreIndependentOfCaptureIdentity() {
        start(false); var now = NOW.plusSeconds(5);
        assertEquals("rps_wrong_capture", CoreRpsRound.rejection(storage, history(sign("old", now, 1)), owner, now));
        assertEquals("rps_wrong_capture", CoreRpsRound.rejection(storage, history(sign(id(), now, 1)), new ExternalSpeech(UUID.randomUUID(), owner.epoch()), now));
        assertEquals("rps_stale_sign", CoreRpsRound.rejection(storage, history(sign(id(), NOW.minusMillis(1), 1)), owner, now));
        assertEquals("rps_stale_sign", CoreRpsRound.rejection(storage, history(sign(id(), now.plusSeconds(3), 1)), owner, now));
        assertEquals("rps_stale_sign", CoreRpsRound.rejection(storage, history(sign(id(), NOW, 1)), owner, NOW.plusSeconds(15)));
        assertEquals("rps_low_confidence", CoreRpsRound.rejection(storage, history(sign(id(), now, .69)), owner, now));
        assertEquals("rps_low_confidence", CoreRpsRound.rejection(storage, history(sign(id(), now, 1.1)), owner, now));
        assertEquals("rps_out_of_order", CoreRpsRound.rejection(storage, history(sign(id(), now, .1), sign(id(), now.minusMillis(1), 1)), owner, now));
        var data = JsonParser.parseString(sign(id(), now, .9).getPayload()).getAsJsonObject(); data.addProperty("source", "valerian.hand.camera");
        data.addProperty("stabilityFrames", 2);
        assertEquals("rps_unstable_sign", CoreRpsRound.rejection(storage, history(Event.observation(Event.TYPE_HAND_SIGN, "sensor", data.toString())), owner, now));
        data.addProperty("stabilityFrames", 3);
        assertNull(CoreRpsRound.rejection(storage, history(Event.observation(Event.TYPE_HAND_SIGN, "sensor", data.toString())), owner, now));
        assertEquals("rps_invalid_sign", CoreRpsRound.rejection(storage, history(Event.observation(Event.TYPE_HAND_SIGN, "sensor", "broken")), owner, now));
    }
    @Test void repeatedRoundsOwnNewCaptureAndKeepBoundedResultsWithoutReplayingAnOldSign() {
        start(false); String first = id(); var events = history(sign(first, NOW.plusSeconds(1), 1));
        CoreRpsRound.result(storage, events, live, NOW.plusSeconds(1));
        assertEquals("draw", storage.get(RpsStorageKeys.LAST_ROUND).getAsJsonObject().get("winner").getAsString());
        CoreRpsRound.result(storage, events, live, NOW.plusSeconds(2));
        assertEquals(1, storage.get(CoreRpsRound.COUNT).getAsInt());
        for (int round = 2; round <= 20; round++) {
            var time = NOW.plusSeconds(round * 2); CoreRpsRound.start(storage, owner, time);
            assertNotEquals(first, id()); assertFalse(storage.containsKey(RpsStorageKeys.LAST_ROUND));
            assertEquals("rps_wrong_capture", CoreRpsRound.rejection(storage, history(sign(first, time, 1)), owner, time));
            CoreRpsRound.result(storage, history(sign(id(), time, 1)), live, time);
            assertEquals(round, storage.get(CoreRpsRound.COUNT).getAsInt());
        }
        assertEquals(16, storage.get(RpsStorageKeys.ROUNDS).getAsJsonArray().size());
        CoreRpsRound.reset(storage); assertFalse(storage.containsKey(CoreRpsRound.KEY)); assertFalse(storage.containsKey(RpsStorageKeys.LAST_ROUND));
        assertTrue(storage.containsKey(CoreRpsRound.COUNTDOWN)); verifyNoInteractions(gateway);
    }
    @Test void expiredAndEndedPreparationHasNoResultAndCannotAcceptDelayedPlayback() {
        start(true); String old = id();
        assertTrue(CoreRpsRound.expired(storage, NOW.plusSeconds(30))); assertNull(CoreRpsRound.cue(storage, NOW.plusSeconds(30)));
        assertFalse(CoreRpsRound.captured(storage, speech("One, two, three"), owner, NOW, NOW.plusSeconds(30), List.of()));
        CoreRpsRound.close(storage, "ABORTED", null, NOW.plusSeconds(30));
        assertTrue(CoreRpsRound.live(CoreRpsRound.round(storage)), "An ended voice session must not create backend speech");
        assertFalse(storage.containsKey(RpsStorageKeys.LAST_ROUND));
        CoreRpsRound.start(storage, owner, NOW.plusSeconds(31)); assertNotEquals(old, id());
        assertFalse(CoreRpsRound.canOpen(storage, old, owner, NOW.plusSeconds(32)));
    }
    static LanguageModelGateway controls() {
        var gateway = mock(LanguageModelGateway.class);
        when(gateway.decide(any())).thenAnswer(call -> {
            List<PromptMessage> messages = call.getArgument(0);
            String system = messages.getFirst().getContent();
            return system.contains("clearly ready to start a round") || system.contains("wants to play another round");
        });
        when(gateway.infer(any())).thenAnswer(call -> {
            InferenceRequest request = call.getArgument(0);
            if (request.purpose() != InferencePurpose.DECISION) return "{\"speech\":\"Ready for a round?\"}";
            String system = request.messages().getFirst().getContent();
            return Boolean.toString(system.contains("clearly ready to start a round") || system.contains("wants to play another round"));
        }); return gateway;
    }
    static Storage storage(Agent agent) { return agent.reachableStates().stream().map(State::ownPolicy).filter(CoreRpsActivityPolicy.class::isInstance)
            .map(CoreRpsActivityPolicy.class::cast).findFirst().orElseThrow().storage(); }
    @Test void graphKeepsResultReplayAndTextMotionWhileLiveWaitsForPlaybackOnlyOnce() {
        var gateway = controls(); var agent = new RockScissorPaper().createAgent(); var storage = storage(agent);
        var owner = new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway).withExternalSpeech(owner);
        var intro = agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Ready"), runtime);
        var plan = BehaviourPlan.fromJson(intro.getPayload()); assertNull(plan.getMotion()); assertNull(plan.getSpeech());
        assertEquals("INTRO", CoreRpsRound.phase(storage));
        var policy = (CoreRpsActivityPolicy) agent.reachableStates().stream().filter(s -> s.getName().contains("Reveal Sign")).findFirst().orElseThrow().ownPolicy();
        var nativeSpeech = agent.recordExternalSpeech("Show one hand. One, two, three.", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                owner.sessionId(), owner.epoch(), "spoken", SpeechProvenance.Association.NONE, List.of(), true));
        assertTrue(policy.liveSpeechCaptured(nativeSpeech, owner, Instant.now(), Instant.now(), List.of()));
        String id = CoreRpsRound.text(CoreRpsRound.round(storage), "id"); clearInvocations(gateway);
        var played = new Event(CoreRpsRound.PLAYED, "system", Event.KIND_SYSTEM, id);
        var reveal = BehaviourPlan.fromJson(agent.acknowledge(played, runtime).getPayload());
        assertEquals("rock", reveal.getMotion().getAsJsonObject().get("handSign").getAsString()); assertNull(reveal.getSpeech());
        assertTrue(reveal.getDisplay().getAsJsonObject().get("awaitingUserSign").getAsBoolean());
        assertNull(agent.acknowledge(played, runtime)); verify(gateway, never()).infer(any()); verify(gateway, never()).decide(any());
        agent.acknowledge(sign(id, Instant.now(), 1), runtime);
        assertTrue(agent.getCurrentState().getActiveStatePath().getLast().contains("Round Result"));
        assertEquals("RESULT", CoreRpsRound.phase(storage));
        assertFalse(policy.liveActivityContext().facts().contains("Stein"));
        assertDoesNotThrow(() -> new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter().instructions(agent));
        var textRuntime = new PolicyRuntime(new PromptMessageAssembler(), mock(LanguageModelGateway.class));
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Explain the result"), textRuntime);
        assertNotNull(BehaviourPlan.fromJson(agent.generate(textRuntime).getPayload()).getSpeech(), "Text fallback after Live must retain the result");
        assertNull(agent.generate(runtime), "The current Live owner suppresses a speech-only backend result");
        agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Again"), runtime);
        assertEquals("INTRO", CoreRpsRound.phase(storage)); assertFalse(storage.containsKey(RpsStorageKeys.LAST_ROUND));
        assertNull(agent.acknowledge(sign(id, Instant.now(), 1), runtime));
        agent.reset(); assertFalse(storage.containsKey(CoreRpsRound.KEY));
        var textAgent = new RockScissorPaper().createAgent();
        var textPlan = BehaviourPlan.fromJson(textAgent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Ready"),
                new PolicyRuntime(new PromptMessageAssembler(), gateway)).getPayload());
        assertEquals("Rock, scissor, paper", textPlan.getSpeech()); assertNotNull(textPlan.getMotion());
        assertTrue(textPlan.getDisplay().getAsJsonObject().get("awaitingUserSign").getAsBoolean());
    }
}
