package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;

/** Optional host-owned preview; a transition always invalidates it before executing actions. */
public interface BehaviourSpeculation {
    void prepare(State state, Event event, PolicyRuntime runtime);
    void invalidate();
}
