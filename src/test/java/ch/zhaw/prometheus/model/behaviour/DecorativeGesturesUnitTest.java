package ch.zhaw.prometheus.model.behaviour;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;

class DecorativeGesturesUnitTest {
    final Storage storage = new Storage();
    final Instant now = Instant.parse("2026-10-26T10:00:00Z");
    BehaviourPlan plan(String label) { return BehaviourPlan.fromJson("{\"speech\":\"Hello\",\"nonVerbal\":{\"gesture\":\"" + label
            + "\",\"gaze\":{\"direction\":\"toward_user\"}},\"motion\":{\"handSign\":\"paper\"},\"display\":{\"text\":\"Hello\"}}"); }
    String label(BehaviourPlan plan) { return plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString(); }

    @Test void spacingAndSameLabelBoundariesPreserveOtherChannelsAndOriginalPlan() {
        DecorativeGestures.publish(storage, plan("EXPLAIN"), false, now);
        var original = plan("POLITE");
        var filtered = DecorativeGestures.publish(storage, original, false, now.plusSeconds(11));
        assertEquals("NONE", label(filtered)); assertEquals("POLITE", label(original));
        var expected = original.toJsonObject(); expected.getAsJsonObject("nonVerbal").addProperty("gesture", "NONE");
        assertEquals(expected, filtered.toJsonObject());
        assertEquals("POLITE", label(DecorativeGestures.publish(storage, original, false, now.plusSeconds(12))));
        assertEquals("NONE", label(DecorativeGestures.publish(storage, plan("EXPLAIN"), false, now.plusSeconds(59))));
        assertEquals("EXPLAIN", label(DecorativeGestures.publish(storage, plan("EXPLAIN"), false, now.plusSeconds(60))));
    }

    @Test void exemptionsAndNoneDoNotLoseMotionOrSpendDecorativeBudget() {
        var quiet = plan("NONE");
        assertSame(quiet, DecorativeGestures.publish(storage, quiet, false, now));
        assertFalse(storage.containsKey(DecorativeGestures.HISTORY));
        DecorativeGestures.publish(storage, plan("EXPLAIN"), false, now);
        assertEquals("EXPLAIN", label(DecorativeGestures.publish(storage, plan("EXPLAIN"), true, now.plusSeconds(1))));
        var sign = plan("rock"); assertSame(sign, DecorativeGestures.publish(storage, sign, false, now.plusSeconds(2)));
        assertEquals(2, storage.get(DecorativeGestures.HISTORY).getAsJsonArray().size());
    }

    @Test void boundedSerializableHistoryExpiresAndIgnoresInvalidOrFutureEntries() {
        storage.put(DecorativeGestures.HISTORY, JsonParser.parseString("[{\"label\":\"EXPLAIN\",\"at\":\"broken\"},{\"label\":\"POLITE\",\"at\":\"2099-01-01T00:00:00Z\"}]"));
        for (int i = 0; i < 20; i++) DecorativeGestures.publish(storage, plan("EXPLAIN"), true, now.plusSeconds(i));
        assertEquals(5, storage.get(DecorativeGestures.HISTORY).getAsJsonArray().size());
        var reloaded = new Storage(); reloaded.put(DecorativeGestures.HISTORY, JsonParser.parseString(storage.get(DecorativeGestures.HISTORY).toString()));
        assertEquals("NONE", label(DecorativeGestures.publish(reloaded, plan("EXPLAIN"), false, now.plusSeconds(20))));
        assertEquals("EXPLAIN", label(DecorativeGestures.publish(reloaded, plan("EXPLAIN"), false, now.plusSeconds(80))));
        DecorativeGestures.clear(reloaded); assertFalse(reloaded.containsKey(DecorativeGestures.HISTORY));
    }

    @Test void onlyCurrentExplicitRequestsAreExemptNotNegationExamplesOrOldRequests() {
        for (String text : java.util.List.of("Please show that gesture again", "Could you nod again?", "Zeig mir bitte die EXPLAIN Geste"))
            assertTrue(DecorativeGestures.requested(text), text);
        for (String text : java.util.List.of("Don't show that gesture", "If I ask you to show a gesture", "How do gestures work?", "Explain this"))
            assertFalse(DecorativeGestures.requested(text), text);
        var history = new EventHistory(); history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Please show that gesture again"));
        assertTrue(DecorativeGestures.requested(history));
        history.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor", "{}"));
        assertFalse(DecorativeGestures.requested(history));
    }
}
