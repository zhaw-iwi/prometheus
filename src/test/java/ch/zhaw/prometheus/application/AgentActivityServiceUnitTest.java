package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.logging.*;

class AgentActivityServiceUnitTest {
    @Test void overlappingOperationsRetainIdentityAndFailureWithoutContent() throws Exception {
        var now = new AtomicLong();
        var service = new AgentActivityService(mock(AgentMonitorBroadcaster.class), Clock.systemUTC(), now::get);
        UUID agent = UUID.randomUUID(), epoch = UUID.randomUUID(), session = UUID.randomUUID(), source = UUID.randomUUID();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = workers.submit(() -> service.call(agent, "request", true, () -> {
                ActivityTrace.bind(epoch, session, source);
                return ActivityTrace.measure("thinking", () -> {
                    entered.countDown();
                    try { assertTrue(release.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                    ActivityTrace.inference("request_1", "EXTRACTION", "fixture-model", "low", 10, 5, 1);
                    return "private response";
                });
            }));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            var active = service.snapshot(agent).active().getFirst(); assertEquals("thinking", active.stage());
            service.call(agent, "second", true, () -> null);
            assertEquals(active.operationId(), service.snapshot(agent).active().getFirst().operationId());
            now.set(3_000_000_000L); release.countDown(); assertEquals("private response", pending.get(10, TimeUnit.SECONDS));
            assertTrue(service.snapshot(agent).active().isEmpty());
            assertTrue(service.snapshot(agent).recent().stream().anyMatch(e -> "thinking".equals(e.stage()) && Double.valueOf(3000).equals(e.durationMs())));
            assertTrue(service.snapshot(agent).recent().stream().anyMatch(e -> source.equals(e.sourceId()) && session.equals(e.sessionId())));
            assertThrows(IllegalStateException.class, () -> service.call(agent, "request", true, () -> { throw new IllegalStateException("private failure"); }));
            var view = service.snapshot(agent);
            assertEquals("failed", view.recent().getLast().outcome());
            assertFalse(new com.google.gson.Gson().toJson(view).contains("private"));
            assertNull(ActivityTrace.current());
            assertTrue(service.snapshot(UUID.randomUUID()).recent().isEmpty());
        } finally { release.countDown(); service.close(); }
    }
    @Test void boundedHistoryAggregatesQuietCuesAndResetFencesOldOperations() {
        var service = new AgentActivityService(mock(AgentMonitorBroadcaster.class)); UUID id = UUID.randomUUID();
        try {
            for (int i=0;i<300;i++) service.call(id,"sensor",false,()-> { ActivityTrace.cue("cooldown", null); return null; });
            assertEquals(1, service.snapshot(id).recent().size()); assertEquals(300, service.snapshot(id).cue().occurrences());
            assertTrue(service.snapshot(id).active().isEmpty());
            for (int i=0;i<100;i++) service.call(id,"turn",true,()->null);
            assertEquals(128, service.snapshot(id).recent().size()); assertTrue(service.snapshot(id).dropped()>0);
            UUID before=UUID.randomUUID(), after=UUID.randomUUID();
            service.call(id,"request",true,()-> { ActivityTrace.bind(before,null,null); return null; });
            service.call(id,"reset",true,()-> { ActivityTrace.bind(after,null,null); return null; });
            assertEquals(after,service.snapshot(id).epoch()); assertNull(service.snapshot(id).cue());
        } finally { service.close(); }
    }
}
