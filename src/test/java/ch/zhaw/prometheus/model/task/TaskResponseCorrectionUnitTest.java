package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;

class TaskResponseCorrectionUnitTest {
    final Instant now = Instant.parse("2026-11-11T18:00:00Z");
    final Storage storage = new Storage();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    final UUID response = UUID.randomUUID();
    final String question = "What has keys but no locks, space but no room, and Enter but you cannot go inside?";
    TaskResponseCorrectionUnitTest() {
        TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.put(storage, TaskMemory.REVISION, "2");
        TaskMemory.put(storage, TaskMemory.ACTIONS, "1");
        storage.put(LiveSpeechRequest.ENABLED, new JsonPrimitive(true));
        TaskResponse.expect(storage, owner, response, question, now);
        TaskResponse.purpose(storage, TaskResponse.Purpose.CONTRIBUTION);
    }
    TaskResponse.Check claim(Storage target, String segment, String text, Instant started, Instant at) {
        var event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", BehaviourPlan.speechOnly(text).toJson());
        event.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), segment,
                SpeechProvenance.Association.NONE, List.of(), true));
        return TaskResponse.claim(target, owner, event, started, at).orElse(null);
    }
    TaskResponse.Check reject() {
        var check = claim(storage, "wrong", "What has many keys but opens no doors?", now.plusSeconds(1), now.plusSeconds(3));
        assertNotNull(check);
        assertTrue(TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.AMBIGUOUS, now.plusSeconds(4), true));
        assertEquals("REJECTED", TaskResponse.status(storage));
        return check;
    }
    Storage reload() {
        var reloaded = new Storage();
        for (String key : List.of(TaskMemory.PHASE, TaskMemory.REVISION, TaskMemory.ACTIONS, LiveSpeechRequest.ENABLED, TaskResponse.KEY))
            reloaded.put(key, storage.get(key).deepCopy());
        return reloaded;
    }
    @Test void changedRiddleGetsOneFreshDeliveryAttemptWithUnchangedAuthorityAndBudget() {
        var first = reject();
        assertTrue(TaskResponse.correct(storage, owner, "wrong", now.plusSeconds(5)));
        var correction = TaskResponse.correctionId(storage);
        assertNotNull(correction); assertNotEquals(response, correction);
        var loaded = reload();
        assertEquals(correction, TaskResponse.correctionId(loaded));
        assertFalse(TaskResponse.correct(loaded, owner, "wrong", now.plusSeconds(6)));
        assertFalse(TaskResponse.finish(loaded, owner, first, TaskResponse.Verdict.MATCH, now.plusSeconds(6)));
        assertNull(claim(loaded, "old-draft", question, now.plusSeconds(4), now.plusSeconds(7)));
        assertNull(claim(loaded, "wrong", question, now.plusSeconds(6), now.plusSeconds(7)));
        var next = claim(loaded, "corrected", question, now.plusSeconds(6), now.plusSeconds(9));
        assertNotNull(next); assertEquals(response, next.id()); assertEquals(first.facts(), next.facts());
        assertEquals(first.deadline(), next.deadline()); assertEquals(first.revision(), next.revision());
        assertTrue(TaskResponse.finish(loaded, owner, next, TaskResponse.Verdict.MATCH, now.plusSeconds(10), true));
        assertEquals("CAPTURED", TaskResponse.status(loaded)); assertTrue(TaskResponse.playbackPending(loaded));
        assertEquals(1, loaded.get(TaskMemory.ACTIONS).getAsInt());
        assertTrue(TaskResponse.release(loaded, owner, response, "corrected"));
        assertFalse(TaskResponse.release(loaded, owner, response, "corrected"));
        assertTrue(TaskResponse.released(loaded));
    }
    @Test void secondRejectionIsTerminalAndCannotRenewTheDeadline() {
        reject(); assertTrue(TaskResponse.correct(storage, owner, "wrong", now.plusSeconds(5)));
        var second = claim(storage, "second", "What has keys but no doors?", now.plusSeconds(6), now.plusSeconds(9));
        assertTrue(TaskResponse.finish(storage, owner, second, TaskResponse.Verdict.AMBIGUOUS, now.plusSeconds(10), true));
        assertEquals("UNCERTAIN", TaskResponse.status(storage));
        assertFalse(TaskResponse.correct(storage, owner, "second", now.plusSeconds(11)));
        assertEquals(1, storage.get(TaskMemory.ACTIONS).getAsInt());
    }
    @Test void lateIncompleteOldDraftCannotCancelTheCorrectionButNewInterruptedSpeechCan() {
        reject(); assertTrue(TaskResponse.correct(storage, owner, "wrong", now.plusSeconds(5)));
        var event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", BehaviourPlan.speechOnly("Partial draft").toJson());
        event.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(), "late",
                SpeechProvenance.Association.NONE, List.of(), false));
        assertFalse(TaskResponse.incomplete(storage, owner, event, now.plusSeconds(4), now.plusSeconds(8)));
        assertTrue(TaskResponse.incomplete(storage, owner, event, now.plusSeconds(6), now.plusSeconds(8)));
    }
    @ParameterizedTest @ValueSource(strings = {"deadline", "owner", "revision", "paused", "released", "disabled", "wrong_segment"})
    void correctionRequiresCurrentUnreleasedAuthorityAndTime(String boundary) {
        reject(); var currentOwner = owner; var at = now.plusSeconds(5); var segment = "wrong";
        switch (boundary) {
            case "deadline" -> at = now.plusSeconds(36);
            case "owner" -> currentOwner = new ExternalSpeech(UUID.randomUUID(), owner.epoch());
            case "revision" -> TaskMemory.put(storage, TaskMemory.REVISION, "3");
            case "paused" -> TaskMemory.put(storage, TaskMemory.PHASE, "PAUSED");
            case "released" -> { var v = storage.get(TaskResponse.KEY).getAsJsonObject(); v.addProperty("released", true); storage.put(TaskResponse.KEY, v); }
            case "disabled" -> storage.put(LiveSpeechRequest.ENABLED, new JsonPrimitive(false));
            case "wrong_segment" -> segment = "unrelated";
        }
        assertFalse(TaskResponse.correct(storage, currentOwner, segment, at));
        assertNull(TaskResponse.correctionId(storage));
    }
    @Test void rejectedSpeechExpiresAndAnUnverifiedVerdictKeepsExistingRecovery() {
        reject(); assertEquals(now.plusSeconds(45), TaskResponse.deadline(storage));
        assertTrue(TaskResponse.expired(storage, now.plusSeconds(45)));
        TaskResponse.expect(storage, owner, response, question, now);
        var check = claim(storage, "invalid", question, now.plusSeconds(1), now.plusSeconds(3));
        assertTrue(check.verifiedVerdict("not JSON").isEmpty());
        TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.AMBIGUOUS, now.plusSeconds(4), false);
        assertEquals("UNCERTAIN", TaskResponse.status(storage));
    }
}
