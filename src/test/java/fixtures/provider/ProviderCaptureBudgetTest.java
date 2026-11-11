package fixtures.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.spi.live.LiveSessionGateway;

class ProviderCaptureBudgetTest {
    @TempDir Path directory;
    @Test void requestAndReportedTokenCeilingsPreventFurtherProviderCalls() throws Exception {
        for (boolean byTokens : List.of(false, true)) {
            var output = directory.resolve(byTokens + ".jsonl");
            var recorder = ProviderCaptureApplication.Capture.recorder(output, byTokens ? 4 : 1, byTokens ? 10 : 100, 1);
            var real = mock(LanguageModelGateway.class);
            when(real.infer(any())).thenAnswer(call -> {
                LatencyTrace.record("inference", 1, true, "id", "EXTRACTION", "synthetic", "none", 8, 2);
                return "{}";
            });
            var gateway = (LanguageModelGateway) recorder.postProcessAfterInitialization(real, "gateway");
            var request = new InferenceRequest(InferencePurpose.EXTRACTION, List.of(), InferenceRequest.Output.JSON_OBJECT);
            assertEquals("{}", gateway.infer(request));
            assertThrows(IllegalStateException.class, () -> gateway.infer(request)); verify(real, times(1)).infer(any());
            assertEquals(1, Files.readAllLines(output).size()); assertTrue(Files.readString(output).contains("cumulativeTokens"));
        }
    }
    @Test void aSecondLiveSessionIsRejectedBeforeProviderAccess() {
        var recorder = ProviderCaptureApplication.Capture.recorder(directory.resolve("live.jsonl"), 4, 100, 1);
        var real = mock(LiveSessionGateway.class);
        var gateway = (LiveSessionGateway) recorder.postProcessAfterInitialization(real, "live");
        gateway.create(null); assertThrows(IllegalStateException.class, () -> gateway.create(null));
        verify(real, times(1)).create(null);
    }
    @Test void providerFailureRetainsSafeEvidenceWithoutExceptionText() throws Exception {
        var output = directory.resolve("failure.jsonl");
        var recorder = ProviderCaptureApplication.Capture.recorder(output, 4, 100, 1);
        var real = mock(LanguageModelGateway.class); when(real.infer(any())).thenThrow(new IllegalStateException("private provider detail"));
        var gateway = (LanguageModelGateway) recorder.postProcessAfterInitialization(real, "gateway");
        assertThrows(IllegalStateException.class, () -> gateway.infer(new InferenceRequest(InferencePurpose.EXTRACTION, List.of(), InferenceRequest.Output.JSON_OBJECT)));
        assertTrue(Files.readString(output).contains("IllegalStateException")); assertFalse(Files.readString(output).contains("private provider detail"));
    }
}
