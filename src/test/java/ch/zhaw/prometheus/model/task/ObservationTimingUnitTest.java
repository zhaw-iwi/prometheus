package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.event.*;

class ObservationTimingUnitTest {
    static JsonObject fixture() throws Exception {
        try (var input = ObservationTimingUnitTest.class.getResourceAsStream("/coordination/observation-timing.json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void recordedClockLeadRejectsAnOtherwiseValidScissorsObservation() throws Exception {
        var data = fixture();
        Instant received = Instant.parse(data.get("serverReceiptTime").getAsString());
        var payload = data.getAsJsonObject("observation");
        var rule = new Gson().fromJson(data.get("rule"), TaskSpec.Rule.class);
        var original = Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER, payload.toString());
        assertFalse(TaskMemory.fresh(original, received));
        assertFalse(TaskDecision.matchesObservation(rule, original, received));
        // Characterize the cause, without modifying the production tolerance or refreshing at receipt.
        var calibrated = payload.deepCopy();
        calibrated.addProperty("ts", data.get("serverCaptureTime").getAsString());
        var corrected = Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER, calibrated.toString());
        assertTrue(TaskDecision.matchesObservation(rule, corrected, received));
        assertFalse(TaskDecision.matchesObservation(rule, corrected, received.plusSeconds(16)));
        assertEquals(data.get("browserSourceTime").getAsString(), payload.get("ts").getAsString());
    }
    @Test void expiryAndFutureBoundariesAreExplicitAndReceiptDoesNotRenewAge() {
        Instant now = Instant.parse("2026-11-04T12:17:19Z");
        assertTrue(ObservationEvidence.fresh(now.minusSeconds(15).plusNanos(1), now, 15, 2));
        assertFalse(ObservationEvidence.fresh(now.minusSeconds(15), now, 15, 2));
        assertTrue(ObservationEvidence.fresh(now.plusSeconds(2), now, 15, 2));
        assertFalse(ObservationEvidence.fresh(now.plusSeconds(2).plusNanos(1), now, 15, 2));
        assertFalse(ObservationEvidence.fresh(null, now, 15, 2));
        assertFalse(ObservationEvidence.fresh(now.plusNanos(1), now, 15, 0));
    }
}
