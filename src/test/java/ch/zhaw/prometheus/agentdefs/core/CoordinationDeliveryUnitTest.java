package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;

class CoordinationDeliveryUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void readyDraftInANewSessionNeedsANewCompletedInvitationButCanThenAccept() {
        f.propose(f.dialogue(), f.live);
        f.policy().beginLiveConversation(f.agent.getEventHistory());
        assertNull(f.policy().liveActivityContext().continuation());
        assertFalse(f.policy().liveResolvedControlReply("yes", f.agent.getEventHistory(), f.owner));
        f.storage().put(CoreTaskVoice.INVITATION, new JsonPrimitive(Instant.EPOCH.toString()));
        f.hear("Shall we start the saved proposal now?", true, Instant.now());
        assertTrue(f.policy().liveResolvedControlReply("yes", f.agent.getEventHistory(), f.owner));
        f.turn("START", null, "yes", f.live); assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
    }
    void startOne() {
        var agreement = f.dialogue(); agreement.addProperty("dialogueLimit", 1);
        f.propose(agreement, f.live); f.start(f.live);
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        assertTrue(f.agent.getCurrentState().getName().contains("finishing"));
        assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
    }
    Event system(String type, String payload) { return new Event(type, "system", Event.KIND_SYSTEM, payload); }
    void capture() {
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject();
        var started = Instant.parse(response.get("created").getAsString()).plusNanos(1);
        var speech = f.hear("One agreed contribution.", true, started);
        var check = TaskResponse.claim(f.storage(), f.owner, speech, started, started.plusMillis(1)).orElseThrow();
        assertTrue(TaskResponse.finish(f.storage(), f.owner, check, true, started.plusMillis(2)));
    }
    @Test void finalSpeechNeedsAssociationAndCurrentPlaybackAcknowledgementBeforeReflection() {
        startOne(); var cue = f.policy().liveActivityContext().playbackCue(); assertNotNull(cue); assertNull(cue.segmentId());
        f.agent.acknowledge(system(CoordinationDelivery.PLAYED, cue.id().toString()), f.live);
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        f.turn("STEP", null, "One more contribution", f.live); assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        capture(); cue = f.policy().liveActivityContext().playbackCue(); assertNotNull(cue.segmentId());
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        f.agent.acknowledge(system(CoordinationDelivery.PLAYED, UUID.randomUUID().toString()), f.live);
        var other = f.text.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), f.owner.epoch()));
        f.agent.acknowledge(system(CoordinationDelivery.PLAYED, cue.id().toString()), other);
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        f.agent.acknowledge(system(CoordinationDelivery.PLAYED, cue.id().toString()), f.live);
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage())); assertNull(f.policy().liveActivityContext().playbackCue());
        assertEquals("PLAYBACK_CONFIRMED", f.storage().get(CoordinationDelivery.KEY).getAsJsonObject().get("status").getAsString());
        f.agent.acknowledge(system(CoordinationDelivery.PLAYED, cue.id().toString()), f.live);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @ParameterizedTest @ValueSource(strings = {"timeout", "ended", "failed", "stop", "return"})
    void uncertainDeliveryNeverReplaysAndKeepsBudgetOutcome(String reason) {
        startOne(); capture();
        if (reason.equals("timeout")) {
            var delivery = f.storage().get(CoordinationDelivery.KEY).getAsJsonObject().deepCopy();
            delivery.addProperty("deadline", Instant.EPOCH.toString()); f.storage().put(CoordinationDelivery.KEY, delivery);
            assertTrue(f.policy().liveActivityContext().playbackCue().expired(Instant.now()));
            f.agent.acknowledge(system(Event.TYPE_SYSTEM_TICK, ""), f.live);
        } else if (reason.equals("ended") || reason.equals("failed"))
            f.agent.acknowledge(system(reason.equals("ended") ? CoordinationDelivery.ENDED : CoordinationDelivery.FAILED, "test"), f.live);
        else f.say(reason.equals("stop") ? "Stop" : "Return to conversation", f.live);
        assertEquals(reason.equals("return") ? "CONVERSATION" : "UNCERTAIN", CoordinationMemory.stage(f.storage()));
        assertEquals("LIMIT_REACHED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
        assertEquals("UNCERTAIN", f.storage().get(CoordinationDelivery.KEY).getAsJsonObject().get("status").getAsString());
        assertNull(f.policy().liveActivityContext().playbackCue());
        f.turn("RESTART", null, "Restart the same task", f.live);
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("UNCERTAIN", f.storage().get(CoordinationMemory.OUTCOMES).getAsJsonArray().get(0).getAsJsonObject()
                .getAsJsonObject("delivery").get("status").getAsString());
    }
    @ParameterizedTest @ValueSource(strings = {"text", "silent", "legacy", "immediate"})
    void publicationModeSilentOutputAndOldGraphsKeepTheirContracts(String mode) {
        var agreement = f.dialogue(); agreement.addProperty("dialogueLimit", 1);
        if (mode.equals("immediate")) {
            agreement = f.sensor(false); agreement.getAsJsonObject("reactions").addProperty("firstAction", "IMMEDIATE");
            agreement.getAsJsonObject("reactions").addProperty("maxActions", 1);
        }
        if (mode.equals("legacy")) f.storage().remove(CoordinationDelivery.ENABLED);
        var runtime = mode.equals("text") ? f.text : f.live; f.propose(agreement, runtime);
        if (mode.equals("silent")) {
            when(f.gateway.infer(any())).thenReturn("{\"operation\":\"START\",\"agreement\":null,\"reply\":{\"motion\":{\"handSign\":\"paper\"}}}");
            f.say("Start the saved proposal now", runtime);
        } else f.start(runtime);
        assertEquals(mode.equals("immediate") ? "FINISHING" : "REFLECTION", CoordinationMemory.stage(f.storage()));
        assertEquals(mode.equals("immediate"), f.policy().liveActivityContext().playbackCue() != null);
    }
    @Test void finalSensorContributionUsesTheSameDeliveryBoundary() {
        var agreement = f.sensor(false); agreement.getAsJsonObject("reactions").addProperty("maxActions", 1);
        f.propose(agreement, f.live); f.start(f.live); capture(); f.openCueWindow();
        when(f.gateway.infer(any())).thenReturn("{\"speech\":\"Offer one welcome idea\"}");
        f.sign("rock", 1, Instant.now(), f.live);
        assertEquals("FINISHING", CoordinationMemory.stage(f.storage()));
        assertEquals("PENDING", TaskResponse.status(f.storage()));
        assertNotNull(f.policy().liveActivityContext().playbackCue());
    }
}
