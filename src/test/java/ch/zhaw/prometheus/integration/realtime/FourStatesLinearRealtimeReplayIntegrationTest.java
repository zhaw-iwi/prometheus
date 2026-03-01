package ch.zhaw.prometheus.integration.realtime;

import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.agents.AgentFixtures;
import ch.zhaw.prometheus.model.Agent;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/four-states-linear-all-options-realtime-replay-script.json"
})
class FourStatesLinearRealtimeReplayIntegrationTest extends AbstractRealtimeReplayIntegrationTest {

    @Override
    protected Agent buildAgent() {
        return AgentFixtures.fourStatesLinear();
    }

    @Override
    protected String scriptPath() {
        return "classpath:scripts/four-states-linear-all-options-realtime-replay-script.json";
    }

    @Override
    protected String expectedFinalState() {
        return "Session Goodbye Final";
    }
}
