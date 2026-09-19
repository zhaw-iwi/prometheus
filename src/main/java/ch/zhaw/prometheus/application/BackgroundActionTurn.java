package ch.zhaw.prometheus.application;

import java.util.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

/** Turn-local admission and transaction handoff, never retained by a persisted agent. */
final class BackgroundActionTurn implements ActionExecution, AutoCloseable {
    private final BackgroundActionExecutor executor;
    private final BackgroundActionExecutor.Completion completion;
    private final UUID agentId;
    private final List<BackgroundActionExecutor.Reservation> jobs = new ArrayList<>();
    private boolean handedOff;

    BackgroundActionTurn(UUID agentId, BackgroundActionExecutor executor, BackgroundActionExecutor.Completion completion) {
        this.agentId = agentId; this.executor = executor; this.completion = completion;
    }
    @Override public void submit(Action action, EventHistory events, ObservationSnapshot snapshot, PolicyRuntime runtime) {
        PreparedAction prepared = action.prepare(events, snapshot, runtime);
        if (!prepared.expectedVersions().isEmpty() && prepared.storageId() == null)
            throw new IllegalStateException("Background action storage must be persisted before transition execution");
        jobs.add(executor.reserve(prepared, runtime.languageModelGateway(), completion));
        LatencyTrace.measure("action_queued", () -> null);
    }
    void commit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { jobs.forEach(job -> job.commit(agentId)); }
                @Override public void afterCompletion(int status) { jobs.forEach(BackgroundActionExecutor.Reservation::close); }
            });
        } else jobs.forEach(job -> job.commit(agentId));
        handedOff = true;
    }
    @Override public void close() { if (!handedOff) jobs.forEach(BackgroundActionExecutor.Reservation::close); }
}
