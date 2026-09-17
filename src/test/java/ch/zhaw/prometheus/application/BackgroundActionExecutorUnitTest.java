package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;
import ch.zhaw.prometheus.spi.NoOpLanguageModelGateway;

class BackgroundActionExecutorUnitTest {
    private final NoOpLanguageModelGateway gateway = new NoOpLanguageModelGateway();
    private PreparedAction work(PreparedAction.Work work) { return new PreparedAction(UUID.randomUUID(), null, Map.of(), work); }

    @Test void admissionIsBoundedAndPerAgentFifoDoesNotBlockOtherAgents() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var otherDone = new CountDownLatch(1);
        var order = new CopyOnWriteArrayList<Integer>(); UUID first = UUID.randomUUID();
        try (var executor = new BackgroundActionExecutor(2, 3, 30000)) {
            executor.reserve(work(g -> { entered.countDown(); waitFor(release); order.add(1); return Map.of(); }), gateway,
                    (a,v,r) -> "completed").commit(first);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            executor.reserve(work(g -> { order.add(2); return Map.of(); }), gateway, (a,v,r) -> "completed").commit(first);
            var third = executor.reserve(work(g -> Map.of()), gateway, (a,v,r) -> { otherDone.countDown(); return "completed"; });
            assertThrows(IllegalStateException.class, () -> executor.reserve(work(g -> Map.of()), gateway, (a,v,r) -> "completed"));
            third.commit(UUID.randomUUID()); assertTrue(otherDone.await(5, TimeUnit.SECONDS)); assertTrue(order.isEmpty());
            release.countDown();
            await().untilAsserted(() -> { assertEquals(List.of(1,2), order); assertEquals(3, executor.availableCapacity()); assertEquals(0, executor.retainedAgents()); });
        } finally { release.countDown(); }
    }

    @Test void failureReleasesCapacityAndFollowingWorkStillRuns() throws Exception {
        var complete = new CountDownLatch(1); UUID id = UUID.randomUUID();
        try (var executor = new BackgroundActionExecutor(1, 2, 30000)) {
            executor.reserve(work(g -> { throw new IllegalStateException("fixture"); }), gateway, (a,v,r) -> fail("must not apply")).commit(id);
            executor.reserve(work(g -> Map.of()), gateway, (a,v,r) -> { complete.countDown(); return "completed"; }).commit(id);
            assertTrue(complete.await(5, TimeUnit.SECONDS));
            await().untilAsserted(() -> assertEquals(2, executor.availableCapacity()));
        }
    }

    @Test void transactionRollbackNeverDispatchesReservedWork() {
        var calls = new AtomicInteger();
        try (var executor = new BackgroundActionExecutor(1, 1, 30000)) {
            var action = new StaticExtractionAction("fixture", new Storage(), "outcome") {
                @Override public PreparedAction prepare(EventHistory e, ObservationSnapshot s, PolicyRuntime r) {
                    return work(g -> { calls.incrementAndGet(); return Map.of(); });
                }
            };
            TransactionSynchronizationManager.initSynchronization();
            try {
                try (var turn = new BackgroundActionTurn(UUID.randomUUID(), executor, (a,v,r) -> "completed")) {
                    turn.submit(action, new EventHistory(), ObservationSnapshot.empty(), new PolicyRuntime(new PromptMessageAssembler(), gateway));
                    turn.commit();
                }
                assertEquals(0, executor.availableCapacity());
                TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
                assertEquals(1, executor.availableCapacity()); assertEquals(0, calls.get());
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
    }

    @Test void shutdownDiscardsLateWorkEvenWhenWorkerIgnoresInterruption() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var exited = new CountDownLatch(1);
        var applies = new AtomicInteger(); var executor = new BackgroundActionExecutor(1, 1, 30000);
        executor.reserve(work(g -> {
            entered.countDown();
            while (true) { try { release.await(); break; } catch (InterruptedException ignored) { } }
            exited.countDown(); return Map.of();
        }), gateway, (a,v,r) -> { applies.incrementAndGet(); return "completed"; }).commit(UUID.randomUUID());
        assertTrue(entered.await(5, TimeUnit.SECONDS)); executor.close(); release.countDown();
        assertTrue(exited.await(5, TimeUnit.SECONDS));
        var workerField = BackgroundActionExecutor.class.getDeclaredField("workers"); workerField.setAccessible(true);
        assertTrue(((ExecutorService) workerField.get(executor)).awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(0, executor.retainedAgents()); assertEquals(0, applies.get());
    }

    @Test void versionsRebaseOnlyThroughAcceptedBackgroundWrites() {
        UUID storage = UUID.randomUUID();
        var prepared = new PreparedAction(UUID.randomUUID(), storage, Map.of("key", "v0"), g -> Map.of("key", "{}"));
        var versions = new BackgroundActionExecutor.Versions(); versions.retain(prepared); versions.retain(prepared);
        versions.applied(storage, "key", "v0", "v1"); versions.release(prepared);
        assertEquals("v1", versions.expected(storage, "key", "v0"));
        assertNotEquals("foreground-write", versions.expected(storage, "key", "v0"));
        versions.release(prepared); assertEquals("v0", versions.expected(storage, "key", "v0"));
    }

    @Test void queueDeadlineStartsAtCommitAndExpiredWorkDoesNotDispatch() throws Exception {
        var clock = new AtomicLong(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var calls = new AtomicInteger(); UUID id = UUID.randomUUID();
        try (var executor = new BackgroundActionExecutor(1, 2, 10, clock::get)) {
            var first = executor.reserve(work(g -> { entered.countDown(); waitFor(release); return Map.of(); }), gateway, (a,v,r) -> "completed");
            clock.set(TimeUnit.SECONDS.toNanos(1)); first.commit(id);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            executor.reserve(work(g -> { calls.incrementAndGet(); return Map.of(); }), gateway, (a,v,r) -> "completed").commit(id);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(11)); release.countDown();
            await().untilAsserted(() -> assertEquals(0, executor.retainedAgents()));
            assertEquals(0, calls.get()); assertEquals(2, executor.availableCapacity());
        } finally { release.countDown(); }
    }
    private static void waitFor(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture deadline"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
