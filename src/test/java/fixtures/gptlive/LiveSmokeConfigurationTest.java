package fixtures.gptlive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ch.zhaw.prometheus.model.task.TaskResponse;

class LiveSmokeConfigurationTest {
    @ParameterizedTest
    @CsvSource({"First fixture observation.,MATCH", "Different fixture speech.,UNRELATED"})
    void browserProviderRecognisesTheRealResponseCheckWithoutDependingOnPromptWording(String speech, String verdict) {
        try (var provider = new LiveSmokeConfiguration.Provider()) {
            var check = new TaskResponse.Check(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                    "fixture-segment", "Offer one fixture observation", speech, Instant.now().plusSeconds(30),
                    TaskResponse.Purpose.CONTRIBUTION, null);
            var gateway = new LiveSmokeConfiguration().offlineLanguageGateway(provider);
            assertEquals(TaskResponse.Verdict.valueOf(verdict), check.verdict(gateway.infer(check.request())));
        }
    }
}
