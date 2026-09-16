package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;

class AgentTurnSerialiserUnitTest {
    @Test void holdsThroughTransactionCompletionAndCleansUpReentrantReferences() {
        var turns = new AgentTurnSerialiser(); UUID id = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals("ok", turns.call(id, () -> turns.call(id, () -> "ok")));
            assertEquals(1, turns.retainedAgents());
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(2, callbacks.size());
            callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            assertEquals(0, turns.retainedAgents());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void interruptedWaitAndWorkFailureReleaseReferences() throws Exception {
        var turns = new AgentTurnSerialiser(); UUID id = UUID.randomUUID();
        var attempted = new CountDownLatch(1); var finished = new CountDownLatch(1); var restored = new AtomicBoolean();
        turns.call(id, () -> {
            Thread contender = new Thread(() -> {
                attempted.countDown();
                try { turns.call(id, () -> fail("overlapping turn")); }
                catch (IllegalStateException stopped) { restored.set(Thread.currentThread().isInterrupted()); }
                finally { finished.countDown(); }
            });
            contender.start();
            try { assertTrue(attempted.await(5, TimeUnit.SECONDS)); contender.interrupt(); assertTrue(finished.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException unexpected) { throw new AssertionError(unexpected); }
            assertEquals(1, turns.retainedAgents());
            return true;
        });
        assertTrue(restored.get()); assertEquals(0, turns.retainedAgents());
        assertThrows(IllegalStateException.class, () -> turns.call(id, () -> { throw new IllegalStateException("fixture"); }));
        assertEquals(0, turns.retainedAgents());
    }
}
