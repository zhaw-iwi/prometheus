package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.logging.LatencyTrace;

class GuardInferenceExecutorUnitTest {
    @Test void boundsGlobalAndPerTurnWorkWhileKeepingRequestsIsolated() throws Exception {
        var properties = options(3, 2, 4);
        var started = new CountDownLatch(3); var release = new CountDownLatch(1);
        var active = new AtomicInteger(); var maximum = new AtomicInteger();
        var turnA = new AtomicInteger(); var maxA = new AtomicInteger();
        try (var executor = new GuardInferenceExecutor(properties)) {
            java.util.function.Function<InferenceRequest, String> work = request -> {
                boolean a = request.messages().getFirst().getContent().equals("a");
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                if (a) maxA.accumulateAndGet(turnA.incrementAndGet(), Math::max);
                try {
                    assertTrue(Thread.currentThread().isVirtual());
                    assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                    started.countDown(); await(release); return request.messages().getFirst().getContent();
                } finally { active.decrementAndGet(); if (a) turnA.decrementAndGet(); }
            };
            try (var first = executor.start(List.of(request("a"), request("a"), request("a")), work);
                 var second = executor.start(List.of(request("b"), request("b")), work)) {
                try { assertTrue(started.await(5, TimeUnit.SECONDS)); assertEquals(3, active.get()); }
                finally { release.countDown(); }
                for (int i = 0; i < 3; i++) assertEquals("a", first.await(i));
                for (int i = 0; i < 2; i++) assertEquals("b", second.await(i));
            }
        }
        assertEquals(3, maximum.get()); assertTrue(maxA.get() <= 2);
    }

    @Test void capacityExhaustionDoesNotDispatchAndCancellationReleasesCapacity() throws Exception {
        var properties = options(1, 1, 0); properties.setGuardQueueWaitMs(50);
        var started = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        var extra = new AtomicInteger();
        try (var executor = new GuardInferenceExecutor(properties)) {
            try (var session = executor.start(List.of(request("held")), ignored -> {
                started.countDown();
                try { new CountDownLatch(1).await(); return "false"; }
                catch (InterruptedException stopped) { interrupted.countDown(); Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            })) {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> executor.start(List.of(request("other")), r -> { extra.incrementAndGet(); return "false"; }));
            }
            assertTrue(interrupted.await(5, TimeUnit.SECONDS)); assertEquals(0, extra.get());
            try (var next = executor.start(List.of(request("next")), r -> "false")) { assertEquals("false", next.await(0)); }
            executor.close();
            for (int i = 0; i < 3; i++) assertTrue(assertThrows(IllegalStateException.class,
                    () -> executor.start(List.of(request("closed")), r -> "false")).getMessage().contains("closed"));
        }
    }

    @Test void requiredFailureIsSanitizedAndExpiredTurnCancelsWork() throws Exception {
        var now = new AtomicLong(); var started = new CountDownLatch(1); var interrupted = new CountDownLatch(1);
        try (var executor = new GuardInferenceExecutor(options(2, 2, 2), now::get)) {
            try (var failed = executor.start(List.of(request("error")), ignored -> { throw new IllegalStateException("private provider text"); })) {
                assertEquals("Required guard inference failed", assertThrows(IllegalStateException.class, () -> failed.await(0)).getMessage());
            }
            try (var session = executor.start(List.of(request("held")), ignored -> {
                started.countDown();
                try { new CountDownLatch(1).await(); return "false"; }
                catch (InterruptedException stopped) { interrupted.countDown(); Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            })) {
                assertTrue(started.await(5, TimeUnit.SECONDS)); now.set(TimeUnit.SECONDS.toNanos(31));
                assertTrue(assertThrows(IllegalStateException.class, () -> session.await(0)).getMessage().contains("turn deadline"));
                assertTrue(interrupted.await(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void carriesOnlyOpaqueTraceContextIntoWorker() {
        String id = UUID.randomUUID().toString(); InferenceRequest request;
        try (var scope = new LatencyTrace(id, ignored -> {})) { request = request("fixture"); }
        try (var executor = new GuardInferenceExecutor(options(1, 1, 1));
             var session = executor.start(List.of(request), ignored -> LatencyTrace.currentId())) {
            assertEquals(id, session.await(0)); assertEquals("unscoped", LatencyTrace.currentId());
        }
    }

    static OpenAIProperties options(int global, int perTurn, int queue) {
        var properties = new OpenAIProperties(); properties.setGuardParallelism(global);
        properties.setGuardPerTurnParallelism(perTurn); properties.setGuardQueueCapacity(queue); return properties;
    }
    static InferenceRequest request(String value) { return new InferenceRequest(InferencePurpose.DECISION, List.of(PromptMessage.system(value)), InferenceRequest.Output.BOOLEAN); }
    static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture barrier timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); }
    }
}
