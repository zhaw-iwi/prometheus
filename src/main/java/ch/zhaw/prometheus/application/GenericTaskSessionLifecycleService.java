package ch.zhaw.prometheus.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;

/** Serialize the durable task pause with ordinary turns; no inference or periodic database work. */
@Service
public class GenericTaskSessionLifecycleService {
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final AgentMonitorBroadcaster monitor;
    private final TransactionTemplate transaction;
    public GenericTaskSessionLifecycleService(AgentApplicationService turns, AgentRepository agents,
            AgentMonitorBroadcaster monitor, PlatformTransactionManager transactions) {
        this.turns = turns; this.agents = agents; this.monitor = monitor;
        transaction = new TransactionTemplate(transactions);
    }
    public void closed(UUID id, UUID epoch, UUID session) {
        turns.serialized(id, () -> transaction.execute(status -> {
            var agent = agents.findById(id).orElse(null);
            if (agent == null || !epoch.equals(agent.executionEpoch()) || agent.getCurrentState() == null
                    || !(agent.getCurrentState().ownPolicy() instanceof TaskPolicy task)
                    || !session.toString().equals(TaskMemory.text(task.storage(), TaskMemory.SESSION, ""))) return null;
            if (TaskMemory.pauseForSession(task.storage(), "live_session_ended")) {
                agents.saveAndFlush(agent);
                AfterCommit.run(() -> monitor.publish(agent));
            }
            return null;
        }));
    }
}
