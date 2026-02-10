package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregatorType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public abstract class Decision extends PersistedNode {

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Policy policy;

    @Column(length = 3000)
    private String eventSelectorSpecJson;
    @Enumerated(EnumType.STRING)
    private SnapshotAggregatorType snapshotAggregatorType;

    protected Decision() {

    }

    public Decision(Policy policy) {
        this.policy = policy;
        this.snapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
    }

    public Decision(Policy policy, EventSelectorSpec eventSelectorSpec) {
        this(policy);
        this.setEventSelectorSpec(eventSelectorSpec);
    }

    public Policy getPolicy() {
        if (this.policy == null) {
            throw new IllegalStateException("decision policy not set");
        }
        return this.policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public EventSelector getEventSelector() {
        EventSelectorSpec spec = this.getEventSelectorSpec();
        if (spec == null) {
            return null;
        }
        return spec.toEventSelector();
    }

    public EventSelectorSpec getEventSelectorSpec() {
        if (this.eventSelectorSpecJson == null || this.eventSelectorSpecJson.isBlank()) {
            return null;
        }
        return EventSelectorSpec.fromJson(this.eventSelectorSpecJson);
    }

    public void setEventSelectorSpec(EventSelectorSpec eventSelectorSpec) {
        if (eventSelectorSpec == null) {
            this.eventSelectorSpecJson = null;
            return;
        }
        this.eventSelectorSpecJson = eventSelectorSpec.toJson();
    }

    public SnapshotAggregator getSnapshotAggregator() {
        SnapshotAggregatorType type = this.snapshotAggregatorType == null
                ? SnapshotAggregatorType.DEFAULT_OBSERVATION
                : this.snapshotAggregatorType;
        return type.create();
    }

    public SnapshotAggregatorType getSnapshotAggregatorType() {
        if (this.snapshotAggregatorType == null) {
            this.snapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        return this.snapshotAggregatorType;
    }

    public void setSnapshotAggregatorType(SnapshotAggregatorType snapshotAggregatorType) {
        if (snapshotAggregatorType == null) {
            this.snapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
            return;
        }
        this.snapshotAggregatorType = snapshotAggregatorType;
    }

    public boolean decide(EventHistory events, PolicyRuntime runtime) {
        return this.getPolicy().decide(events, runtime.promptMessageAssembler(), runtime.languageModelGateway());
    }

    public boolean decide(EventHistory events, ObservationSnapshot snapshot, PolicyRuntime runtime) {
        return this.decide(events, runtime);
    }

    @Override
    public String toString() {
        return "Decision with policy " + this.policy;
    }
}

