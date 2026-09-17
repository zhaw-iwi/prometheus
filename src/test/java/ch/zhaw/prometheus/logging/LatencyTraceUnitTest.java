package ch.zhaw.prometheus.logging;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LatencyTraceUnitTest {
    @Test void monotonicScopesAreIsolatedAndRestoreAfterFailure() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000);
        String id = UUID.randomUUID().toString();
        try (var trace = new LatencyTrace(id, ignored -> {}, clock::get);
                var executor = Executors.newSingleThreadExecutor()) {
            long start = LatencyTrace.now();
            clock.addAndGet(3_000_000);
            assertEquals(3, LatencyTrace.elapsedMs(start));
            assertEquals("unscoped", executor.submit(LatencyTrace::currentId).get());
            assertThrows(IllegalStateException.class, () -> LatencyTrace.measure("test", () -> {
                try (var nested = new LatencyTrace("bad\r\nheader", ignored -> {})) {
                    assertNotEquals(id, nested.id());
                    throw new IllegalStateException();
                }
            }));
            assertEquals(id, LatencyTrace.currentId());
        }
        assertEquals("unscoped", LatencyTrace.currentId());
    }

    @Test void httpCorrelatesOnlyThePublishedEventAndCleansUpRejectedRequests() throws Exception {
        var filter = new LatencyTraceFilter();
        var request = new MockHttpServletRequest("POST", "/demo/agents/test/acknowledge");
        var response = new MockHttpServletResponse();
        String id = UUID.randomUUID().toString();
        UUID event = UUID.randomUUID();
        request.addHeader(LatencyTrace.TRACE_HEADER, id);
        filter.doFilter(request, response, (req, res) -> LatencyTrace.behaviour(event));
        assertEquals(id, response.getHeader(LatencyTrace.TRACE_HEADER));
        assertEquals(event.toString(), response.getHeader(LatencyTrace.BEHAVIOUR_HEADER));
        var rejected = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/demo/session"), rejected,
                (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).sendError(403));
        assertNotEquals(id, rejected.getHeader(LatencyTrace.TRACE_HEADER));
        assertNull(rejected.getHeader(LatencyTrace.BEHAVIOUR_HEADER));
        assertEquals("unscoped", LatencyTrace.currentId());
    }
}
