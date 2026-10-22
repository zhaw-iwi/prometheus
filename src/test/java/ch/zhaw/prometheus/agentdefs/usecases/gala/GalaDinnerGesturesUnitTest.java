package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;

class GalaDinnerGesturesUnitTest {
    private final Storage storage = new Storage();
    private final Instant now = Instant.parse("2026-10-22T08:00:00Z");
    private BehaviourPlan beat(String label) { return GalaDinnerAction.plan(null, label, "attentive", "toward_user"); }
    private String label(BehaviourPlan plan) { return plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString(); }

    @Test void cadenceSuppressesRepeatedAndNearbyBeatsWithoutChangingOtherChannels() {
        assertEquals("EXPLAIN", label(GalaDinnerGestures.publish(storage, beat("EXPLAIN"), false, now)));
        var original = beat("POLITE");
        var suppressed = GalaDinnerGestures.publish(storage, original, false, now.plusSeconds(11));
        assertEquals("NONE", label(suppressed)); assertEquals("POLITE", label(original));
        assertEquals(original.getNonVerbal().getAsJsonObject().get("gaze"), suppressed.getNonVerbal().getAsJsonObject().get("gaze"));
        assertEquals("POLITE", label(GalaDinnerGestures.publish(storage, original, false, now.plusSeconds(12))));
        assertEquals("NONE", label(GalaDinnerGestures.publish(storage, beat("EXPLAIN"), false, now.plusSeconds(59))));
        assertEquals("EXPLAIN", label(GalaDinnerGestures.publish(storage, beat("EXPLAIN"), false, now.plusSeconds(60))));
    }

    @Test void noneDoesNotSpendCadenceAndRequiredGesturesAreNotSuppressed() {
        GalaDinnerGestures.publish(storage, beat("NONE"), false, now);
        assertFalse(storage.containsKey(GalaDinnerGestures.HISTORY));
        GalaDinnerGestures.publish(storage, beat("EXPLAIN"), false, now);
        assertEquals("EXPLAIN", label(GalaDinnerGestures.publish(storage, beat("EXPLAIN"), true, now.plusSeconds(1))));
        var rps = beat("NONE"); var motion = new com.google.gson.JsonObject(); motion.addProperty("handSign", "rock");
        rps.setMotion(motion);
        assertSame(rps, GalaDinnerGestures.publish(storage, rps, false, now.plusSeconds(2)));
        assertEquals("rock", rps.getMotion().getAsJsonObject().get("handSign").getAsString());
    }

    @Test void historyIsBoundedSurvivesActivityChangesAndClearsForTheEncounter() {
        for (int i = 0; i < 20; i++) GalaDinnerGestures.publish(storage, beat("EXPLAIN"), true, now.plusSeconds(i));
        assertEquals(5, storage.get(GalaDinnerGestures.HISTORY).getAsJsonArray().size());
        GalaDinnerMemory.reception(storage);
        assertTrue(GalaDinnerGestures.guidance(storage, now.plusSeconds(20)).contains("EXPLAIN"));
        assertFalse(GalaDinnerGestures.guidance(storage, now.plusSeconds(80)).contains("EXPLAIN"));
        GalaDinnerMemory.clear(storage); assertFalse(storage.containsKey(GalaDinnerGestures.HISTORY));
    }

    @Test void explicitGestureExemptionDoesNotTreatExamplesOrNegationAsRequests() {
        assertTrue(GalaDinnerGestures.requested("Zeig mir bitte die EXPLAIN Geste"));
        assertTrue(GalaDinnerGestures.requested("Please show that gesture again"));
        assertFalse(GalaDinnerGestures.requested("Don't show that gesture"));
        assertFalse(GalaDinnerGestures.requested("Was wenn ich sage: zeig diese Geste?"));
        assertFalse(GalaDinnerGestures.requested("Explain the gala"));
        assertFalse(GalaDinnerGestures.requested("How do gestures work?"));
    }

    @Test void publicationConsumesThePlanOnceAndReloadCannotReplayIt() {
        GalaDinnerMemory.reply(storage, beat("EXPLAIN"));
        assertFalse(storage.containsKey(GalaDinnerGestures.HISTORY));
        var policy = new GalaDinnerPolicy(storage);
        assertEquals("EXPLAIN", label(policy.onStart(null, null, null, null)));
        assertNull(policy.onStart(null, null, null, null));
        assertNull(new GalaDinnerPolicy(storage).onStart(null, null, null, null));
        assertEquals(1, storage.get(GalaDinnerGestures.HISTORY).getAsJsonArray().size());
        GalaDinnerMemory.put(storage, GalaDinnerMemory.COUNT, "1");
        GalaDinnerMemory.put(storage, GalaDinnerMemory.SEEN, Instant.now().toString());
        policy.beginLiveConversation(new ch.zhaw.prometheus.model.event.EventHistory());
        assertFalse(storage.containsKey(GalaDinnerGestures.HISTORY));
        assertNull(policy.onStart(null, null, null, null));
    }

    @Test void unusableOptionalHistoryCannotBlockValidOutput() {
        storage.put(GalaDinnerGestures.HISTORY, com.google.gson.JsonParser.parseString(
                "[{\"at\":\"2026-10-22T08:00:00Z\"},{\"at\":\"broken\",\"label\":\"EXPLAIN\"}]"));
        assertEquals("EXPLAIN", label(GalaDinnerGestures.publish(storage, beat("EXPLAIN"), false, now)));
        assertEquals(1, storage.get(GalaDinnerGestures.HISTORY).getAsJsonArray().size());
    }
}
