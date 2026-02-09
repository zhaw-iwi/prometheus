package ch.zhaw.prometheus.runtime;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.repositories.AgentRepository;

@Component
@ConditionalOnProperty(name = "prometheus.runtime.tick.enabled", havingValue = "true")
public class ContinuousEvaluationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContinuousEvaluationScheduler.class);

    private final AgentRepository repository;
    private final AgentMonitorBroadcaster monitorBroadcaster;

    public ContinuousEvaluationScheduler(AgentRepository repository, AgentMonitorBroadcaster monitorBroadcaster) {
        this.repository = repository;
        this.monitorBroadcaster = monitorBroadcaster;
    }

    @Scheduled(fixedDelayString = "${prometheus.runtime.tick.delay-ms:1000}")
    public void scheduledTick() {
        this.runCycle();
    }

    int runCycle() {
        List<Agent> agents = this.repository.findAll();
        int processed = 0;
        for (Agent agent : agents) {
            if (agent == null || !agent.isActive()) {
                continue;
            }
            try {
                agent.tick();
                this.repository.save(agent);
                this.monitorBroadcaster.publish(agent);
                processed++;
            } catch (RuntimeException exception) {
                LOGGER.warn("continuous tick failed for agent {}", agent.getId(), exception);
            }
        }
        return processed;
    }
}
