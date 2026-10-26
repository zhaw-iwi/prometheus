package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.SensoryRemarks;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;

/** Core opt-in extension; saved TaskPolicy instances retain their original contract. */
@Entity
public class CoreTaskPolicy extends TaskPolicy implements ExternalVoicePolicy {
    protected CoreTaskPolicy() {}
    public CoreTaskPolicy(Storage storage, String instructions) { super(storage, instructions); }
    @Override public boolean supportsLiveState(State state) { return state instanceof TaskState; }
    @Override public LiveActivityContext liveActivityContext() {
        if (!storage().containsKey(SensoryRemarks.PENDING)) return null;
        // Remain activity-aware through expiry; projection may read this accessor more than once.
        var pending = SensoryRemarks.context(storage(), Instant.now());
        String task = "Task phase: " + TaskMemory.phase(storage()) + ". ";
        for (String key : java.util.List.of(TaskMemory.SPEC, TaskMemory.DRAFT)) if (storage().containsKey(key))
            task += (key.equals(TaskMemory.SPEC) ? "Agreed goal: " : "Proposed goal (not active): ")
                    + TaskSpec.parse(storage().get(key)).goal() + ". ";
        return pending == null ? new LiveActivityContext(task + "No automatic sensory comment is currently requested.", null)
                : new LiveActivityContext(task + pending.facts(), pending.continuation(), pending.refreshAt());
    }
    @Override public void reset() { super.reset(); SensoryRemarks.reset(storage()); }
}
