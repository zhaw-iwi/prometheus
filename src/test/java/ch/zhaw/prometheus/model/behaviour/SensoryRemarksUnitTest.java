package ch.zhaw.prometheus.model.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;

class SensoryRemarksUnitTest {
    final Instant now = Instant.parse("2026-10-26T12:00:00Z");
    final Storage storage = new Storage(); final EventHistory history = new EventHistory();
    Event group(int n, long ms) { return event(Event.TYPE_SOCIAL_GROUPING, "\"humanCount\":" + n, ms); }
    Event face(String expression, long ms) { return event(Event.TYPE_FACE_EMOTION, "\"emotion\":\"" + expression + "\",\"confidence\":0.9", ms); }
    Event event(String type, String fields, long ms) {
        var e = Event.observation(type, "sensor", "{" + fields + ",\"ts\":\"" + now.plusMillis(ms) + "\"}"); history.appendEvent(e); return e;
    }
    Optional<SensoryRemarks.Change> change(long ms) { return SensoryRemarks.change(history, storage, now.plusMillis(ms), 2000, 4000, 15000); }

    @Test void countFluctuationsNeedStabilityAndRefreshCannotRepeatOrQueueSuppressedChanges() {
        group(1, 0); assertTrue(change(0).isEmpty());
        group(0, 500); assertTrue(change(500).isEmpty());
        group(1, 1000); assertTrue(change(1000).isEmpty());
        group(1, 2999); assertTrue(change(2999).isEmpty());
        group(1, 3000); assertTrue(change(3000).isPresent()); SensoryRemarks.attempted(storage, now.plusMillis(3000));
        group(1, 7000); assertTrue(change(7000).isEmpty());
        group(2, 8000); group(2, 10000); assertTrue(change(10000).isEmpty());
        group(2, 19000); assertTrue(change(19000).isEmpty(), "No replay after cooldown");
        group(3, 20000); group(3, 22000); assertTrue(change(22000).isPresent());
    }

    @Test void unknownInitialAbsenceOldDuplicateFutureOrInvalidSamplesDoNotAuthorizeSpeech() {
        group(0, 0); group(0, 2000); assertTrue(change(2000).isEmpty());
        history.reset(); group(1, -16000); assertTrue(change(0).isEmpty());
        history.reset(); group(1, 0); group(1, 0); assertTrue(change(2000).isEmpty());
        history.reset(); group(1, 3000); assertTrue(change(0).isEmpty());
        history.reset(); event(Event.TYPE_SOCIAL_GROUPING, "\"humanCount\":\"1\"", 0); assertTrue(change(0).isEmpty());
    }

    @Test void expiringLiveOccasionIsSupersededAndUserCorrectionDisablesFacialCommentsUntilResumed() {
        face("sad", 0); face("sad", 2000);
        var change = change(2000).orElseThrow(); SensoryRemarks.occasion(storage, change, now.plusMillis(2000));
        var context = SensoryRemarks.context(storage, now.plusMillis(2000)); assertTrue(context.continuation().interruptible());
        assertNull(SensoryRemarks.context(storage, now.plusSeconds(8)));
        SensoryRemarks.supersede(storage, face("happy", 3000), now.plusMillis(3000)); assertNull(SensoryRemarks.context(storage, now.plusMillis(3000)));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "I am not sad")); SensoryRemarks.userTurn(storage, history);
        assertTrue(storage.containsKey(SensoryRemarks.CORRECTION));
        face("neutral", 4000); face("neutral", 6000); assertTrue(change(6000).isEmpty());
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Resume facial comments")); SensoryRemarks.userTurn(storage, history);
        assertFalse(storage.containsKey(SensoryRemarks.CORRECTION));
        SensoryRemarks.reset(storage); assertFalse(storage.containsKey(SensoryRemarks.LAST));
    }

    @Test void richSocialSignaturesIgnoreTrackerIdsButNoticeConfidentMovementAndAttentionChanges() {
        String basic = "\"humanCount\":1,\"people\":[{\"id\":\"%s\",\"movement\":{\"state\":\"%s\",\"confidence\":%s}}]";
        var a = SensoryRemarks.facts(event(Event.TYPE_SOCIAL_CONTEXT, basic.formatted("a", "moving", ".8"), 0), now);
        var b = SensoryRemarks.facts(event(Event.TYPE_SOCIAL_CONTEXT, basic.formatted("b", "moving", ".9"), 0), now);
        assertEquals(a, b);
        var c = SensoryRemarks.facts(event(Event.TYPE_SOCIAL_CONTEXT, basic.formatted("b", "stationary", ".9"), 0), now);
        assertNotEquals(a, c);
        assertNull(SensoryRemarks.facts(event(Event.TYPE_FACE_EMOTION, "\"emotion\":\"sad\",\"confidence\":0.3", 0), now));
    }

    @Test void recentConversationSuppressesTheCrossingWithoutDeferringItAndExpiryNeverRefreshesSourceAge() {
        group(1, 0);
        var user = Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Tell me about the lab");
        var saved = history.appendEvent(user);
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "createdDate", now.plusMillis(1000));
        group(1, 2000);
        assertTrue(change(2000).isEmpty());
        group(1, 6000); assertTrue(change(6000).isEmpty());
        var c = new SensoryRemarks.Change(Event.TYPE_FACE_EMOTION, JsonParser.parseString("{\"expression\":\"happy\"}").getAsJsonObject(), now.minusSeconds(14), "source");
        SensoryRemarks.occasion(storage, c, now);
        assertNotNull(SensoryRemarks.context(storage, now));
        assertNull(SensoryRemarks.context(storage, now.plusSeconds(1)));
    }
}
