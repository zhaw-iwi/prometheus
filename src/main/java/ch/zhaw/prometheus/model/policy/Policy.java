package ch.zhaw.prometheus.model.policy;

import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.PersistedNode;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(length = 60)
public abstract class Policy extends PersistedNode {
    public Policy withOuterPolicy(Policy outerPolicy) {
        return this;
    }

    public abstract BehaviourPlan onStart(State state, EventHistory events);

    public abstract BehaviourPlan onRespond(State state, EventHistory events);

    public abstract String summarise(State state, EventHistory events);

    public abstract String describe();

    public boolean decide(EventHistory events) {
        throw new UnsupportedOperationException("Policy does not support decisions.");
    }

    public JsonElement extract(EventHistory events) {
        throw new UnsupportedOperationException("Policy does not support extraction actions.");
    }

    public JsonElement summarise(EventHistory events) {
        throw new UnsupportedOperationException("Policy does not support summarisation actions.");
    }
}
