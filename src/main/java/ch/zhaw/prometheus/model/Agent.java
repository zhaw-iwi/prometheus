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
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.regulation.ModulationBundle;
import ch.zhaw.prometheus.model.regulation.NoOpRegulationSystem;
import ch.zhaw.prometheus.model.regulation.PersistableRegulationSystem;
import ch.zhaw.prometheus.model.regulation.RegulationContext;
import ch.zhaw.prometheus.model.regulation.RegulationResult;
import ch.zhaw.prometheus.model.regulation.RegulationSystem;
import ch.zhaw.prometheus.model.regulation.RegulationSystemSpec;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregator;
import ch.zhaw.prometheus.model.snapshot.SnapshotAggregatorType;
import ch.zhaw.prometheus.spi.ContenFilterException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Column(length = 10000)
    private String regulationSystemSpecJson;
    @Enumerated(EnumType.STRING)
    private SnapshotAggregatorType regulationSnapshotAggregatorType;

    @Transient
    private RegulationSystem regulationSystem;
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
        this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
        this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
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

    public Event start(PolicyRuntime runtime) {
        try {
            Event response = this.currentState.start(runtime);
            this.recordEvent(response);
            return response;
        } catch (ContenFilterException e) {
            throw e;
        }
    }

    public Event tick(PolicyRuntime runtime) {
        if (!this.isActive() || this.currentState == null) {
            return null;
        }
        Event tickEvent = Event.systemTick();
        return this.acknowledge(tickEvent, runtime);
    }

    public Event generate(PolicyRuntime runtime) {
        if (!this.isActive() || this.currentState == null) {
            return null;
        }
        Event response = this.currentState.generate(runtime);
        this.recordEvent(response);
        return response;
    }

    public Event acknowledge(Event event, PolicyRuntime runtime) {
        Event response = this.acknowledgeWithoutRegulation(event, true, runtime);
        Event responseFromRegulation = this.applyRegulation(event, runtime);
        return responseFromRegulation != null ? responseFromRegulation : response;
    }

    private Event acknowledgeWithoutRegulation(Event event, boolean recordInput, PolicyRuntime runtime) {
        try {
            if (recordInput) {
                this.recordEvent(event);
            }
            Event response = this.currentState.acknowledge(event, runtime);
            this.recordEvent(response);
            return response;
        } catch (TransitionException e) {
            this.currentState = e.getSubsequentState();
            if (this.currentState.isStarting()) {
                Event response = this.currentState.start(runtime);
                this.recordEvent(response);
                return response;
            }
            this.currentState.enter();
            return this.acknowledgeWithoutRegulation(event, false, runtime);
        }
    }

    public RegulationSystem getRegulationSystem() {
        if (this.regulationSystem == null) {
            this.regulationSystem = this.resolveRegulationSystemFromSpec();
        }
        return this.regulationSystem;
    }

    public void setRegulationSystem(RegulationSystem regulationSystem) {
        if (regulationSystem == null) {
            this.regulationSystem = new NoOpRegulationSystem();
            this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
            return;
        }
        if (!(regulationSystem instanceof PersistableRegulationSystem persistable)) {
            throw new IllegalArgumentException(
                    "regulation system must implement PersistableRegulationSystem: "
                            + regulationSystem.getClass().getName());
        }
        this.regulationSystem = regulationSystem;
        this.regulationSystemSpecJson = persistable.toSpec().toJson();
    }

    public ModulationBundle getLatestModulation() {
        if (this.latestModulation == null) {
            this.latestModulation = ModulationBundle.neutral();
        }
        return this.latestModulation;
    }

    public SnapshotAggregatorType getRegulationSnapshotAggregatorType() {
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        return this.regulationSnapshotAggregatorType;
    }

    public void setRegulationSnapshotAggregatorType(SnapshotAggregatorType regulationSnapshotAggregatorType) {
        if (regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
            return;
        }
        this.regulationSnapshotAggregatorType = regulationSnapshotAggregatorType;
    }

    public PolicyResult getTotalPolicy() {
        return this.getTotalPolicy(null);
    }

    public PolicyResult getTotalPolicy(PromptMessageAssembler promptMessageAssembler) {
        return this.getTotalPolicy(promptMessageAssembler, OutputProfile.FULL_PLAN);
    }

    public PolicyResult getTotalPolicy(PromptMessageAssembler promptMessageAssembler, OutputProfile outputProfile) {
        return this.currentState.getPolicyBundle(null, promptMessageAssembler, outputProfile);
    }

    public List<String> listStates() {
        Set<State> visited = new HashSet<>();
        List<State> states = new ArrayList<>();
        this.initialState.collectStates(visited, states);
        return states.stream().map(State::getName).distinct().toList();
    }

    public void reset() {
        this.currentState = this.initialState;
        this.currentState.reset();
        if (this.eventHistory != null) {
            this.eventHistory.reset();
        }
        this.getRegulationSystem().reset();
        this.latestModulation = ModulationBundle.neutral();
        this.syncRegulationSystemSpecFromRuntime();
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
            this.regulationSystem = this.resolveRegulationSystemFromSpec();
        }
        if (this.regulationSystemSpecJson == null || this.regulationSystemSpecJson.isBlank()) {
            this.regulationSystemSpecJson = RegulationSystemSpec.noOp().toJson();
        }
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
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

    private Event applyRegulation(Event triggerEvent, PolicyRuntime runtime) {
        if (triggerEvent == null || this.currentState == null) {
            return null;
        }
        if (isInternalRegulationEvent(triggerEvent)) {
            return null;
        }
        RegulationResult result = this.getRegulationSystem()
                .update(new RegulationContext(triggerEvent,
                        this.eventHistory,
                        this.getRegulationSnapshotAggregator().aggregate(this.eventHistory), Instant.now()));
        if (result == null) {
            return null;
        }
        this.latestModulation = result.modulation() == null ? ModulationBundle.neutral() : result.modulation();
        if (result.internalEvents() == null || result.internalEvents().isEmpty()) {
            this.syncRegulationSystemSpecFromRuntime();
            return null;
        }
        Event latestResponse = null;
        for (Event internal : result.internalEvents()) {
            if (internal == null) {
                continue;
            }
            Event normalized = new Event(
                    internal.getType(),
                    internal.getActor(),
                    internal.getKind(),
                    internal.getPayload());
            Event response = this.acknowledgeWithoutRegulation(normalized, true, runtime);
            if (response != null) {
                latestResponse = response;
            }
        }
        this.syncRegulationSystemSpecFromRuntime();
        return latestResponse;
    }

    private SnapshotAggregator getRegulationSnapshotAggregator() {
        if (this.regulationSnapshotAggregatorType == null) {
            this.regulationSnapshotAggregatorType = SnapshotAggregatorType.DEFAULT_OBSERVATION;
        }
        return this.regulationSnapshotAggregatorType.create();
    }

    private static boolean isInternalRegulationEvent(Event event) {
        String type = event.getType();
        return type != null && type.startsWith("int.");
    }

    private void recordEvent(Event event) {
        if (event == null || this.eventHistory == null || this.currentState == null) {
            return;
        }
        event.setStatePath(this.currentState.getActiveStatePath());
        this.eventHistory.appendEvent(event);
    }

    private RegulationSystem resolveRegulationSystemFromSpec() {
        if (this.regulationSystemSpecJson == null || this.regulationSystemSpecJson.isBlank()) {
            return new NoOpRegulationSystem();
        }
        RegulationSystemSpec spec = RegulationSystemSpec.fromJson(this.regulationSystemSpecJson);
        if (spec == null) {
            return new NoOpRegulationSystem();
        }
        return spec.toRegulationSystem();
    }

    private void syncRegulationSystemSpecFromRuntime() {
        if (!(this.regulationSystem instanceof PersistableRegulationSystem persistable)) {
            return;
        }
        this.regulationSystemSpecJson = persistable.toSpec().toJson();
    }
}

