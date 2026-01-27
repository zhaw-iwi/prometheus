package ch.zhaw.prometheus.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.spi.ContenFilterException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;

@Entity
public class Agent {

    @Id
    @GeneratedValue
    private UUID id;

    public UUID getId() {
        return this.id;
    }

    protected Agent() {

    }

    private String name;
    private String description;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State initialState;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State currentState;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private EventHistory eventHistory;

    public Agent(String name, String description, State initialState) {
        this(name, description, initialState, null);
    }

    public Agent(String name, String description, State initialState, Storage storage) {
        this.name = name;
        this.description = description;
        this.initialState = initialState;
        this.storage = storage;
        this.currentState = this.initialState;
        this.eventHistory = new EventHistory();
        this.attachEventHistory();
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public State getCurrentState() {
        return this.currentState;
    }

    public EventHistory getEventHistory() {
        return this.eventHistory;
    }

    public List<Event> getEventsForState(String stateName) {
        if (this.eventHistory == null) {
            return List.of();
        }
        return this.eventHistory.selectList(EventSelector.stateName(stateName));
    }

    @JsonIgnore
    public Map<String, JsonElement> getStorage() {
        if (this.storage == null) {
            return java.util.Map.of();
        }
        return this.storage.toMap();
    }

    public boolean isActive() {
        return this.currentState.isActive();
    }

    public Event start() {
        try {
            return this.currentState.start();
        } catch (ContenFilterException e) {
            throw e;
        }
    }

    public Event respond(Event event) {
        try {
            return this.currentState.respond(event);
        } catch (ContenFilterException e) {
            throw e;
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                return this.start();
            }
            return this.respond(event);
        }
    }

    public void acknowledge(Event event) {
        try {
            this.currentState.acknowledge(event);
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                this.currentState.enter();
            } else {
                this.acknowledge(event);
            }
        }
    }

    public PolicyResult getTotalPolicy() {
        return this.currentState.getPolicyBundle();
    }

    public List<String> listStates() {
        Set<State> visited = new HashSet<>();
        List<State> states = new ArrayList<>();
        this.initialState.collectStates(visited, states);
        return states.stream().map(State::getName).distinct().toList();
    }

    public void reset() {
        // @TODO: if is starting = True, consider sending starting message again.
        this.currentState = this.initialState;
        this.currentState.reset();
        if (this.eventHistory != null) {
            this.eventHistory.reset();
        }
    }

    public void resetCurrentState() {
        this.currentState.reset();
    }

    @Override
    public String toString() {
        return "Agent with current state " + this.currentState;
    }

    @PostLoad
    private void postLoad() {
        if (this.eventHistory == null) {
            this.eventHistory = new EventHistory();
        }
        this.attachEventHistory();
    }

    private void attachEventHistory() {
        if (this.initialState == null || this.eventHistory == null) {
            return;
        }
        Set<State> visited = new HashSet<>();
        List<State> states = new ArrayList<>();
        this.initialState.collectStates(visited, states);
        for (State state : states) {
            state.setEventHistory(this.eventHistory);
        }
    }
}
