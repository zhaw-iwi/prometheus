package ch.zhaw.prometheus.integration.realtime;

import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.agents.AgentFixtures;
import ch.zhaw.prometheus.model.Agent;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "prometheus.gateway.mode=scripted",
        "prometheus.gateway.script=classpath:scripts/single-state-micro-coaching-realtime-replay-script.json"
})
class SingleStateMicroCoachingRealtimeReplayIntegrationTest extends AbstractRealtimeReplayIntegrationTest {

    @Override
    protected Agent buildAgent() {
        return AgentFixtures.singleStateMicroCoaching();
    }

    @Override
    protected String scriptPath() {
        return "classpath:scripts/single-state-micro-coaching-realtime-replay-script.json";
    }

    @Override
    protected String expectedFinalState() {
        return "Session Goodbye Final";
    }
}
