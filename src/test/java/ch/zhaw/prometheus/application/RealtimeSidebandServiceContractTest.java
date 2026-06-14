package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RealtimeSidebandServiceContractTest {
    private static final Path SIDEBAND_SERVICE = Path.of(
            "src/main/java/ch/zhaw/prometheus/application/RealtimeSidebandService.java");

    @Test
    void userSpeechAcknowledgementUsesRealtimeSpeechProfile() throws IOException {
        String source = Files.readString(SIDEBAND_SERVICE);
        int callStart = source.indexOf("Optional<ResponseView> acknowledged = agentService.acknowledge");
        assertTrue(callStart >= 0);
        int profileIndex = source.indexOf("OutputProfile.REALTIME_SPEECH", callStart);
        assertTrue(profileIndex > callStart);
        int callEnd = source.indexOf(");", profileIndex);
        assertTrue(callEnd > profileIndex);
        String acknowledgeCall = source.substring(callStart, callEnd);

        assertTrue(acknowledgeCall.contains("OutputProfile.REALTIME_SPEECH"));
        assertFalse(acknowledgeCall.contains("OutputProfile.BACKEND_COMPLEMENT"));
    }

    @Test
    void sidebandDoesNotCreateFreeFormAssistantResponses() throws IOException {
        String source = Files.readString(SIDEBAND_SERVICE);

        assertFalse(source.contains("pendingResponseInstruction"));
        assertFalse(source.contains("RealtimePromptInstructions.responseInstruction"));
        assertFalse(source.contains("recordRealtimeAssistantSpeech"));
    }
}
