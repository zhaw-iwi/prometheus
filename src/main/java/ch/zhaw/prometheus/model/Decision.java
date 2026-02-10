package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregator;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

@Entity
public abstract class Decision extends PersistedNode {

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Policy policy;

    @Transient
    private EventSelector eventSelector;
    @Transient
    private SnapshotAggregator snapshotAggregator;

    protected Decision() {

    }

    public Decision(Policy policy) {
        this.policy = policy;
    }

    public Decision(Policy policy, EventSelector eventSelector) {
        this(policy);
        this.eventSelector = eventSelector;
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
        return this.eventSelector;
    }

    public void setEventSelector(EventSelector eventSelector) {
        this.eventSelector = eventSelector;
    }

    public SnapshotAggregator getSnapshotAggregator() {
        if (this.snapshotAggregator == null) {
            this.snapshotAggregator = DefaultObservationSnapshotAggregator.INSTANCE;
        }
        return this.snapshotAggregator;
    }

    public void setSnapshotAggregator(SnapshotAggregator snapshotAggregator) {
        this.snapshotAggregator = snapshotAggregator;
    }

    public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.getPolicy().decide(events, assembler, languageModelGateway);
    }

    public boolean decide(EventHistory events, ObservationSnapshot snapshot,
            ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.decide(events, assembler, languageModelGateway);
    }

    @Override
    public String toString() {
        return "Decision with policy " + this.policy;
    }
}

