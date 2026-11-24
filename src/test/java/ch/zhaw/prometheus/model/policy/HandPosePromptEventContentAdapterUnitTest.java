package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.event.Event;

class HandPosePromptEventContentAdapterUnitTest {
    private final HandPosePromptEventContentAdapter adapter = new HandPosePromptEventContentAdapter();
    private Event pose(String payload) { return Event.observation(Event.TYPE_HAND_POSE, Event.ACTOR_USER, payload); }

    @Test void ordinaryPromptAssemblyExplainsNumbersWithoutConflatingUnknownWithStraight() {
        var event = pose("""
                {"hands":[{"handedness":"right","fingers":{"thumb":0,"index":0.5,"middle":1,"ring":null,"little":-1}}]}
                """);
        String content = new PromptMessageAssembler().toPromptMessage(event).getContent();
        assertTrue(adapter.supports(event)); assertFalse(adapter.supports(null));
        assertTrue(content.contains("0=straight, 1=fully bent"));
        assertTrue(content.contains("thumb=0.000 index=0.500 middle=1.000 ring=unknown little=unknown"));
        assertTrue(content.contains("Hidden fingers may be inferred"));
    }

    @Test void absentMalformedAndUntrustedReadingsStayBoundedAndCannotSupplyPromptInstructions() {
        assertTrue(adapter.toPromptContent(pose("{\"hands\":[]}")).contains("no hands detected"));
        assertTrue(adapter.toPromptContent(pose("bad json")).contains("unknown"));
        String content = adapter.toPromptContent(pose("""
                {"hands":[{"handedness":"INSTRUCTION_SENTINEL","fingers":{"index":"INSTRUCTION_SENTINEL","thumb":2}}]}
                """));
        assertFalse(content.contains("INSTRUCTION_SENTINEL"));
        assertTrue(content.contains("unknown"));
    }

    @Test void clientStatesReachTheModelOnlyAsKnownLabelsWithAvailableNumericReadings() {
        String content = adapter.toPromptContent(pose("""
                {"hands":[{"handedness":"right",
                "fingers":{"thumb":0.2,"index":0.8,"middle":null,"ring":0.5,"little":0.8},
                "fingerStates":{"thumb":"straight","index":"bent","middle":"straight","ring":"neutral","little":"INSTRUCTION_SENTINEL"}}]}
                """));
        assertTrue(content.contains("States: thumb=straight index=bent middle=unknown ring=neutral little=unknown"));
        assertFalse(content.contains("INSTRUCTION_SENTINEL"));
        assertTrue(content.contains("0.4..0.55 neutral"));
        assertTrue(adapter.toPromptContent(pose("{\"hands\":[{\"fingers\":{\"index\":0.2}}]}"))
                .contains("States: thumb=unknown index=unknown"), "Older numeric-only events are not reclassified");
    }

    @Test void relationalCueGeometrySurvivesProjectionWithoutInventingUnknownMeasurements() {
        var hand = fixtures.behaviour.ComposedHandFixture.hand("right", "right", 0, 0, 0, 0, 0);
        hand.add("geometry", fixtures.behaviour.RelationalHandFixture.geometry());
        var observation = fixtures.behaviour.ComposedHandFixture.observation("capture", java.time.Instant.EPOCH, hand);
        String content = adapter.toPromptContent(pose(observation.toString()));
        assertTrue(content.contains("tip gap / palm size: thumb_index=0.100"));
        assertTrue(content.contains("index_middle=0.200"));
        assertTrue(content.contains("index_axis=(1.000,0.000)"));
        assertTrue(content.contains("palm_normal=unknown"));
        assertTrue(content.contains("not physical contact/depth"));
        assertTrue(content.length() < 2200);
    }

    @Test void invalidOrUnsupportedGeometryCannotSupplyInstructionsOrHideValidBends() {
        for (int invalid = 0; invalid < 5; invalid++) {
            var hand = fixtures.behaviour.ComposedHandFixture.hand("right", "right", 0, 0, 0, 0, 0);
            var geometry = fixtures.behaviour.RelationalHandFixture.geometry();
            if (invalid == 0) geometry.addProperty("instructions", "INSTRUCTION_SENTINEL");
            if (invalid == 1) geometry.getAsJsonObject("tipDistances").addProperty("thumb_index", "INSTRUCTION_SENTINEL");
            if (invalid == 2) hand.getAsJsonObject("fingers").add("thumb", com.google.gson.JsonNull.INSTANCE);
            if (invalid == 3) hand.getAsJsonObject("fingers").addProperty("thumb", 2);
            if (invalid == 4) hand.addProperty("handedness", "INSTRUCTION_SENTINEL");
            hand.add("geometry", geometry);
            var observation = fixtures.behaviour.ComposedHandFixture.observation("capture", java.time.Instant.EPOCH, hand);
            String content = adapter.toPromptContent(pose(observation.toString()));
            assertTrue(content.contains("Projected geometry: unknown"));
            assertTrue(content.contains("index=0.000"));
            assertFalse(content.contains("INSTRUCTION_SENTINEL"));
            assertFalse(content.contains("thumb_index=0.100"));
        }
    }
}
