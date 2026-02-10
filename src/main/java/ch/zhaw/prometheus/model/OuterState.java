package ch.zhaw.prometheus.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PolicyResult;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;

@Entity
public class OuterState extends State {

    protected OuterState() {

    }

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State innerInitial;
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State innerCurrent;

    public OuterState(String prompt, String name, List<Transition> transitions, State innerInitial) {
        super(name, new PromptPolicy(prompt, null, PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                transitions);
        this.innerInitial = innerInitial;
        this.innerCurrent = this.innerInitial;
    }

    public OuterState(String prompt, String name, List<Transition> transitions, State innerInitial,
            String summarisePrompt) {
        super(name, new PromptPolicy(prompt, null, summarisePrompt), transitions, true, false);
        this.innerInitial = innerInitial;
        this.innerCurrent = this.innerInitial;
    }

    @Override
    public boolean isActive() {
        return this.innerCurrent.isActive();
    }

    @Override
    protected void collectStates(Set<State> visited, List<State> result) {
        if (visited.contains(this)) {
            return;
        }
        super.collectStates(visited, result);
        this.innerInitial.collectStates(visited, result);
    }

    public Event start() {
        return this.start(null);
    }

    public Event start(Policy outerPolicy) {
        Policy totalPolicy = this.resolvePolicy(outerPolicy);
        return this.innerCurrent.start(totalPolicy);
    }

    public Event respond(Event event) throws TransitionException {
        return this.respond(event, null);
    }

    public Event respond(Event event, Policy outerPolicy) throws TransitionException {
        this.raiseIfTransit();
        Policy totalPolicy = this.resolvePolicy(outerPolicy);
        Event responseEvent = null;
        try {
            responseEvent = this.innerCurrent.respond(event, totalPolicy);
            return responseEvent;
        } catch (TransitionException e) {
            this.innerCurrent = e.getSubsequentState();
            if (this.innerCurrent.isStarting()) {
                responseEvent = this.innerCurrent.start(totalPolicy);
            } else {
                responseEvent = this.innerCurrent.respond(event, totalPolicy);
            }
            return responseEvent;
        }
    }

    @Override
    public void acknowledge(Event event, Policy outerPolicy) throws TransitionException {
        this.raiseIfTransit();
        Policy totalPolicy = this.resolvePolicy(outerPolicy);
        try {
            this.innerCurrent.acknowledge(event, totalPolicy);
        } catch (TransitionException e) {
            this.innerCurrent = e.getSubsequentState();
            if (this.innerCurrent.isStarting()) {
                // do not append userSays to new state (cf. respond(..))
                this.innerCurrent.enter();
            } else {
                this.innerCurrent.acknowledge(event, totalPolicy);
            }
        }
    }

    @Override
    public String getTotalPolicy(Policy outerPolicy) {
        Policy totalPolicy = this.resolvePolicy(outerPolicy);
        return this.innerCurrent.getTotalPolicy(totalPolicy);
    }

    @Override
    public PolicyResult getPolicyBundle(Policy outerPolicy) {
        Policy totalPolicy = this.resolvePolicy(outerPolicy);
        return this.innerCurrent.getPolicyBundle(totalPolicy);
    }

    @Override
    public void reset() {
        this.reset(new HashSet<State>());
    }

    @Override
    protected void reset(Set<State> statesAlreadyReseted) {
        if (statesAlreadyReseted.contains(this)) {
            return;
        }
        super.reset(statesAlreadyReseted);
        this.innerCurrent = this.innerInitial;
        this.innerCurrent.reset(statesAlreadyReseted);
    }

    @Override
    public String toString() {
        return "OuterState IS-A " + super.toString() + " with inner initial " + this.innerInitial
                + " and inner current " + this.innerCurrent;
    }

    public State getInnerCurrent() {
        return this.innerCurrent;
    }

    public List<String> getInnerCurrentChain() {
        List<String> chain = new java.util.ArrayList<>();
        State current = this.innerCurrent;
        while (current != null) {
            chain.add(current.getName());
            if (current instanceof OuterState nestedOuter) {
                current = nestedOuter.getInnerCurrent();
            } else {
                break;
            }
        }
        return chain;
    }

    @Override
    public List<String> getActiveStatePath() {
        List<String> path = new java.util.ArrayList<>();
        path.add(this.getName());
        if (this.innerCurrent != null) {
            path.addAll(this.innerCurrent.getActiveStatePath());
        }
        return path;
    }

}
