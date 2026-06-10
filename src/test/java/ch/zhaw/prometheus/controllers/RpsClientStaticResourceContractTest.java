package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class RpsClientStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/rps/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/rps/script.js");

    @Test
    void rpsClientExposesManualSignControls() throws IOException {
        String index = Files.readString(INDEX);

        assertTrue(index.contains("<script src=\"script.js\"></script>"));
        assertTrue(index.contains("data-testid=\"manual-sign-rock\""));
        assertTrue(index.contains("data-testid=\"manual-sign-scissor\""));
        assertTrue(index.contains("data-testid=\"manual-sign-paper\""));
        assertTrue(index.contains("data-sign=\"rock\""));
        assertTrue(index.contains("data-sign=\"scissor\""));
        assertTrue(index.contains("data-sign=\"paper\""));
        assertTrue(index.contains("data-testid=\"agent-sign-label\""));
        assertTrue(index.contains("data-testid=\"user-sign-label\""));
    }

    @Test
    void rpsClientEmitsNormalizedManualHandSignEvents() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("type: \"obs.hand.sign\""));
        assertTrue(script.contains("source: \"rps.web\""));
        assertTrue(script.contains("detectionMode: \"manual\""));
        assertTrue(script.contains("confidence: 1.0"));
        assertTrue(script.contains("payload: JSON.stringify(payload)"));
        assertTrue(script.contains("normalizeSign(sign)"));
    }

    @Test
    void rpsClientRendersMotionHandSignFromBehaviourStream() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("new EventSource(behaviourStreamUrl())"));
        assertTrue(script.contains("lastBehaviourEventId"));
        assertTrue(script.contains("handleResponseEvent(data.responseEvent)"));
        assertTrue(script.contains("motion.handSign"));
        assertTrue(script.contains("renderAgentSign(sign)"));
        assertTrue(script.contains("renderUserSign(sign)"));
    }
}
