package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.*;
import ch.zhaw.prometheus.model.task.TaskMemory;

class CoordinationRecoveryUnitTest {
    @Test void restartAcceptsOnlyAnExactRedundantEchoAndKeepsTheAcceptedTerms() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
        f.say("Stop the task.", f.text);
        var accepted = f.storage().get(CoordinationMemory.AGREEMENT).getAsJsonObject().deepCopy();
        var oldRun = f.storage().get(CoordinationMemory.RUN).deepCopy();
        f.turn("RESTART", accepted, "Explicitly restart the same agreed task with a fresh budget", f.live);
        assertEquals("RUNNING", TaskMemory.phase(f.storage()));
        assertNotEquals(oldRun, f.storage().get(CoordinationMemory.RUN));
        assertEquals(accepted, f.storage().get(CoordinationMemory.AGREEMENT));
        assertEquals(0, f.storage().get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(f.storage().containsKey(CoordinationMemory.DRAFT));
        assertFalse(f.storage().containsKey(CoordinationRecovery.KEY));
    }

    @Test void restartCannotSmuggleChangedTermsOrTreatAQuestionAsConsent() {
        for (String text : java.util.List.of("Explicitly restart the same task", "Can we restart later?", "Do not restart the task")) {
            var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text); f.start(f.text);
            f.say("Stop the task.", f.text);
            var accepted = f.storage().get(CoordinationMemory.AGREEMENT).getAsJsonObject().deepCopy();
            var echoed = accepted.deepCopy();
            if (text.startsWith("Explicitly")) echoed.addProperty("success", "Changed without agreement");
            var run = f.storage().get(CoordinationMemory.RUN).deepCopy();
            var revision = f.storage().get(TaskMemory.REVISION).deepCopy();
            f.turn("RESTART", echoed, text, f.live);
            assertEquals(run, f.storage().get(CoordinationMemory.RUN));
            assertEquals(revision, f.storage().get(TaskMemory.REVISION));
            assertEquals(accepted, f.storage().get(CoordinationMemory.AGREEMENT));
            assertEquals("COMPLETED", TaskMemory.phase(f.storage()));
            assertEquals("CANCELLED", f.storage().get(CoordinationMemory.OUTCOME).getAsString());
            assertNotNull(CoordinationRecovery.current(f.storage(), Instant.now()));
        }
    }
    @Test void rejectedProviderOutputPreservesTermsAndRecordsOnlySafeFacts() {
        var f = new CoordinationAgentUnitTest(); f.propose(f.sensor(false), f.text);
        var draft = f.storage().get(CoordinationMemory.DRAFT).deepCopy();
        doReturn("private malformed provider text").when(f.gateway).infer(any());
        f.say("Begin our saved activity", f.text);
        var fact = CoordinationRecovery.current(f.storage(), Instant.now());
        assertNotNull(fact); assertEquals("update_shape", fact.get("category").getAsString());
        assertEquals("UNKNOWN", fact.get("operation").getAsString());
        assertFalse(fact.get("mutationCommitted").getAsBoolean());
        assertFalse(fact.toString().contains("private"));
        assertEquals(draft, f.storage().get(CoordinationMemory.DRAFT));
        assertEquals("READY", CoordinationMemory.stage(f.storage()));
        assertFalse(f.storage().containsKey(TaskMemory.ACTIONS));
        f.say("Start", f.text);
        assertFalse(f.storage().containsKey(CoordinationRecovery.KEY));
        assertEquals("EXECUTION", CoordinationMemory.stage(f.storage()));
    }

    @Test void unknownOperationsExceptionsAndRepeatedFailuresCannotLeakContentOrGrowHistory() {
        var f = new CoordinationAgentUnitTest();
        doThrow(new IllegalStateException("secret transport detail")).when(f.gateway).infer(any());
        for (int i = 0; i < 30; i++) f.say("Explain this observation", f.text);
        var fact = CoordinationRecovery.current(f.storage(), Instant.now());
        assertNotNull(fact); assertEquals("inference_failed", fact.get("category").getAsString());
        assertFalse(fact.toString().contains("secret")); assertTrue(fact.toString().length() < 600);
    }

    @Test void obsoleteExpiredLegacyAndResetFactsDoNotDiagnoseCurrentWork() {
        var storage = new Storage(); Instant at = Instant.parse("2026-11-10T10:00:00Z");
        assertNull(CoordinationRecovery.current(storage, at));
        CoordinationRecovery.rejected(storage, "private", "private", null, at);
        var fact = CoordinationRecovery.current(storage, at);
        assertEquals("unknown", fact.get("category").getAsString());
        assertNull(CoordinationRecovery.current(storage, at.minusSeconds(1)));
        assertNull(CoordinationRecovery.current(storage, at.plusSeconds(CoordinationRecovery.TTL_SECONDS)));
        storage.put(TaskMemory.REVISION, new JsonPrimitive(1));
        assertNull(CoordinationRecovery.current(storage, at));
        CoordinationRecovery.rejected(storage, "operation_constraints", "START", null, at);
        storage.put(CoordinationMemory.RUN, new JsonPrimitive("new-run"));
        assertNull(CoordinationRecovery.current(storage, at));
        CoordinationRecovery.rejected(storage, "operation_constraints", "START", null, at);
        storage.put(TaskMemory.PHASE, new JsonPrimitive("PAUSED"));
        assertNull(CoordinationRecovery.current(storage, at));
        CoordinationMemory.clear(storage); assertFalse(storage.containsKey(CoordinationRecovery.KEY));
        storage.put(CoordinationRecovery.KEY, new JsonPrimitive("legacy invalid record"));
        assertNull(CoordinationRecovery.current(storage, at));
    }
}
