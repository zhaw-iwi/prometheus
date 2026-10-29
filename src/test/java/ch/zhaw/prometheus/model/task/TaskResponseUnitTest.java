package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import com.google.gson.*;

class TaskResponseUnitTest {
    final Instant now = Instant.parse("2026-10-22T08:00:00Z");
    final Storage storage = new Storage();
    final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
    final UUID response = UUID.randomUUID();
    TaskResponseUnitTest() {
        TaskMemory.put(storage, TaskMemory.PHASE, "RUNNING"); TaskMemory.put(storage, TaskMemory.REVISION, "2");
        TaskResponse.expect(storage, owner, response, "Tell one new robot joke; the agreed rock hand motion was requested separately.", now);
    }
    Event speech(String text, ExternalSpeech source, boolean complete, String id) {
        Event e = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, "assistant", BehaviourPlan.speechOnly(text).toJson());
        e.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, source.sessionId(), source.epoch(), id,
                SpeechProvenance.Association.NONE, List.of(), complete)); return e;
    }
    String result(TaskResponse.Check c, String verdict) {
        return "{\"responseId\":\""+c.id()+"\",\"revision\":"+c.revision()+",\"segmentId\":\""+c.segment()+"\",\"verdict\":\""+verdict+"\"}";
    }
    @Test void completedParaphraseCanBeClaimedOnceAndOnlyFreshPostCheckCuesAreEligible() {
        var e = speech("My circuits run on coffee today!", owner, true, "segment-1");
        var c = TaskResponse.claim(storage, owner, e, now.plusSeconds(1), now.plusSeconds(5)).orElseThrow();
        assertTrue(TaskResponse.claim(storage, owner, e, now.plusSeconds(1), now.plusSeconds(6)).isEmpty());
        assertFalse(TaskResponse.captured(storage, owner));
        assertTrue(c.matches(result(c, "MATCH")));
        assertTrue(TaskResponse.finish(storage, owner, c, true, now.plusSeconds(8)));
        assertTrue(TaskResponse.captured(storage, owner));
        assertEquals(now.plusSeconds(8).toString(), TaskMemory.text(storage, TaskMemory.AFTER, ""));
        assertEquals(1, TaskResponse.firstSample(storage, List.of(e), owner));
        assertEquals(-1, TaskResponse.firstSample(storage, List.of(), owner));
        assertFalse(TaskResponse.finish(storage, owner, c, true, now.plusSeconds(9)));
        assertFalse(c.toString().contains("coffee"));
    }
    @Test void acknowledgmentsOldStartsIncompleteOrWrongSessionDoNotSpendTheCheck() {
        for (var e : List.of(speech("Okay!", owner, true, "a"), speech("A substantive but incomplete answer", owner, false, "b"),
                speech("Some unrelated other-session answer", new ExternalSpeech(UUID.randomUUID(),owner.epoch()), true, "c")))
            assertTrue(TaskResponse.claim(storage, owner, e, now.plusSeconds(1), now.plusSeconds(4)).isEmpty());
        var e = speech("I have told a robot joke", owner, true, "d");
        assertTrue(TaskResponse.claim(storage, owner, e, now.minusSeconds(1), now.plusSeconds(4)).isEmpty());
        assertTrue(TaskResponse.claim(storage, owner, e, null, now.plusSeconds(4)).isEmpty());
        assertEquals("PENDING", TaskResponse.status(storage));
    }
    @Test void wrongIdentitiesInvalidVerdictsAndUnrelatedSpeechNeverAuthorizeProgression() {
        var c = TaskResponse.claim(storage, owner, speech("What time is dinner?",owner,true,"e"),now.plusSeconds(1),now.plusSeconds(3)).orElseThrow();
        for (String raw : List.of(result(c,"UNRELATED"),result(c,"AMBIGUOUS"),result(c,"MATCH").replace(c.id().toString(),UUID.randomUUID().toString()),
                result(c,"MATCH").replace("\"revision\":2","\"revision\":3"),result(c,"MATCH").replace("\"e\"","\"other\""),"{}")) assertFalse(c.matches(raw));
        assertTrue(TaskResponse.finish(storage,owner,c,false,now.plusSeconds(4)));
        assertEquals("UNCERTAIN",TaskResponse.status(storage)); assertFalse(TaskResponse.captured(storage,owner));
    }
    @Test void explicitlyRequestedShortWordStillRequiresTheSemanticCheck() {
        TaskResponse.expect(storage, owner, response, "Say exactly Okay on this cue.", now);
        var check = TaskResponse.claim(storage, owner, speech("Okay!", owner, true, "literal"),
                now.plusSeconds(1), now.plusSeconds(2)).orElseThrow();
        assertFalse(TaskResponse.captured(storage, owner));
        assertTrue(check.request().messages().getFirst().getContent().contains("explicitly requested exact word"));
        assertTrue(TaskResponse.finish(storage, owner, check, true, now.plusSeconds(3)));
        assertTrue(TaskResponse.captured(storage, owner));
    }
    @Test void deadlineOrRevisionChangeFencesLateSuccessAndReloadCannotRepeatClaim() {
        var e=speech("A complete robot joke is here",owner,true,"f");
        var c=TaskResponse.claim(storage,owner,e,now.plusSeconds(1),now.plusSeconds(3)).orElseThrow();
        var reload=new Storage(); storage.toMap().forEach((k,v)->reload.put(k,JsonParser.parseString(v.toString())));
        assertTrue(TaskResponse.claim(reload,owner,e,now.plusSeconds(1),now.plusSeconds(4)).isEmpty());
        assertTrue(TaskResponse.expired(reload,now.plusSeconds(45)));
        assertTrue(TaskResponse.finish(reload,owner,c,true,now.plusSeconds(45))); assertEquals("UNCERTAIN",TaskResponse.status(reload));
        TaskMemory.put(storage,TaskMemory.REVISION,"3"); assertFalse(TaskResponse.finish(storage,owner,c,true,now.plusSeconds(4)));
        assertFalse(TaskResponse.captured(storage,owner));
        TaskMemory.clear(storage); assertFalse(storage.containsKey(TaskResponse.KEY));
    }
    @Test void noCaptionDeadlineDoesNotBecomeSuccessAndFutureCooldownIsPreserved() {
        assertTrue(TaskResponse.expired(storage,now.plusSeconds(46))); assertFalse(TaskResponse.captured(storage,owner));
        TaskResponse.uncertain(storage); assertNull(TaskResponse.deadline(storage));
        TaskResponse.expect(storage,owner,UUID.randomUUID(),"Task is armed; waiting for a fresh cue, no action yet.",now);
        TaskMemory.put(storage,TaskMemory.AFTER,now.plusSeconds(20).toString());
        var c=TaskResponse.claim(storage,owner,speech("Ready and waiting for your sign.",owner,true,"g"),now.plusSeconds(1),now.plusSeconds(3)).orElseThrow();
        assertTrue(TaskResponse.finish(storage,owner,c,true,now.plusSeconds(4)));
        assertEquals(now.plusSeconds(20).toString(),TaskMemory.text(storage,TaskMemory.AFTER,""));
    }
    @Test void silentWaitPreservesTheCapturedBoundaryButCannotRebaseAPendingCheck() {
        var c=TaskResponse.claim(storage,owner,speech("A complete robot joke",owner,true,"wait"),now.plusSeconds(1),now.plusSeconds(3)).orElseThrow();
        assertTrue(TaskResponse.finish(storage,owner,c,true,now.plusSeconds(4)));
        TaskMemory.revise(storage); TaskResponse.preserveCapturedBoundaryOnWait(storage);
        assertTrue(TaskResponse.captured(storage,owner));
        TaskResponse.expect(storage,owner,UUID.randomUUID(),"Another robot joke",now.plusSeconds(5));
        TaskMemory.revise(storage); TaskResponse.preserveCapturedBoundaryOnWait(storage);
        assertFalse(TaskResponse.captured(storage,owner));
        assertTrue(TaskResponse.claim(storage,owner,speech("A new robot joke",owner,true,"new"),now.plusSeconds(6),now.plusSeconds(8)).isEmpty());
    }
    @Test void optedInUnrelatedCandidateLeavesOneChanceWithinTheOriginalDeadline() {
        var first = speech("The weather is nice", owner, true, "unrelated");
        var check = TaskResponse.claim(storage, owner, first, now.plusSeconds(1), now.plusSeconds(2), 1).orElseThrow();
        assertEquals(TaskResponse.Verdict.UNRELATED, check.verdict(result(check, "UNRELATED")));
        assertTrue(TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.UNRELATED, now.plusSeconds(3)));
        assertEquals("PENDING", TaskResponse.status(storage));
        assertEquals(now.plusSeconds(45), TaskResponse.deadline(storage));
        assertTrue(TaskResponse.claim(storage, owner, first, now.plusSeconds(1), now.plusSeconds(4), 1).isEmpty());
        assertFalse(storage.containsKey(TaskMemory.AFTER));
        var second = TaskResponse.claim(storage, owner, speech("A complete robot joke", owner, true, "actual"),
                now.plusSeconds(4), now.plusSeconds(5), 1).orElseThrow();
        assertTrue(TaskResponse.finish(storage, owner, second, TaskResponse.Verdict.MATCH, now.plusSeconds(6)));
        assertTrue(TaskResponse.captured(storage, owner));
    }
    @Test void allowanceCannotBeRenewedAndDefaultOrAmbiguousResultsRemainConservative() {
        for (var verdict : List.of(TaskResponse.Verdict.UNRELATED, TaskResponse.Verdict.AMBIGUOUS)) {
            TaskResponse.expect(storage, owner, response, "A robot joke", now);
            var first = TaskResponse.claim(storage, owner, speech("Unrelated speech", owner, true, "one"), now.plusSeconds(1), now.plusSeconds(2), 1).orElseThrow();
            TaskResponse.finish(storage, owner, first, TaskResponse.Verdict.UNRELATED, now.plusSeconds(3));
            var last = TaskResponse.claim(storage, owner, speech("Another utterance", owner, true, "two"), now.plusSeconds(4), now.plusSeconds(5), 99).orElseThrow();
            TaskResponse.finish(storage, owner, last, verdict, now.plusSeconds(6));
            assertEquals("UNCERTAIN", TaskResponse.status(storage));
        }
        TaskResponse.expect(storage, owner, response, "A robot joke", now);
        var check = TaskResponse.claim(storage, owner, speech("Unrelated speech", owner, true, "default"), now.plusSeconds(1), now.plusSeconds(2)).orElseThrow();
        TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.UNRELATED, now.plusSeconds(3));
        assertEquals("UNCERTAIN", TaskResponse.status(storage));
        assertEquals(0, new TaskPolicy(storage, "").liveUnrelatedResponseAllowance());
    }
    @Test void queuedCandidateSurvivesReloadButCannotCrossOwnerRevisionOrResponse() {
        var check = TaskResponse.claim(storage, owner, speech("Unrelated speech", owner, true, "one"), now.plusSeconds(1), now.plusSeconds(2), 1).orElseThrow();
        var actual = speech("A complete joke", owner, true, "two");
        assertTrue(TaskResponse.claim(storage, owner, actual, now.plusSeconds(3), now.plusSeconds(4), 1).isEmpty());
        var reload = new Storage(); storage.toMap().forEach((k,v) -> reload.put(k, JsonParser.parseString(v.toString())));
        assertTrue(TaskResponse.finish(reload, owner, check, TaskResponse.Verdict.UNRELATED, now.plusSeconds(5)));
        assertTrue(TaskResponse.takeCandidate(reload, new ExternalSpeech(UUID.randomUUID(), owner.epoch())).isEmpty());
        var candidate = TaskResponse.takeCandidate(reload, owner).orElseThrow();
        assertEquals("two", candidate.segment()); assertEquals(now.plusSeconds(3), candidate.started());
        assertTrue(TaskResponse.takeCandidate(reload, owner).isEmpty());
        TaskMemory.revise(storage);
        assertFalse(TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.UNRELATED, now.plusSeconds(5)));
        TaskResponse.expect(storage, owner, UUID.randomUUID(), "New occasion", now.plusSeconds(6));
        assertTrue(TaskResponse.takeCandidate(storage, owner).isEmpty());
    }
    @Test void invalidIdentityOrExpiredUnrelatedResultCannotExtendTheOccasion() {
        var check = TaskResponse.claim(storage, owner, speech("Unrelated speech", owner, true, "one"), now.plusSeconds(1), now.plusSeconds(2), 1).orElseThrow();
        assertEquals(TaskResponse.Verdict.AMBIGUOUS, check.verdict(result(check, "UNRELATED").replace(check.id().toString(), UUID.randomUUID().toString())));
        assertEquals(TaskResponse.Verdict.AMBIGUOUS, check.verdict(result(check, "INVALID")));
        TaskResponse.finish(storage, owner, check, TaskResponse.Verdict.UNRELATED, now.plusSeconds(45));
        assertEquals("UNCERTAIN", TaskResponse.status(storage)); assertNull(TaskResponse.deadline(storage));
    }
    @Test void committingPurposeSurvivesReloadAndCannotReclassifyAnInFlightCheck() {
        TaskResponse.purpose(storage, TaskResponse.Purpose.CONTRIBUTION);
        var reload = new Storage(); storage.toMap().forEach((k,v) -> reload.put(k, JsonParser.parseString(v.toString())));
        var check = TaskResponse.claim(reload, owner, speech("An actual joke", owner, true, "purpose"), now.plusSeconds(1), now.plusSeconds(2)).orElseThrow();
        TaskResponse.purpose(reload, TaskResponse.Purpose.ARMING);
        assertEquals(TaskResponse.Purpose.CONTRIBUTION, check.purpose());
        var request = JsonParser.parseString(check.request().messages().getLast().getContent()).getAsJsonObject();
        assertEquals("CONTRIBUTION", request.get("responsePurpose").getAsString());
        assertEquals("CONTRIBUTION", reload.get(TaskResponse.KEY).getAsJsonObject().get("purpose").getAsString());
        assertFalse(TaskResponse.captured(reload, owner));
        TaskResponse.expect(reload, owner, UUID.randomUUID(), "A separate legacy occasion", now.plusSeconds(3));
        var legacy = TaskResponse.claim(reload, owner, speech("Another response", owner, true, "legacy"), now.plusSeconds(4), now.plusSeconds(5)).orElseThrow();
        assertEquals(TaskResponse.Purpose.UNSPECIFIED, legacy.purpose());
    }
    @Test void explicitLiteralRequiresEveryWordAndCannotBypassIdentityOrDeadline() {
        TaskResponse.contribution(storage, "Required spoken content: Welcome to the lab.", "Welcome to the lab.");
        var check = TaskResponse.claim(storage, owner, speech("WELCOME, to the lab!", owner, true, "literal"), now.plusSeconds(1), now.plusSeconds(2)).orElseThrow();
        assertTrue(check.exactlyMatches());
        TaskResponse.contribution(storage, "Changed content", "Different words");
        assertEquals("Welcome to the lab.", storage.get(TaskResponse.KEY).getAsJsonObject().get("exactSpeech").getAsString());
        assertFalse(TaskResponse.finish(storage, new ExternalSpeech(UUID.randomUUID(), owner.epoch()), check, true, now.plusSeconds(3)));
        assertTrue(TaskResponse.finish(storage, owner, check, true, now.plusSeconds(45)));
        assertEquals("UNCERTAIN", TaskResponse.status(storage));
        for (String text : List.of("Welcome to lab", "Welcome to the lab later", "I'll say welcome to the lab")) {
            TaskResponse.expect(storage, owner, UUID.randomUUID(), "Current task", now);
            TaskResponse.contribution(storage, "Required spoken content: Welcome to the lab.", "Welcome to the lab.");
            var changed = TaskResponse.claim(storage, owner, speech(text, owner, true, UUID.randomUUID().toString()), now.plusSeconds(1), now.plusSeconds(2)).orElseThrow();
            assertFalse(changed.exactlyMatches(), text);
        }
    }
}
