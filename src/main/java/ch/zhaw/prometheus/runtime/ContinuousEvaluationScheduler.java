package ch.zhaw.prometheus.runtime;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.repositories.AgentRepository;

@Component
@ConditionalOnProperty(name = "prometheus.runtime.tick.enabled", havingValue = "true")
public class ContinuousEvaluationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContinuousEvaluationScheduler.class);

    private final AgentRepository repository;
    private final AgentApplicationService agentService;

    public ContinuousEvaluationScheduler(AgentRepository repository, AgentApplicationService agentService) {
        this.repository = repository;
        this.agentService = agentService;
    }

    @Scheduled(fixedDelayString = "${prometheus.runtime.tick.delay-ms:1000}")
    public void scheduledTick() {
        this.runCycle();
    }

    int runCycle() {
        int processed = 0;
        for (UUID id : this.repository.findAllIds()) {
            try {
                if (this.agentService.tick(id)) processed++;
            } catch (RuntimeException exception) {
                LOGGER.warn("continuous tick failed for agent {}", id, exception);
            }
        }
        return processed;
    }
}
