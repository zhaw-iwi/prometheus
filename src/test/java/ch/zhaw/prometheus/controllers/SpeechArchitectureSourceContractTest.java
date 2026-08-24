package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SpeechArchitectureSourceContractTest {

    @Test
    void combinedRealtimeBackendAndProfilesStayDeleted() throws IOException {
        List<String> deletedTypes = List.of(
                "application/AssistantBehaviourPublishedEvent.java",
                "application/RealtimeCallOrchestrationService.java",
                "application/RealtimeCallSettings.java",
                "application/RealtimePromptInstructions.java",
                "application/RealtimeSidebandService.java",
                "controllers/RealtimeController.java",
                "spi/RealtimeCallConfig.java",
                "spi/RealtimeSessionClient.java");
        Path packageRoot = Path.of("src/main/java/ch/zhaw/prometheus");
        deletedTypes.forEach(relative -> assertFalse(Files.exists(packageRoot.resolve(relative)),
                () -> "Removed combined Realtime type returned: " + relative));

        String javaSource = readTree(packageRoot, ".java");
        assertFalse(javaSource.contains("RealtimeCall"));
        assertFalse(javaSource.contains("RealtimeSideband"));
        assertFalse(javaSource.contains("RealtimeSessionClient"));
        assertFalse(javaSource.contains("AssistantBehaviourPublishedEvent"));
        assertFalse(javaSource.contains("REALTIME_SPEECH"));
        assertFalse(javaSource.contains("BACKEND_COMPLEMENT"));
    }

    @Test
    void configurationNamesOnlyTheScopedTranscriptionAndOutputSpeechServices() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/openai.properties.template"));
        String productionProperties = Files.readString(Path.of("src/main/resources/openai-prod.properties"));

        for (String content : List.of(properties, productionProperties)) {
            assertTrue(content.contains("openai.liveTranscriptionClientSecretUrl"));
            assertTrue(content.contains("openai.liveTranscriptionWebRtcUrl"));
            assertTrue(content.contains("prometheus.speech.model"));
            assertFalse(content.contains("openai.realtime"));
            assertFalse(content.contains("openai.clientSecretUrl"));
            assertFalse(content.contains("openai.realtimeCallsUrl"));
            assertFalse(content.contains("openai.transcriptionModel"));
        }
    }

    @Test
    void productionBrowserClientsDoNotReferenceCombinedRealtimeRoutesOrEvents() throws IOException {
        String valerian = Files.readString(Path.of("src/main/resources/public/valerian/script.js"));
        String workbench = Files.readString(Path.of("src/main/resources/public/apiworkbench/script.js"));

        for (String content : List.of(valerian, workbench)) {
            assertFalse(content.contains("/realtime/call"));
            assertFalse(content.contains("/realtime/calls"));
            assertFalse(content.contains("response.create"));
            assertFalse(content.contains("response.cancel"));
        }
        assertFalse(valerian.contains("new RTCPeerConnection"));
        assertFalse(valerian.contains("output_audio_transcript"));
    }

    private static String readTree(Path root, String suffix) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            StringBuilder result = new StringBuilder();
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(suffix)).toList()) {
                result.append(Files.readString(path));
            }
            return result.toString();
        }
    }
}
