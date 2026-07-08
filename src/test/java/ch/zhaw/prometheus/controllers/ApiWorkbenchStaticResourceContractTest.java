package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ApiWorkbenchStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/apiworkbench/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/apiworkbench/script.js");
    private static final Path CSS = Path.of("src/main/resources/public/apiworkbench/workbench.css");

    @Test
    void apiWorkbenchStaticClientIsSelfContained() throws IOException {
        String index = Files.readString(INDEX);
        String script = Files.readString(SCRIPT);
        String css = Files.readString(CSS);

        assertTrue(index.contains("<title>PROMETHEUS API Workbench</title>"));
        assertTrue(index.contains("data-testid=\"apiworkbench-shell\""));
        assertTrue(index.contains("data-testid=\"lifecycle-steps\""));
        assertTrue(index.contains("data-testid=\"endpoint-list\""));
        assertTrue(index.contains("data-testid=\"request-body-editor\""));
        assertTrue(index.contains("data-testid=\"snippet-output\""));
        assertTrue(index.contains("data-testid=\"http-response-preview\""));
        assertTrue(index.contains("data-testid=\"sse-response-preview\""));
        assertTrue(index.contains("<script src=\"script.js\"></script>"));
        assertTrue(index.contains("<link rel=\"stylesheet\" href=\"workbench.css\""));

        assertTrue(script.contains("const ENDPOINTS = ["));
        assertTrue(script.contains("const LIFECYCLE_STEPS = ["));
        assertTrue(script.contains("function buildResolvedUrl(endpoint)"));
        assertTrue(script.contains("function fetchSnippet(endpoint)"));
        assertTrue(script.contains("function curlSnippet(endpoint)"));
        assertTrue(script.contains("function sseSnippet(endpoint)"));
        assertTrue(script.contains("function renderEndpointList()"));
        assertTrue(script.contains("function renderLifecycleSteps()"));
        assertTrue(script.contains("X-Prometheus-Access-Code"));
        assertTrue(script.contains("X-Prometheus-Admin-Token"));

        assertTrue(css.contains(".workbench-grid"));
        assertTrue(css.contains(".session-strip"));
        assertTrue(css.contains(".endpoint-item"));
        assertTrue(css.contains(".snippet-output"));
        assertTrue(css.contains("@media (max-width: 767px)"));
    }

    @Test
    void apiWorkbenchCatalogCoversCurrentClientDeveloperContracts() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("/demo/session"));
        assertTrue(script.contains("/demo/agent-types"));
        assertTrue(script.contains("/demo/agents"));
        assertTrue(script.contains("/demo/agents/{agentId}/info"));
        assertTrue(script.contains("/demo/agents/{agentId}/acknowledge"));
        assertTrue(script.contains("/demo/agents/{agentId}/behaviour/generate"));
        assertTrue(script.contains("/demo/agents/{agentId}/behaviour/stream"));
        assertTrue(script.contains("/demo/agents/{agentId}/monitor/stream"));
        assertTrue(script.contains("/demo/agents/{agentId}/prompt"));
        assertTrue(script.contains("/demo/agents/{agentId}/realtime/call"));
        assertTrue(script.contains("/admin/agent-types"));
        assertTrue(script.contains("/admin/access-codes"));
        assertTrue(script.contains("/{agentId}/info"));
        assertTrue(script.contains("/{agentId}/acknowledge"));
        assertTrue(script.contains("/{agentId}/behaviour/stream"));
        assertTrue(script.contains("obs.user_utterance"));
        assertTrue(script.contains("resp.behaviour_plan") || script.contains("behaviour-plan"));

        assertFalse(script.contains("/visual/facial"));
        assertFalse(script.contains("/visual/social"));
        assertFalse(script.contains("/realtime/index.html"));
        assertFalse(script.contains("/monitor/index.html"));
        assertFalse(script.toLowerCase().contains("gigi"));
        assertFalse(script.toLowerCase().contains("tdsr"));
    }
}
