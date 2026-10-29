package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.*;

class CoordinationVoiceUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void pendingPreparationNamesTheRequiredEvidenceBeforeRequestingAcceptance() {
        f.propose(f.sensor(true), f.live);
        var facts = f.policy().liveActivityContext().facts();
        assertTrue(facts.contains("Before asking for acceptance, request fresh recognition evidence for: rock"));
        assertTrue(facts.contains("without quoting it"));
        assertFalse(ch.zhaw.prometheus.model.coordination.CoordinationMemory.ready(f.storage()));
    }
    @Test void armingDoesNotExposeFutureActionScriptButTheQualifiedCueDoes() {
        f.propose(f.sensor(false), f.live); f.start(f.live);
        var context = f.policy().liveActivityContext();
        assertNotNull(context.continuation()); assertTrue(context.facts().contains("rock"));
        assertFalse(f.policy().liveObservationContextEnabled());
        assertTrue(context.facts().startsWith("Task accepted and armed."));
        assertFalse(context.facts().contains("Offer one welcome idea"));
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject();
        assertEquals("ARMING", response.get("purpose").getAsString());
        assertFalse(response.get("facts").getAsString().contains("Offer one welcome idea"));
        var started = Instant.parse(response.get("created").getAsString()).plusNanos(1);
        var speech = f.hear("Armed, waiting for rock", true, started);
        var check = TaskResponse.claim(f.storage(), f.owner, speech, started, started.plusMillis(1)).orElseThrow();
        TaskResponse.finish(f.storage(), f.owner, check, true, started.plusMillis(2));
        assertFalse(f.policy().liveObservationContextEnabled());
        assertFalse(f.policy().liveActivityContext().facts().contains("Offer one welcome idea"));
        f.openCueWindow();
        org.mockito.Mockito.when(f.gateway.infer(org.mockito.ArgumentMatchers.any())).thenReturn("{\"speech\":\"One welcome idea\"}");
        f.sign("rock", 1, Instant.now(), f.live);
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("CONTRIBUTION", f.storage().get(TaskResponse.KEY).getAsJsonObject().get("purpose").getAsString());
        assertTrue(f.policy().liveObservationContextEnabled());
        assertTrue(f.policy().liveActivityContext().facts().contains("Required spoken content: One welcome idea"));
        assertFalse(f.policy().liveActivityContext().facts().contains("Offer one welcome idea"));
        var contribution = f.storage().get(TaskResponse.KEY).getAsJsonObject();
        var contributionStart = Instant.parse(contribution.get("created").getAsString()).plusNanos(1);
        var spoken = f.hear("One welcome idea", true, contributionStart);
        var delivered = TaskResponse.claim(f.storage(), f.owner, spoken, contributionStart, contributionStart.plusMillis(1)).orElseThrow();
        TaskResponse.finish(f.storage(), f.owner, delivered, true, contributionStart.plusMillis(2));
        assertFalse(f.policy().liveObservationContextEnabled());
        f.turn("KEEP", null, "What can you see?", f.live);
        assertTrue(f.policy().liveObservationContextEnabled());
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @ParameterizedTest @ValueSource(strings = {"revision", "expired", "checked"})
    void obsoleteOrAlreadyAssociatedContributionCannotRemainASpeakingOccasion(String condition) {
        f.propose(f.dialogue(), f.live); f.start(f.live);
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        if (condition.equals("revision")) ch.zhaw.prometheus.model.coordination.CoordinationMemory.revise(f.storage());
        else if (condition.equals("expired")) { response.addProperty("expires", Instant.EPOCH.toString()); f.storage().put(CoreTaskVoice.RESPONSE, response); }
        else {
            Instant start = Instant.parse(response.get("created").getAsString()).plusNanos(1);
            var speech = f.hear("One agreed contribution", true, start);
            assertTrue(TaskResponse.claim(f.storage(), f.owner, speech, start, start.plusMillis(1)).isPresent());
        }
        assertNull(f.policy().liveActivityContext().continuation());
        assertFalse(f.policy().liveActivityContext().facts().contains("Give its first agreed contribution now"));
    }
    @Test void controlOccasionIsStableConsumedOnceAndCannotCrossSessionOrRevision() {
        f.propose(f.sensor(false), f.live);
        assertTrue(f.policy().liveActivityContext().facts().startsWith("Proposal saved, not active."));
        assertTrue(f.policy().liveActivityContext().facts().contains("fresh matching cue"));
        var first = f.policy().liveActivityContext().continuation();
        assertEquals(first, f.policy().liveActivityContext().continuation());
        Instant created = Instant.parse(f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().get("created").getAsString());
        f.hear("Proposed task", false, created.plusNanos(1)); assertNotNull(f.policy().liveActivityContext().continuation());
        f.hear("Older words", true, created.minusNanos(1)); assertNotNull(f.policy().liveActivityContext().continuation());
        var wrong = new ExternalSpeech(UUID.randomUUID(), f.owner.epoch());
        var event = f.agent.recordExternalSpeech("Unrelated session", new SpeechProvenance(SpeechProvenance.Origin.NATIVE,
                wrong.sessionId(), wrong.epoch(), UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), true));
        f.policy().liveSpeechCaptured(event, wrong, created.plusNanos(1), Instant.now(), List.of());
        assertNotNull(f.policy().liveActivityContext().continuation());
        f.hear("Awaiting acceptance. Shall we start now?", true, created.plusNanos(1));
        assertNull(f.policy().liveActivityContext().continuation());
        f.policy().beginLiveConversation(f.agent.getEventHistory()); assertFalse(f.storage().containsKey(CoreTaskVoice.RESPONSE));
        f.start(f.live); assertNotNull(f.policy().liveActivityContext().continuation());
        f.say("Return to conversation", f.live);
        assertTrue(f.policy().liveActivityContext().facts().contains("now in Conversation"));
        assertNotEquals(first.id(), f.policy().liveActivityContext().continuation().id());
    }
    @Test void dialogueContributionWaitsForAssociatedSpeechAndRefreshCannotRepeatIt() {
        f.propose(f.dialogue(), f.live); f.start(f.live);
        assertEquals("PENDING", TaskResponse.status(f.storage()));
        var response = f.storage().get(TaskResponse.KEY).getAsJsonObject().deepCopy();
        f.turn("STEP", null, "Do the next part", f.live); assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        // The failed request must not replace the task response's identity or capture boundary.
        assertEquals(response, f.storage().get(TaskResponse.KEY));
        assertEquals(response.get("id").getAsString(), f.policy().liveActivityContext().continuation().id().toString());
        var created = Instant.parse(response.get("created").getAsString());
        var event = f.hear("One agreed contribution", true, created.plusNanos(1));
        var check = TaskResponse.claim(f.storage(), f.owner, event, created.plusNanos(1), created.plusMillis(1)).orElseThrow();
        assertTrue(TaskResponse.finish(f.storage(), f.owner, check, true, created.plusMillis(2)));
        f.turn("STEP", null, "Continue with the next part", f.live);
        assertEquals(2, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertNotEquals(response.get("id"), f.storage().get(TaskResponse.KEY).getAsJsonObject().get("id"));
        var current = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject();
        assertEquals(f.owner.sessionId().toString(), current.get("session").getAsString());
        f.policy().beginLiveConversation(f.agent.getEventHistory());
        assertEquals("PAUSED", TaskMemory.phase(f.storage())); assertNull(f.policy().liveActivityContext().continuation());
    }
    @Test void ordinaryQuestionsDoNotAuthorizeTaskContributions() {
        f.propose(f.sensor(false), f.live); f.start(f.live);
        int count = f.storage().get(TaskMemory.ACTIONS).getAsInt();
        f.turn("KEEP", null, "What can you see?", f.live);
        assertEquals(count, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(f.policy().voiceInstructions().contains("Ordinary unrelated questions"));
    }
    @Test void cueUsesCurrentCommittedContentAndOnlyCompleteExplicitLiteralsGetExactMatching() {
        for (String action : List.of("Say exactly: \"Welcome to the lab.\"", "Say exactly: Welcome to the lab.",
                "Say exactly: \"Welcome to the lab.\" Then estimate the face (e.g. neutral).", "Say exactly: A different line.")) {
            var fixture = new CoordinationAgentUnitTest(); var agreement = fixture.sensor(false);
            agreement.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("action", action);
            fixture.propose(agreement, fixture.live); fixture.start(fixture.live);
            var response = fixture.storage().get(TaskResponse.KEY).getAsJsonObject();
            var at = Instant.parse(response.get("created").getAsString()).plusNanos(1);
            var armed = fixture.hear("Waiting for rock", true, at);
            var check = TaskResponse.claim(fixture.storage(), fixture.owner, armed, at, at.plusMillis(1)).orElseThrow();
            TaskResponse.finish(fixture.storage(), fixture.owner, check, true, at.plusMillis(2)); fixture.openCueWindow();
            org.mockito.Mockito.when(fixture.gateway.infer(org.mockito.ArgumentMatchers.any())).thenReturn("{\"speech\":\"Welcome to the lab.\"}");
            fixture.sign("rock", 1, Instant.now(), fixture.live);
            var committed = fixture.storage().get(TaskResponse.KEY).getAsJsonObject();
            assertTrue(committed.get("facts").getAsString().contains("Required spoken content: Welcome to the lab."));
            assertFalse(committed.get("facts").getAsString().contains("neutral"));
            assertFalse(fixture.policy().liveActivityContext().facts().contains("neutral"));
            assertEquals(!action.contains("Then") && !action.contains("different"), committed.has("exactSpeech"));
        }
    }
}
