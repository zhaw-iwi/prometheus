package ch.zhaw.prometheus.logging;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LatencyTraceUnitTest {
    @Test void exportsBoundedFailureSpansAndExplicitWorkerTimingsWithoutCrossRequestLeakage() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000);
        try (var trace = new LatencyTrace(null, ignored -> {}, clock::get);
                var executor = Executors.newSingleThreadExecutor()) {
            assertThrows(IllegalStateException.class, () -> LatencyTrace.measure("persist", () -> {
                clock.addAndGet(2_500_000);
                throw new IllegalStateException("private conversation");
            }));
            var workerScope = LatencyTrace.continuation(trace.id());
            executor.submit(() -> {
                try (var worker = workerScope.get()) {
                    LatencyTrace.record("inference", 1.5, true, UUID.randomUUID().toString(),
                            "DECISION", "fixture-model", "none", 10, 1);
                }
            }).get();
            var data = decode(LatencyTrace.responseHeader());
            assertEquals(2.5, data.get("durationMs").getAsDouble());
            var spans = data.getAsJsonArray("spans");
            assertEquals(2, spans.size());
            assertEquals("error", spans.get(0).getAsJsonObject().get("status").getAsString());
            assertEquals("fixture-model", spans.get(1).getAsJsonObject().get("model").getAsString());
            assertFalse(data.toString().contains("private"));
            for (int i = 0; i < 100; i++) LatencyTrace.record("generate", 1, true);
            assertTrue(LatencyTrace.responseHeader().length() <= 6000);
            assertTrue(decode(LatencyTrace.responseHeader()).get("truncated").getAsBoolean());
        }
        assertNull(LatencyTrace.responseHeader());
        try (var other = new LatencyTrace(null, ignored -> {})) {
            assertEquals(0, decode(LatencyTrace.responseHeader()).getAsJsonArray("spans").size());
        }
    }

    private static com.google.gson.JsonObject decode(String value) {
        return com.google.gson.JsonParser.parseString(new String(java.util.Base64.getDecoder().decode(value),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
    }

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
