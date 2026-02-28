package ch.zhaw.prometheus.runtime;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.logging.AgentBehaviourBroadcaster;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.repositories.AgentRepository;

@Component
@ConditionalOnProperty(name = "prometheus.runtime.tick.enabled", havingValue = "true")
public class ContinuousEvaluationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContinuousEvaluationScheduler.class);

    private final AgentRepository repository;
    private final AgentMonitorBroadcaster monitorBroadcaster;
    private final AgentBehaviourBroadcaster behaviourBroadcaster;
    private final AgentApplicationService agentService;

    public ContinuousEvaluationScheduler(AgentRepository repository, AgentMonitorBroadcaster monitorBroadcaster,
            AgentBehaviourBroadcaster behaviourBroadcaster,
            AgentApplicationService agentService) {
        this.repository = repository;
        this.monitorBroadcaster = monitorBroadcaster;
        this.behaviourBroadcaster = behaviourBroadcaster;
        this.agentService = agentService;
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
                Event response = agent.tick(this.agentService.runtime());
                this.repository.save(agent);
                try {
                    this.monitorBroadcaster.publish(agent);
                } catch (Throwable failure) {
                    LOGGER.debug("SSE monitor publish failed in scheduler boundary; agentId={}", agent.getId(), failure);
                }
                try {
                    this.behaviourBroadcaster.publish(agent.getId(), response);
                } catch (Throwable failure) {
                    LOGGER.debug("SSE behaviour publish failed in scheduler boundary; agentId={}", agent.getId(), failure);
                }
                processed++;
            } catch (RuntimeException exception) {
                LOGGER.warn("continuous tick failed for agent {}", agent.getId(), exception);
            }
        }
        return processed;
    }
}

