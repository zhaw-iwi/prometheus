package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

/** Characterization of confirmed defects; each case becomes a desired-contract test in its milestone. */
class CoordinationRefinementBaselineUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();

    @Test void baselineSingleLiveContributionReflectsBeforeItsSpeechIsCaptured() {
        var agreement = f.dialogue(); agreement.addProperty("dialogueLimit", 1);
        f.propose(agreement, f.live); f.start(f.live);
        assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
        assertEquals(1, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertNotNull(f.policy().liveActivityContext().continuation());
    }
}
