package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskResponse;
import ch.zhaw.prometheus.model.coordination.CoordinationSupport;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.LiveSpeechPart;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class CoordinationCaptureUnitTest {
    @ParameterizedTest @ValueSource(strings = {"fresh", "review", "old", "split", "mismatch", "future", "unordered", "incomplete", "owner", "expired", "missing"})
    void preparationAcknowledgementUsesOnlyACompleteFreshSentenceFromTrustedParts(String variant) {
        var f = new CoordinationAgentUnitTest();var proposal = f.sensor(true);
        if (variant.equals("review")) { proposal.add("reactions", com.google.gson.JsonNull.INSTANCE);proposal.getAsJsonArray("questions").add("What response should follow the cue?"); }
        f.propose(proposal, f.live);
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        var boundary = Instant.parse(response.get("created").getAsString());
        Instant before = boundary.minusMillis(200), after = boundary.plusMillis(200), now = boundary.plusSeconds(1);
        if (variant.equals("expired")) now = boundary.plusSeconds(46);
        String prefix = "I can help. ", request = "Please demonstrate the agreed signs before acceptance.";
        var parts = List.of(new LiveSpeechPart(prefix, before), new LiveSpeechPart(request, after));
        switch (variant) {
            case "old" -> parts = List.of(new LiveSpeechPart(prefix, before), new LiveSpeechPart(request, boundary));
            case "split" -> parts = List.of(new LiveSpeechPart(prefix + "Please ", before), new LiveSpeechPart(request.substring(7), after));
            case "mismatch" -> parts = List.of(new LiveSpeechPart(prefix, before), new LiveSpeechPart("Different words.", after));
            case "future" -> parts = List.of(new LiveSpeechPart(prefix, before), new LiveSpeechPart(request, now.plusMillis(1)));
            case "unordered" -> parts = List.of(new LiveSpeechPart(prefix, after), new LiveSpeechPart(request, before));
            case "missing" -> parts = List.of();
        }
        var event = f.agent.recordExternalSpeech(prefix + request, new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                variant.equals("owner") ? UUID.randomUUID() : f.owner.sessionId(), f.owner.epoch(), UUID.randomUUID().toString(),
                SpeechProvenance.Association.NONE, List.of(), !variant.equals("incomplete")));
        boolean accepted = variant.equals("fresh") || variant.equals("review");
        assertEquals(accepted, f.policy().liveSpeechCaptured(event, f.owner, before, now, parts));
        assertEquals(accepted, f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().has("consumed"));
        assertEquals(variant.equals("review") ? "COORDINATION" : "PREPARATION", CoordinationMemory.stage(f.storage()));
        assertTrue(CoordinationMemory.evidence(f.storage()).isEmpty());
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(f.storage().containsKey(CoreTaskVoice.SPOKEN_PROMPT));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
    }
    @ParameterizedTest @ValueSource(strings = {"A dry", "Tell me a", "Make it a", "Can you", "My answer is", "I think it is", "Die Antwort ist"})
    void unfinishedInputCannotReviseOrExecuteEvenIfProviderWouldCompleteIt(String fragment) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        String revision = TaskMemory.text(f.storage(), TaskMemory.REVISION, "0");
        clearInvocations(f.gateway);
        var response = f.turn("PROPOSE", f.dialogue(), fragment, f.text);
        assertTrue(response.getPayload().contains("finish that request"));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertEquals(revision, TaskMemory.text(f.storage(), TaskMemory.REVISION, "0"));
        verifyNoInteractions(f.gateway);
        f.say("Stop", f.text); assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
    }
    @Test void completeButUnfinishedAnswerGetsALiveSupportOccasionWithoutSpending() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        f.storage().put("live.speechApproval", new com.google.gson.JsonPrimitive(true));
        var revision = f.storage().get(TaskMemory.REVISION).deepCopy();
        var agreement = f.storage().get(CoordinationMemory.AGREEMENT).deepCopy();
        var phase = TaskMemory.phase(f.storage());
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(revision, f.storage().get(TaskMemory.REVISION));
        assertEquals(agreement, f.storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(phase, TaskMemory.phase(f.storage()));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(f.storage().containsKey(TaskResponse.KEY));
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        assertTrue(response.get("supportOnly").getAsBoolean());
        var context = f.policy().liveActivityContext();
        assertFalse(context.facts().contains("finish that request"));
        assertTrue(context.continuation().selfContained());
        assertEquals("Convey these confirmed facts once, preserving their meaning: \"Please finish that request.\"", context.continuation().facts());
        assertEquals(response.get("id").getAsString(), context.continuation().id().toString());
        assertEquals(response.get("expires").getAsString(), context.continuation().expiresAt().toString());
        verifyNoInteractions(f.gateway);
    }
    @ParameterizedTest @ValueSource(strings = {"PENDING", "REJECTED"})
    void unfinishedAnswerCannotReplaceAnInFlightResponseOrItsDeadline(String status) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.live); f.start(f.live);
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject(); response.addProperty("status", status); f.storage().put(TaskResponse.KEY, response);
        var pending = f.storage().get(TaskResponse.KEY).deepCopy();
        var permission = f.storage().get(CoreTaskVoice.RESPONSE).deepCopy();
        clearInvocations(f.gateway); f.say("My answer is", f.live);
        assertEquals(pending, f.storage().get(TaskResponse.KEY));
        assertEquals(permission, f.storage().get(CoreTaskVoice.RESPONSE));
        assertTrue(f.storage().containsKey(CoordinationSupport.LIVE_HELP)); verifyNoInteractions(f.gateway);
    }
}
