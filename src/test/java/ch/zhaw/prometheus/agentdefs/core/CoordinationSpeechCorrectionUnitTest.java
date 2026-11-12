package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.policy.LiveSpeechRequest;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationSpeechCorrectionUnitTest {
    @Test void supportCorrectionKeepsTheSameCompleteSpokenInstructionAndAuthority() {
        var f = new CoordinationAgentUnitTest(); f.storage().put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        f.propose(f.sensor(true), f.live); f.say("I'm stuck. What should I do next?", f.live);
        var request = f.policy().liveSpeechRequest(f.owner);
        var original = f.policy().liveActivityContext().continuation();
        assertTrue(original.selfContained());
        assertTrue(original.facts().startsWith("Speak these words verbatim:"));
        assertTrue(original.facts().contains(request.facts()));
        var draft = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        UUID correction = f.policy().liveSpeechCorrection(request, "incomplete-help", Instant.now());
        var corrected = f.policy().liveActivityContext().continuation();
        assertEquals(original.facts(), corrected.facts()); assertEquals(correction, corrected.id());
        assertEquals(request.id(), corrected.responseId()); assertEquals(request.expiresAt(), corrected.expiresAt());
        assertEquals(draft, f.storage().get(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
    }
    CoordinationAgentUnitTest fixture() {
        var f = new CoordinationAgentUnitTest();
        f.storage().put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        f.propose(f.dialogue(), f.live); return f;
    }
    @Test void oneOrdinaryCorrectionPreservesAuthorityAndProjectsItsOwnOccasionUntilRelease() {
        var f = fixture(); var request = f.policy().liveSpeechRequest(f.owner);
        var original = f.storage().get(CoreTaskVoice.RESPONSE).deepCopy().getAsJsonObject();
        var draft = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        UUID correction = f.policy().liveSpeechCorrection(request, "rejected", Instant.now());
        assertNotNull(correction); var corrected = f.policy().liveSpeechRequest(f.owner);
        assertEquals(request.id(), corrected.id()); assertEquals(request.facts(), corrected.facts());
        assertEquals(request.expiresAt(), corrected.expiresAt()); assertEquals(request.releaseBy(), corrected.releaseBy());
        assertEquals(correction, corrected.correctionId()); assertNotNull(corrected.correctionAt());
        for (String key : List.of("id", "facts", "created", "expires", "revision", "session", "epoch"))
            assertEquals(original.get(key), f.storage().get(CoreTaskVoice.RESPONSE).getAsJsonObject().get(key));
        var context = f.policy().liveActivityContext();
        assertTrue(context.continuation().correction()); assertEquals(correction, context.continuation().id());
        assertEquals(request.id(), context.continuation().responseId()); assertEquals(request.expiresAt(), context.continuation().expiresAt());
        assertTrue((context.facts() + context.continuation().facts()).contains(f.dialogue().get("humanRole").getAsString()));
        assertTrue(corrected.facts().contains(f.dialogue().get("humanRole").getAsString()));
        assertNull(f.policy().liveSpeechCorrection(corrected, "second-rejection", Instant.now()));
        assertTrue(f.policy().liveSpeechReleased(corrected, "replacement"));
        assertTrue(f.policy().liveSpeechRequest(f.owner).released());
        assertNull(f.policy().liveSpeechCorrection(f.policy().liveSpeechRequest(f.owner), "after-release", Instant.now()));
        assertEquals(draft, f.storage().get(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS)); assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
    }
    @ParameterizedTest @ValueSource(strings = {"late", "replaced", "disabled", "released", "task", "pending_help"})
    void correctionCannotAcquireStaleOrDifferentAuthority(String boundary) {
        var f = fixture(); var request = f.policy().liveSpeechRequest(f.owner); Instant now = Instant.now();
        switch (boundary) {
            case "late" -> now = request.expiresAt().minusSeconds(9);
            case "replaced" -> f.say("My answer is", f.live);
            case "disabled" -> f.storage().put(LiveSpeechRequest.ENABLED, new JsonPrimitive(false));
            case "released" -> f.policy().liveSpeechReleased(request, "already-released");
            case "task", "pending_help" -> {
                f.start(f.live);
                if (boundary.equals("pending_help")) f.say("Help me", f.live);
                request = f.policy().liveSpeechRequest(f.owner);
            }
        }
        assertNotNull(request);
        assertNull(f.policy().liveSpeechCorrection(request, "rejected", now));
    }
}
