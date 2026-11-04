package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.event.Event;

class TaskHandConfidenceUnitTest {
    private final Instant now = Instant.parse("2026-11-04T15:39:09Z");
    private TaskSpec.Rule rule(double minimum) {
        return new TaskSpec.Rule(Event.TYPE_HAND_SIGN, "sign", "eq", new JsonPrimitive("scissor"),
                "Tell one joke", TaskSpec.Effect.ACT, minimum, 1, 3);
    }
    private Event hand(String confidence, String sign, Instant at) {
        var payload = new JsonObject(); payload.addProperty("sign", sign); payload.addProperty("ts", at.toString());
        if (!confidence.equals("missing")) payload.add("confidence", JsonParser.parseString(confidence));
        return Event.observation(Event.TYPE_HAND_SIGN, "camera", payload.toString());
    }
    @Test void clientFilteredScoresNeedNoSecondFloorButExplicitMinimumStillApplies() {
        for (String score : java.util.List.of("0", "0.65", "0.757", "1"))
            assertTrue(TaskDecision.matchesObservation(rule(0), hand(score, "scissor", now), now));
        assertFalse(TaskDecision.matchesObservation(rule(.8), hand("0.757", "scissor", now), now));
        assertTrue(TaskDecision.matchesObservation(rule(.8), hand("0.8", "scissor", now), now));
        assertFalse(TaskDecision.matchesObservation(rule(0), hand("0.757", "rock", now), now));
        assertFalse(TaskDecision.matchesObservation(rule(0), hand("0.757", "scissor", now.minusSeconds(30)), now));
        assertFalse(TaskDecision.matchesObservation(rule(0), hand("0.757", "scissor", now.plusSeconds(30)), now));
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "\"0.9\"", "true", "{}", "[]", "-0.1", "1.1", "1e309", "\"NaN\""})
    void noExtraFloorStillRejectsMissingMalformedOrOutOfRangeHandConfidence(String score) {
        assertFalse(TaskDecision.matchesObservation(rule(0), hand(score, "scissor", now), now));
    }
}
