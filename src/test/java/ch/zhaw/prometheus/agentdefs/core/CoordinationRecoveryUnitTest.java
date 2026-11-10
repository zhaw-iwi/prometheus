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
