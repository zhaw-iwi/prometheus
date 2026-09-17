package ch.zhaw.prometheus.logging;

import java.io.IOException;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LatencyTraceFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        // Do not retain request scopes for long-lived SSE subscriptions or static assets.
        return !"POST".equals(request.getMethod());
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        try (LatencyTrace trace = new LatencyTrace(request.getHeader(LatencyTrace.TRACE_HEADER),
                id -> response.setHeader(LatencyTrace.BEHAVIOUR_HEADER, id))) {
            response.setHeader(LatencyTrace.TRACE_HEADER, trace.id());
            long start = LatencyTrace.now();
            try { chain.doFilter(request, response); }
            finally {
                LoggerFactory.getLogger(LatencyTraceFilter.class).info(
                        "latency trace={} stage=http status={} durationMs={} async={}", trace.id(),
                        response.getStatus(), LatencyTrace.elapsedMs(start), request.isAsyncStarted());
            }
        }
    }
}
