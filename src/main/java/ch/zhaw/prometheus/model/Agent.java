package ch.zhaw.prometheus.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonElement;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.regulation.ModulationBundle;
import ch.zhaw.prometheus.model.regulation.NoOpRegulationSystem;
import ch.zhaw.prometheus.model.regulation.RegulationContext;
import ch.zhaw.prometheus.model.regulation.RegulationResult;
import ch.zhaw.prometheus.model.regulation.RegulationSystem;
import ch.zhaw.prometheus.model.snapshot.DefaultObservationSnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregator;
import ch.zhaw.prometheus.spi.ContenFilterException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

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

    @Transient
    private RegulationSystem regulationSystem;
    @Transient
    private SnapshotAggregator regulationSnapshotAggregator;
    @Transient
    private ModulationBundle latestModulation;

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
        this.regulationSystem = new NoOpRegulationSystem();
        this.regulationSnapshotAggregator = DefaultObservationSnapshotAggregator.INSTANCE;
        this.latestModulation = ModulationBundle.neutral();
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
        Event response = this.respondWithoutRegulation(event);
        this.applyRegulation(event);
        return response;
    }

    private Event respondWithoutRegulation(Event event) {
        try {
            return this.currentState.respond(event);
        } catch (ContenFilterException e) {
            throw e;
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                return this.start();
            }
            return this.respondWithoutRegulation(event);
        }
    }

    public Event tick() {
        if (!this.isActive() || this.currentState == null) {
            return null;
        }
        Event tickEvent = Event.systemTick(this.currentState.getName());
        return this.respond(tickEvent);
    }

    public void acknowledge(Event event) {
        this.acknowledgeWithoutRegulation(event);
        this.applyRegulation(event);
    }

    private void acknowledgeWithoutRegulation(Event event) {
        try {
            this.currentState.acknowledge(event);
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                this.currentState.enter();
            } else {
                this.acknowledgeWithoutRegulation(event);
            }
        }
    }

    public RegulationSystem getRegulationSystem() {
        if (this.regulationSystem == null) {
            this.regulationSystem = new NoOpRegulationSystem();
        }
        return this.regulationSystem;
    }

    public void setRegulationSystem(RegulationSystem regulationSystem) {
        this.regulationSystem = regulationSystem == null ? new NoOpRegulationSystem() : regulationSystem;
    }

    public ModulationBundle getLatestModulation() {
        if (this.latestModulation == null) {
            this.latestModulation = ModulationBundle.neutral();
        }
        return this.latestModulation;
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
        if (this.regulationSystem == null) {
            this.regulationSystem = new NoOpRegulationSystem();
        }
        if (this.regulationSnapshotAggregator == null) {
            this.regulationSnapshotAggregator = DefaultObservationSnapshotAggregator.INSTANCE;
        }
        if (this.latestModulation == null) {
            this.latestModulation = ModulationBundle.neutral();
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

    private void applyRegulation(Event triggerEvent) {
        if (triggerEvent == null || this.currentState == null) {
            return;
        }
        if (isInternalRegulationEvent(triggerEvent)) {
            return;
        }
        RegulationResult result = this.getRegulationSystem()
                .update(new RegulationContext(triggerEvent,
                        this.getRegulationSnapshotAggregator().aggregate(this.eventHistory), Instant.now()));
        if (result == null) {
            return;
        }
        this.latestModulation = result.modulation() == null ? ModulationBundle.neutral() : result.modulation();
        if (result.internalEvents() == null || result.internalEvents().isEmpty()) {
            return;
        }
        for (Event internal : result.internalEvents()) {
            if (internal == null) {
                continue;
            }
            Event normalized = new Event(
                    internal.getType(),
                    internal.getActor(),
                    internal.getKind(),
                    internal.getContent(),
                    internal.getPayload(),
                    this.currentState.getName());
            this.acknowledgeWithoutRegulation(normalized);
        }
    }

    private SnapshotAggregator getRegulationSnapshotAggregator() {
        if (this.regulationSnapshotAggregator == null) {
            this.regulationSnapshotAggregator = DefaultObservationSnapshotAggregator.INSTANCE;
        }
        return this.regulationSnapshotAggregator;
    }

    private static boolean isInternalRegulationEvent(Event event) {
        String type = event.getType();
        return type != null && type.startsWith("int.");
    }
}
