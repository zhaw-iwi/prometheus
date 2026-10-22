package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

class GalaDinnerHandSignsUnitTest {
    private final Instant now = Instant.parse("2026-10-22T08:00:00Z"), started = now.minusSeconds(5);
    private JsonObject sample(double confidence, Instant at) {
        var data = new JsonObject(); data.addProperty("sign", "paper"); data.addProperty("confidence", confidence);
        data.addProperty("ts", at.toString()); data.addProperty("source", "valerian.hand.camera");
        data.addProperty("stabilityFrames", 3); return data;
    }
    @ParameterizedTest @CsvSource({"0.65,rps_low_confidence", "0.699,rps_low_confidence", "0.7,ACCEPT", "1.0,ACCEPT", "1.01,rps_low_confidence"})
    void confidenceIsAnAcceptanceBoundaryRatherThanAnEmissionClaim(double confidence, String reason) {
        assertEquals("ACCEPT".equals(reason) ? null : reason, GalaDinnerHandSigns.rejection(sample(confidence, now), "", started, now, null));
    }
    @Test void missingConfidenceAndUnstableCameraEvidenceAreNotAccepted() {
        var data = sample(.9, now); data.remove("confidence");
        assertEquals("rps_low_confidence", GalaDinnerHandSigns.rejection(data, "", started, now, null));
        data = sample(.9, now); data.addProperty("stabilityFrames", 2);
        assertEquals("rps_unstable_sign", GalaDinnerHandSigns.rejection(data, "", started, now, null));
        data.remove("stabilityFrames");
        assertEquals("rps_unstable_sign", GalaDinnerHandSigns.rejection(data, "", started, now, null));
        data.addProperty("source", "valerian.hand.manual");
        assertNull(GalaDinnerHandSigns.rejection(data, "", started, now, null)); // Explicit operator injection has no camera-frame count.
    }
    @Test void timestampGatesExcludePreStartStaleMissingFutureAndOutOfOrderSamples() {
        for (Instant at : java.util.List.of(started.minusMillis(1), now.minusSeconds(16), now.plusSeconds(3)))
            assertEquals("rps_stale_sign", GalaDinnerHandSigns.rejection(sample(.9, at), "", started, now, null));
        var data = sample(.9, now); data.remove("ts");
        assertEquals("rps_stale_sign", GalaDinnerHandSigns.rejection(data, "", started, now, null));
        assertEquals("rps_out_of_order", GalaDinnerHandSigns.rejection(sample(.9, now.minusSeconds(1)), "", started, now, now));
    }
    @Test void captureIdentityRequiresAnExactStringAndCannotPoisonOrderingInAnotherCapture() {
        var data = sample(.9, now);
        assertEquals("rps_wrong_capture", GalaDinnerHandSigns.rejection(data, "current", started, now, null));
        for (var value : java.util.List.of(new com.google.gson.JsonPrimitive(123),
                new com.google.gson.JsonPrimitive("old"), com.google.gson.JsonNull.INSTANCE, new com.google.gson.JsonArray())) {
            data.add("captureId", value);
            assertEquals("rps_wrong_capture", GalaDinnerHandSigns.rejection(data, "current", started, now, null));
        }
        data.addProperty("captureId", "current");
        assertNull(GalaDinnerHandSigns.rejection(data, "current", started, now, null));
        var history = new EventHistory();
        var olderCapture = sample(.99, now.plusSeconds(1)); olderCapture.addProperty("captureId", "old");
        history.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "sensor", olderCapture.toString()));
        history.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "sensor", data.toString()));
        assertEquals(started, GalaDinnerHandSigns.newestPrior(history, "current", started, now));
    }

    @Test void outOfOrderTrackingReusesHistoryWithoutRecordingAnotherSample() {
        var history = new EventHistory();
        for (Instant at : java.util.List.of(now.minusSeconds(1), now.minusSeconds(3), now.minusSeconds(2)))
            history.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, "sensor", sample(.65, at).toString()));
        assertEquals(now.minusSeconds(1), GalaDinnerHandSigns.newestPrior(history, "", started, now));
        assertEquals(3, history.toList().size());
    }
}
