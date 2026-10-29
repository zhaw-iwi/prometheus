package ch.zhaw.prometheus.agentdefs.core;

import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.*;
import jakarta.persistence.Entity;

/** Local routing; unknown or unmatched observations never invoke the planner. */
@Entity
public class CoordinationDecision extends Decision {
    private String coordinationCondition;
    protected CoordinationDecision() {}
    public CoordinationDecision(Storage storage, String condition) {
        super(new TaskPolicy(storage, "")); coordinationCondition = condition;
    }
    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        var storage = ((TaskPolicy) getPolicy()).storage();
        if ("DELIVERY".equals(coordinationCondition)) return !events.isEmpty()
                && CoordinationDelivery.matches(storage, events.toList().getLast(), runtime, java.time.Instant.now());
        if ("OBSERVE_PREPARATION".equals(coordinationCondition))
            return "PREPARATION".equals(CoordinationMemory.stage(storage)) && !events.isEmpty()
                    && TaskSpec.FIELDS.containsKey(events.toList().getLast().getType());
        return coordinationCondition.equals(CoordinationMemory.stage(storage));
    }
}
