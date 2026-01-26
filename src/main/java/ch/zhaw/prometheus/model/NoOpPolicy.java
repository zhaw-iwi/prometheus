package ch.zhaw.prometheus.model;

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
