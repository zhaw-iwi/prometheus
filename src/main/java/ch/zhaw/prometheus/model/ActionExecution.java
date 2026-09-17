package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;

/** Host-owned execution boundary; embedded runtimes without a host remain caller-thread only. */
@FunctionalInterface
public interface ActionExecution {
    void submit(Action action, EventHistory events, ObservationSnapshot snapshot, PolicyRuntime runtime);
}
