package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationAcceptanceUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void fullySpecifiedExplicitStartNeedsNoSecondConfirmation() {
        f.turn("PROPOSE_AND_START", f.sensor(false), "Start now: offer a welcome idea on rock, finish on paper, limit three, no preparation", f.text);
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertNull(f.sign("rock", 1, java.time.Instant.EPOCH, f.text));
    }
    @Test void immediateStartCannotBypassChecksAndCasualAcknowledgementCannotStart() {
        f.turn("PROPOSE_AND_START", f.sensor(true), "Start now but first check the signs", f.text);
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT));
        var response = f.turn("PROPOSE", f.sensor(false), "Please propose this only", f.text);
        assertTrue(response.getPayload().contains("awaiting your acceptance"));
        assertTrue(response.getPayload().contains("fresh matching cue"));
        assertTrue(CoordinationMemory.acceptancePrompt(f.storage()).endsWith("Shall we start it now?"));
        f.say("Thanks", f.text);
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        f.start(f.text);
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
    }
}
