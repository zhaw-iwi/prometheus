package ch.zhaw.statefulconversation.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderColumn(name = "transition_index")
    private List<Transition> transitions;
    @Transient
    protected EventHistory eventHistory;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private StateResponsePolicy responsePolicy;

    public State(String name, StateResponsePolicy responsePolicy, List<Transition> transitions) {
        this.name = name;
        this.transitions = new ArrayList<Transition>(transitions);
        this.isStarting = true;
        this.isOblivious = false;
        this.responsePolicy = responsePolicy;
    }

    public State(String name, StateResponsePolicy responsePolicy, List<Transition> transitions, boolean isStarting,
            boolean isOblivious) {
        this(name, responsePolicy, transitions);
        this.isStarting = isStarting;
        this.isOblivious = isOblivious;
    }

    public String getName() {
        return this.name;
    }

    public boolean isStarting() {
        return this.isStarting;
    }

    public EventHistory getEventHistory() {
        return this.requireSharedEventHistory().filtered(EventFilter.stateName(this.name));
    }

    public EventHistory getEventHistory(EventFilter filter) {
        return this.requireSharedEventHistory().filtered(filter);
    }

    public EventHistory getSharedEventHistory() {
        return this.requireSharedEventHistory();
    }

    public void setEventHistory(EventHistory eventHistory) {
        this.eventHistory = eventHistory;
    }

    public boolean isActive() {
        return true;
    }

    public void setResponsePolicy(StateResponsePolicy responsePolicy) {
        this.responsePolicy = responsePolicy;
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
        if (transition.decide(this.getEventHistory())) {
            State.LOGGER.info(this.getName() + ": Transition to "
                    + transition.getSubsequentState().getName() + ": YES");
            transition.action(this.getEventHistory());
            return true;
        }
        State.LOGGER.info(this.getName() + ": Transition to "
                + transition.getSubsequentState().getName() + ": NO");
        return false;
    }

    public Response start() {
        return this.start(null);
    }

    public Response start(StateResponsePolicy outerPolicy) {
        this.enter();
        return this.handleStart(outerPolicy);
    }

    public Response respond(Event event) throws TransitionException {
        return this.respond(event, null);
    }

    public Response respond(Event event, StateResponsePolicy outerPolicy) throws TransitionException {
        this.acknowledge(event, outerPolicy);
        return this.handleResponse(outerPolicy);
    }

    public void enter() {
        State.LOGGER
                .info(this.getName() + " Starting");
        if (this.isOblivious) {
            State.LOGGER
                    .info(this.getName() + " Oblivious");
            this.requireSharedEventHistory().clearStateEvents(this.name);
        }
    }

    public void acknowledge(Event event) throws TransitionException {
        this.acknowledge(event, null);
    }

    public void acknowledge(Event event, StateResponsePolicy outerPolicy) throws TransitionException {
        State.LOGGER
                .info(this.getName() + " ACK Event: \"" + event.getContent() + "\"");
        this.appendEvent(event);
        this.raiseIfTransit();
    }

    public void appendAssistantSays(String assistantSays) {
        State.LOGGER
                .info(this.getName() + " ACK Assistant: \"" + assistantSays + "\"");
        this.appendAssistantUtterance(assistantSays);
    }

    public void removeLastUserEvent() {
        this.requireSharedEventHistory().removeLastUserEvent(this.name);
    }

    public String getTotalPolicy() {
        return this.getTotalPolicy(null);
    }

    public String getTotalPolicy(StateResponsePolicy outerPolicy) {
        return this.resolveResponsePolicy(outerPolicy).describe();
    }

    public PolicyResult getPolicyBundle() {
        return this.getPolicyBundle(null);
    }

    public PolicyResult getPolicyBundle(StateResponsePolicy outerPolicy) {
        String totalPrompt = this.getTotalPolicy(outerPolicy);
        List<Event> conversation = this.getEventHistory().toList();
        return new PolicyResult(this, totalPrompt, conversation);
    }

    public String summarise() {
        StateResponsePolicy policy = this.resolveResponsePolicy(null);
        String result = policy.summarise(this);
        return result == null ? "" : result;
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

    private void appendEvent(Event event) {
        this.requireSharedEventHistory().appendEvent(event, this);
    }

    private void appendAssistantUtterance(String assistantSays) {
        this.requireSharedEventHistory().appendAssistantUtterance(assistantSays, this);
    }

    protected EventHistory requireSharedEventHistory() {
        if (this.eventHistory == null) {
            throw new IllegalStateException("event history not attached to state " + this.name);
        }
        return this.eventHistory;
    }

    private Response handleStart(StateResponsePolicy outerPolicy) {
        StateResponsePolicy policy = this.resolveResponsePolicy(outerPolicy);
        String assistantSays = policy.onStart(this);
        if (assistantSays == null || assistantSays.isEmpty()) {
            return null;
        }
        this.appendAssistantUtterance(assistantSays);
        return new Response(this, assistantSays);
    }

    private Response handleResponse(StateResponsePolicy outerPolicy) {
        StateResponsePolicy policy = this.resolveResponsePolicy(outerPolicy);
        String assistantSays = policy.onRespond(this);
        if (assistantSays == null || assistantSays.isEmpty()) {
            return null;
        }
        this.appendAssistantUtterance(assistantSays);
        return new Response(this, assistantSays);
    }

    protected StateResponsePolicy resolveResponsePolicy(StateResponsePolicy outerPolicy) {
        if (this.responsePolicy == null) {
            this.responsePolicy = new PromptStateResponsePolicy();
        }
        if (outerPolicy == null) {
            return this.responsePolicy;
        }
        return this.responsePolicy.withOuterPolicy(outerPolicy);
    }

    @Override
    public String toString() {
        return "State with name " + this.getName();
    }

}
