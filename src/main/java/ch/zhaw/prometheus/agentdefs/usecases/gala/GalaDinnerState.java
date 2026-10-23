package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.util.List;
import java.util.Set;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.TransitionException;
import ch.zhaw.prometheus.logging.ActivityTrace;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.task.TaskPolicy;
import jakarta.persistence.Entity;

@Entity
public class GalaDinnerState extends State {
    protected GalaDinnerState() {}
    public GalaDinnerState(String name, Storage storage, boolean starting) {
        this(name, new GalaDinnerPolicy(storage), starting);
    }
    public GalaDinnerState(String name, Policy policy, boolean starting) {
        super(name, policy, List.of(), starting, false, EventSelectorSpec.any());
    }
    @Override public Event acknowledge(Event event, Policy outerPolicy, PolicyRuntime runtime) throws TransitionException {
        if (isStarting() && Event.TYPE_HAND_SIGN.equals(event.getType()) && ownPolicy() instanceof GalaDinnerPolicy gala
                && gala.activitiesEnabled() && !Set.of("RPS", "TASK").contains(GalaDinnerMemory.mode(gala.storage())))
            ActivityTrace.cue("rps_not_started", event.getId(), event.getType());
        return super.acknowledge(event, outerPolicy, runtime);
    }
    @Override protected void reset(Set<State> visited) {
        if (visited.contains(this)) return;
        super.reset(visited);
        GalaDinnerMemory.clear(ownPolicy() instanceof TaskPolicy task ? task.storage()
                : ((GalaDinnerPolicy) ownPolicy()).storage());
    }
}
