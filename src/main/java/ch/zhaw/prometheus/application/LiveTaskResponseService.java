package ch.zhaw.prometheus.application;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;

/** One durable claim per pending response; immutable inference uses the existing bounded action workers. */
@Service
public class LiveTaskResponseService {
    private final AgentApplicationService turns;
    private final AgentRepository agents;
    private final ExternalSpeechOwnership ownership;
    private final BackgroundActionExecutor workers;
    private final LanguageModelGateway gateway;
    private final TransactionTemplate transaction;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final AgentMonitorBroadcaster monitor;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public LiveTaskResponseService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            BackgroundActionExecutor workers, LanguageModelGateway gateway, PlatformTransactionManager transactions,
            org.springframework.context.ApplicationEventPublisher events, AgentMonitorBroadcaster monitor) {
        this(turns, agents, ownership, workers, gateway, transactions, events, monitor, Clock.systemUTC());
    }
    LiveTaskResponseService(AgentApplicationService turns, AgentRepository agents, ExternalSpeechOwnership ownership,
            BackgroundActionExecutor workers, LanguageModelGateway gateway, PlatformTransactionManager transactions,
            org.springframework.context.ApplicationEventPublisher events, AgentMonitorBroadcaster monitor, Clock clock) {
        this.turns = turns; this.agents = agents; this.ownership = ownership; this.workers = workers;
        this.gateway = gateway; this.events = events; this.monitor = monitor; this.clock = clock;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** Called inside the native-record transaction, after recording and before saving the same loaded agent. */
    void prepare(Agent agent, ExternalSpeech owner, Event speech, Instant started) {
        if (!(agent.getCurrentState().ownPolicy() instanceof TaskPolicy policy) || !policy.storage().containsKey(TaskResponse.KEY)) return;
        if (expire(agent, owner)) return;
        var candidate = TaskResponse.claim(policy.storage(), owner, speech, started, clock.instant());
        if (candidate.isEmpty()) return;
        var check = candidate.get();
        UUID id = agent.getId();
        var work = new PreparedAction(check.id(), policy.storage().getID(), Map.of(TaskResponse.KEY, policy.storage().get(TaskResponse.KEY).toString()), model -> {
            boolean match = false;
            // A restarted/lost worker is never replayed. The persisted CHECKING claim expires instead.
            if (clock.instant().isBefore(check.deadline()) && ownership.taskWorkAllowed(id, owner)) {
                try { match = check.matches(model.infer(check.request())); }
                catch (RuntimeException failure) { /* One failure has one uncertain outcome, never a retry. */ }
            }
            return Map.of(TaskResponse.KEY, Boolean.toString(match));
        });
        BackgroundActionExecutor.Reservation job;
        try { job = workers.reserve(work, gateway, (ignored, values, versions) -> finish(id, owner, check, Boolean.parseBoolean(values.get(TaskResponse.KEY)))); }
        catch (RuntimeException full) {
            TaskResponse.finish(policy.storage(), owner, check, false, clock.instant());
            recover(agent, owner, "response_check_unavailable"); return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            job.close(); throw new IllegalStateException("Task response claim requires a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { job.commit(id); }
            @Override public void afterCompletion(int status) { job.close(); }
        });
    }
    private String finish(UUID id, ExternalSpeech owner, TaskResponse.Check check, boolean match) {
        return turns.serialized(id, () -> transaction.execute(status -> {
            var agent = agents.findById(id).orElse(null);
            if (agent == null || !owner.equals(ownership.current(agent)) || !ownership.taskWorkAllowed(id, owner)
                    || !(agent.getCurrentState().ownPolicy() instanceof TaskPolicy policy)) return "obsolete";
            if (!TaskResponse.finish(policy.storage(), owner, check, match, clock.instant())) return "obsolete";
            boolean captured = TaskResponse.captured(policy.storage(), owner);
            if (!captured) recover(agent, owner, "response_association_uncertain");
            agents.saveAndFlush(agent);
            AfterCommit.run(() -> { events.publishEvent(new AgentCommitted(id, owner.epoch())); monitor.publish(agent); });
            return captured ? "response_captured" : "response_uncertain";
        }));
    }
    /** Existing context refresh supplies deadlines; this adds no polling or query. Caller saves the loaded agent. */
    boolean expire(Agent agent, ExternalSpeech owner) {
        // The caller already checked scoped authorization while loading this agent. Use local fences here.
        if (!owner.epoch().equals(agent.executionEpoch()) || !ownership.taskWorkAllowed(agent.getId(), owner)) return false;
        if (!(agent.getCurrentState().ownPolicy() instanceof TaskPolicy policy) || !TaskResponse.expired(policy.storage(), clock.instant())
                || !owner.equals(TaskResponse.owner(policy.storage()))) return false;
        recover(agent, owner, "response_capture_deadline"); return true;
    }
    private void recover(Agent agent, ExternalSpeech owner, String reason) {
        var policy = (TaskPolicy) agent.getCurrentState().ownPolicy();
        TaskResponse.uncertain(policy.storage());
        String type = policy.liveResponseFailureEventType();
        if (type == null) TaskMemory.pauseForResponse(policy.storage(), reason);
        else agent.acknowledge(new Event(type, "system", Event.KIND_SYSTEM, reason), turns.runtime().withExternalSpeech(owner));
    }
}
