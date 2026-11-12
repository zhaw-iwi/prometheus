package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationAcceptanceUnitTest {
    final CoordinationAgentUnitTest f = new CoordinationAgentUnitTest();
    @Test void combinedProposalCannotOverrideAnExplicitInstructionNotToStart() {
        for (String request : java.util.List.of("Propose an invitation task but do not start yet",
                "Don't start now; just propose the task", "Bitte nicht starten; schlage eine Aufgabe vor")) {
            var fixture = new CoordinationAgentUnitTest();
            fixture.turn("PROPOSE_AND_START", fixture.dialogue(), request, fixture.text);
            assertFalse(fixture.storage().containsKey(CoordinationMemory.AGREEMENT), request);
            assertFalse(fixture.storage().containsKey(TaskMemory.ACTIONS), request);
            assertEquals("CONVERSATION", CoordinationMemory.stage(fixture.storage()), request);
        }
        var allowed = new CoordinationAgentUnitTest();
        allowed.turn("PROPOSE_AND_START", allowed.sensor(false), "Start now: when I show rock, offer a welcome idea; finish on paper", allowed.text);
        assertEquals("EXECUTION", CoordinationMemory.stage(allowed.storage()));
        assertEquals(0, allowed.storage().get(TaskMemory.ACTIONS).getAsInt());
    }
    @Test void explicitAcceptanceOfReadySensorProposalDoesNotDependOnModelOutput() {
        for (String command : java.util.List.of("Yes, let's start now.", "Run it", "Please start the saved proposal now",
                "Let's start", "let start", "lets start", "Ja, lass uns starten", "Bitte jetzt starten", "I accept")) {
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
    @Test void explicitDialogueAcceptanceOwnsStartWhileInferenceSuppliesTheFirstContribution() {
        for (String operation : java.util.List.of("START", "STEP")) {
            var fixture = new CoordinationAgentUnitTest(); fixture.propose(fixture.dialogue(), fixture.live);
            var draft = fixture.storage().get(CoordinationMemory.DRAFT).deepCopy();
            clearInvocations(fixture.gateway);
            fixture.turn(operation, null, "Let\u2019s start", fixture.live);
            assertEquals("EXECUTION", CoordinationMemory.stage(fixture.storage()), operation);
            assertEquals(draft, fixture.storage().get(CoordinationMemory.AGREEMENT));
            assertEquals(1, fixture.storage().get(TaskMemory.ACTIONS).getAsInt());
            assertEquals("START", fixture.storage().get(CoordinationMemory.LOG).getAsJsonArray().asList().getLast().getAsJsonObject().get("operation").getAsString());
            verify(fixture.gateway, times(1)).infer(any());
        }
    }
    @Test void firstContributionCannotManufactureAcceptanceOrReplaceExplicitlyAcceptedTerms() {
        for (String command : java.util.List.of("Let's start?", "Don't start", "Start tomorrow", "\"Start\"", "My answer is")) {
            var fixture = new CoordinationAgentUnitTest(); fixture.propose(fixture.dialogue(), fixture.live);
            fixture.turn("STEP", null, command, fixture.live);
            assertEquals("READY", CoordinationMemory.stage(fixture.storage()), command);
            assertFalse(fixture.storage().containsKey(CoordinationMemory.AGREEMENT), command);
            assertFalse(fixture.storage().containsKey(TaskMemory.ACTIONS), command);
        }
        var fixture = new CoordinationAgentUnitTest(); fixture.propose(fixture.dialogue(), fixture.live);
        var saved = fixture.storage().get(CoordinationMemory.DRAFT).deepCopy();
        fixture.turn("REPLACE", fixture.sensor(false), "Let's start", fixture.live);
        assertEquals(saved, fixture.storage().get(CoordinationMemory.DRAFT));
        assertFalse(fixture.storage().containsKey(CoordinationMemory.AGREEMENT));
    }
    @Test void readyNeedsARecentCompletedInvitationAndRepeatedStartsCannotResetTheRun() {
        var ready = new CoordinationAgentUnitTest(); ready.propose(ready.sensor(false), ready.text);
        org.springframework.test.util.ReflectionTestUtils.setField(ready.agent.getEventHistory().toList().getLast(),
                "createdDate", java.time.Instant.now());
        clearInvocations(ready.gateway); ready.say("Bereit", ready.text);
        assertEquals("EXECUTION", CoordinationMemory.stage(ready.storage())); verifyNoInteractions(ready.gateway);
        var run = ready.storage().get(CoordinationMemory.RUN).deepCopy();
        ready.turn("START", null, "Start", ready.text);
        assertEquals(run, ready.storage().get(CoordinationMemory.RUN));
        assertEquals(0, ready.storage().get(TaskMemory.ACTIONS).getAsInt());
        var expired = new CoordinationAgentUnitTest(); expired.propose(expired.sensor(false), expired.text);
        expired.storage().put(CoreTaskVoice.INVITATION, new com.google.gson.JsonPrimitive(java.time.Instant.now().minusSeconds(121).toString()));
        clearInvocations(expired.gateway); expired.say("Bereit", expired.text);
        assertEquals("READY", CoordinationMemory.stage(expired.storage())); verifyNoInteractions(expired.gateway);
        var noPrompt = new CoordinationAgentUnitTest(); noPrompt.propose(noPrompt.sensor(false), noPrompt.live);
        noPrompt.say("Bereit", noPrompt.live); assertEquals("READY", CoordinationMemory.stage(noPrompt.storage()));
    }
    @Test void explicitSensorAcceptanceStillRequiresPreparationAndDoesNotTreatFutureIntentAsConsent() {
        f.propose(f.sensor(true), f.live); var saved = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        clearInvocations(f.gateway); f.say("Yes, let's start now", f.live);
        assertEquals("PREPARATION", CoordinationMemory.stage(f.storage()));
        assertEquals(saved, f.storage().get(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(CoordinationMemory.AGREEMENT)); verifyNoInteractions(f.gateway);
        var ready = new CoordinationAgentUnitTest(); ready.propose(ready.sensor(false), ready.live);
        for (String uncertain : java.util.List.of("Let's start tomorrow, not now", "Run it?", "\"Run it\"", "Don't start now", "yes?", "\"yes\"", "Bitte nicht starten")) {
            ready.turn("START", null, uncertain, ready.live);
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
