package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.event.EventHistory;
import jakarta.persistence.Entity;

@Entity
public class NoOpPolicy extends Policy {
    @Override
    public String onStart(State state, EventHistory events) {
        return null;
    }

    @Override
    public String onRespond(State state, EventHistory events) {
        return null;
    }

    @Override
    public String summarise(State state, EventHistory events) {
        return null;
    }

    @Override
    public String describe() {
        return "";
    }
}
