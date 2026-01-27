package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.Policy;
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

    public boolean decide(EventHistory events) {
        return this.getPolicy().decide(events);
    }

    @Override
    public String toString() {
        return "Decision with policy " + this.policy;
    }
}
