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
}
