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
}
