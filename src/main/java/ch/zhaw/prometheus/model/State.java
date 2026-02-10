package ch.zhaw.prometheus.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.event.EventSelector;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Transient;

@Entity
public class State extends PersistedNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(State.class);

    protected State() {

    }

    private String name;
    private boolean isStarting;
    private boolean isOblivious;
    @Transient
    private EventSelector eventSelector;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderColumn(name = "transition_index")
    private List<Transition> transitions;
    @Transient
    protected EventHistory eventHistory;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Policy policy;

    public State(String name, Policy policy, List<Transition> transitions) {
        this.name = name;
        this.transitions = new ArrayList<Transition>(transitions);
        this.isStarting = true;
        this.isOblivious = false;
        this.policy = policy;
        this.eventSelector = EventSelector.stateName(name);
    }

    public State(String name, Policy policy, List<Transition> transitions, boolean isStarting,
            boolean isOblivious) {
        this(name, policy, transitions);
        this.isStarting = isStarting;
        this.isOblivious = isOblivious;
    }

    public State(String name, Policy policy, List<Transition> transitions, boolean isStarting,
            boolean isOblivious, EventSelector eventSelector) {
        this(name, policy, transitions, isStarting, isOblivious);
        if (eventSelector != null) {
            this.eventSelector = eventSelector;
        }
    }

    public String getName() {
        return this.name;
    }

    public boolean isStarting() {
        return this.isStarting;
    }

    public EventHistory getEventHistory() {
        return this.requireSharedEventHistory().select(this.getEventSelector());
    }

    public EventHistory getEventHistory(EventSelector selector) {
        if (selector == null) {
            return this.getEventHistory();
        }
        return this.requireSharedEventHistory().select(selector);
    }

    public EventHistory getSharedEventHistory() {
        return this.requireSharedEventHistory();
    }

    public void setEventHistory(EventHistory eventHistory) {
        this.eventHistory = eventHistory;
    }

    public EventSelector getEventSelector() {
        if (this.eventSelector == null) {
            this.eventSelector = EventSelector.stateName(this.name);
        }
        return this.eventSelector;
    }

    public void setEventSelector(EventSelector eventSelector) {
        this.eventSelector = eventSelector;
    }

    public boolean isActive() {
        return true;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }

    public void addTransition(Transition transition) {
        this.transitions.add(transition);
    }

    protected List<Transition> getTransitions() {
        return List.copyOf(this.transitions);
    }

    protected void collectStates(Set<State> visited, List<State> result) {
        if (visited.contains(this)) {
            return;
        }
        visited.add(this);
        result.add(this);
        for (Transition current : this.transitions) {
            current.getSubsequentState().collectStates(visited, result);
        }
    }

    protected void raiseIfTransit() throws TransitionException {
        State subsequentState = this.transit();
        if (subsequentState != null) {
            throw new TransitionException(subsequentState);
        }
    }

    private State transit() {
        for (Transition current : this.transitions) {
            if (this.transitThisOne(current)) {
                return current.getSubsequentState();
            }
        }
        return null;
    }

    private boolean transitThisOne(Transition transition) {
        if (transition.decide(this)) {
            State.LOGGER.info(this.getName() + ": Transition to "
                    + transition.getSubsequentState().getName() + ": YES");
            transition.action(this);
            return true;
        }
        State.LOGGER.info(this.getName() + ": Transition to "
                + transition.getSubsequentState().getName() + ": NO");
        return false;
    }

    public Event start() {
        return this.start(null);
    }

    public Event start(Policy outerPolicy) {
        this.enter();
        return this.executeStart(outerPolicy);
    }

    public Event respond(Event event) throws TransitionException {
        return this.respond(event, null);
    }

    public Event respond(Event event, Policy outerPolicy) throws TransitionException {
        this.acknowledge(event, outerPolicy);
        return this.executeResponse(outerPolicy);
    }

    public void enter() {
        State.LOGGER
                .info(this.getName() + " Starting");
        if (this.isOblivious) {
            State.LOGGER
                    .info(this.getName() + " (Oblivious)");
            this.requireSharedEventHistory().clearStateEvents(this.name);
        }
    }

    public void acknowledge(Event event) throws TransitionException {
        this.acknowledge(event, null);
    }

    public void acknowledge(Event event, Policy outerPolicy) throws TransitionException {
        State.LOGGER
                .info(this.getName() + " ACK Event payload: \"" + event.getPayload() + "\"");
        this.raiseIfTransit();
    }

    public String getTotalPolicy() {
        return this.getTotalPolicy(null);
    }

    public String getTotalPolicy(Policy outerPolicy) {
        return this.resolvePolicy(outerPolicy).describe();
    }

    public PolicyResult getPolicyBundle() {
        return this.getPolicyBundle(null);
    }

    public PolicyResult getPolicyBundle(Policy outerPolicy) {
        String totalPrompt = this.getTotalPolicy(outerPolicy);
        List<PromptMessage> promptMessages = PromptMessageAssembler.compose(this.getEventHistory(), totalPrompt);
        return new PolicyResult(this, promptMessages);
    }

    public void reset() {
        this.reset(new HashSet<State>());
    }

    protected void reset(Set<State> statesAlreadyReseted) {
        if (statesAlreadyReseted.contains(this)) {
            return;
        }
        this.requireSharedEventHistory().clearStateEvents(this.name);
        statesAlreadyReseted.add(this);
        for (Transition current : this.transitions) {
            current.getSubsequentState().reset(statesAlreadyReseted);
        }
    }

    protected EventHistory requireSharedEventHistory() {
        if (this.eventHistory == null) {
            throw new IllegalStateException("event history not attached to state " + this.name);
        }
        return this.eventHistory;
    }

    private Event executeStart(Policy outerPolicy) {
        Policy policy = this.resolvePolicy(outerPolicy);
        BehaviourPlan behaviourPlan = policy.onStart(this, this.getEventHistory());
        if (behaviourPlan == null || behaviourPlan.isEmpty()) {
            return null;
        }
        Event responseEvent = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                behaviourPlan.toJson());
        return responseEvent;
    }

    private Event executeResponse(Policy outerPolicy) {
        Policy policy = this.resolvePolicy(outerPolicy);
        BehaviourPlan behaviourPlan = policy.onRespond(this, this.getEventHistory());
        if (behaviourPlan == null || behaviourPlan.isEmpty()) {
            return null;
        }
        Event responseEvent = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                behaviourPlan.toJson());
        return responseEvent;
    }

    public List<String> getActiveStatePath() {
        return List.of(this.name);
    }

    protected Policy resolvePolicy(Policy outerPolicy) {
        if (this.policy == null) {
            this.policy = new PromptPolicy();
        }
        if (outerPolicy == null) {
            return this.policy;
        }
        return this.policy.withOuterPolicy(outerPolicy);
    }

    @Override
    public String toString() {
        return "State with name " + this.getName();
    }

}
