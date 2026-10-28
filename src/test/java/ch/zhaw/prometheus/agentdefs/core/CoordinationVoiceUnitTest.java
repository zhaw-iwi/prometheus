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
    @ParameterizedTest @ValueSource(strings = {"revision", "expired", "checked"})
    void obsoleteOrAlreadyAssociatedContributionCannotRemainASpeakingOccasion(String condition) {
        f.propose(f.dialogue(), f.live); f.start(f.live);
        var response = f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        if (condition.equals("revision")) ch.zhaw.prometheus.model.coordination.CoordinationMemory.revise(f.storage());
        else if (condition.equals("expired")) { response.addProperty("expires", Instant.EPOCH.toString()); f.storage().put(CoreTaskVoice.RESPONSE, response); }
        else {
            Instant start = Instant.parse(response.get("created").getAsString()).plusNanos(1);
            var speech = f.hear("One agreed contribution", true, start);
            assertTrue(TaskResponse.claim(f.storage(), f.owner, speech, start, Instant.now()).isPresent());
        }
        assertNull(f.policy().liveActivityContext().continuation());
        assertFalse(f.policy().liveActivityContext().facts().contains("Give its first agreed contribution now"));
    }
    @Test void controlOccasionIsStableConsumedOnceAndCannotCrossSessionOrRevision() {
        f.propose(f.sensor(false), f.live);
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
        var check = TaskResponse.claim(f.storage(), f.owner, event, created.plusNanos(1), Instant.now()).orElseThrow();
        assertTrue(TaskResponse.finish(f.storage(), f.owner, check, true, Instant.now()));
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
}
