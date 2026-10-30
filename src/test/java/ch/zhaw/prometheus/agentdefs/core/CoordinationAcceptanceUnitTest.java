package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationAcceptanceUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void explicitAcceptanceOfReadySensorProposalDoesNotDependOnModelOutput() {
        for (String command : java.util.List.of("Yes, let's start now.", "Run it", "Please start the saved proposal now")) {
            var fixture = new CoordinationAgentUnitTest(); fixture.propose(fixture.sensor(false), fixture.live);
            var saved = fixture.storage().get(CoordinationMemory.DRAFT).deepCopy();
            clearInvocations(fixture.gateway);
            doThrow(new IllegalStateException("Inference must not be required to arm this unchanged proposal")).when(fixture.gateway).infer(any());
            fixture.say(command, fixture.live);
            assertEquals("EXECUTION", CoordinationMemory.stage(fixture.storage()), command);
            assertEquals(saved, fixture.storage().get(CoordinationMemory.AGREEMENT));
            assertEquals(0, fixture.storage().get(TaskMemory.ACTIONS).getAsInt());
            verifyNoInteractions(fixture.gateway);
        }
    }
    @Test void explicitSensorAcceptanceStillRequiresPreparationAndDoesNotTreatFutureIntentAsConsent() {
        f.propose(f.sensor(true), f.live); var saved = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        clearInvocations(f.gateway); f.say("Yes, let's start now", f.live);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertEquals(saved, f.storage().get(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT)); verifyNoInteractions(f.gateway);
        var ready = new CoordinationAgentUnitTest(); ready.propose(ready.sensor(false), ready.live);
        for (String uncertain : java.util.List.of("Let's start tomorrow, not now", "Run it?", "\"Run it\"", "Don't start now")) {
            ready.turn("KEEP", null, uncertain, ready.live);
            assertEquals("READY", CoordinationMemory.stage(ready.storage()), uncertain);
            assertFalse(ready.storage().containsKey(CoordinationMemory.AGREEMENT));
        }
    }
    @Test void rejectedInferenceReportsCategoryWithoutItsPrivateOutput() {
        f.propose(f.sensor(false), f.live);
        var sink = mock(ch.zhaw.prometheus.logging.ActivityTrace.Sink.class);
        doReturn("private invalid response").when(f.gateway).infer(any());
        try (var trace = ch.zhaw.prometheus.logging.ActivityTrace.attach(sink)) { f.say("Please begin our current activity", f.live); }
        verify(sink).rejected("update_shape", "UNKNOWN", "READY");
        verify(sink).failed("coordination_update_invalid");
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
    }
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
