package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationCaptureUnitTest {
    @ParameterizedTest @ValueSource(strings = {"A dry", "Tell me a", "Make it a", "Can you"})
    void unfinishedInputCannotReviseOrExecuteEvenIfProviderWouldCompleteIt(String fragment) {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        String revision = TaskMemory.text(f.storage(), TaskMemory.REVISION, "0");
        clearInvocations(f.gateway);
        var response = f.turn("PROPOSE", f.dialogue(), fragment, f.text);
        assertTrue(response.getPayload().contains("finish that request"));
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertEquals(revision, TaskMemory.text(f.storage(), TaskMemory.REVISION, "0"));
        verifyNoInteractions(f.gateway);
        f.say("Stop", f.text); assertEquals("REFLECTION", CoordinationMemory.stage(f.storage()));
    }
}
