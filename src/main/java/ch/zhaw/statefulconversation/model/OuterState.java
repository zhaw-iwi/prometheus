package ch.zhaw.statefulconversation.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        super(name, new PromptStateResponsePolicy(prompt, null, PromptStateResponsePolicy.DEFAULT_SUMMARISE_PROMPT),
                transitions);
        this.innerInitial = innerInitial;
        this.innerCurrent = this.innerInitial;
    }

    public OuterState(String prompt, String name, List<Transition> transitions, State innerInitial,
            String summarisePrompt) {
        super(name, new PromptStateResponsePolicy(prompt, null, summarisePrompt), transitions, true, false);
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

    public Response start() {
        return this.start(null);
    }

    public Response start(StateResponsePolicy outerPolicy) {
        StateResponsePolicy totalPolicy = this.resolveResponsePolicy(outerPolicy);
        Response assistantResponse = this.innerCurrent.start(totalPolicy);
        this.requireSharedEventHistory().appendAssistantUtterance(assistantResponse.getText(), this);
        return assistantResponse;
    }

    public Response respond(Event event) throws TransitionException {
        return this.respond(event, null);
    }

    public Response respond(Event event, StateResponsePolicy outerPolicy) throws TransitionException {
        this.requireSharedEventHistory().appendEvent(event, this);
        this.raiseIfTransit();
        StateResponsePolicy totalPolicy = this.resolveResponsePolicy(outerPolicy);
        Response assistantResponse = null;
        try {
            assistantResponse = this.innerCurrent.respond(event, totalPolicy);
            this.requireSharedEventHistory().appendAssistantUtterance(assistantResponse.getText(), this);
            return assistantResponse;
        } catch (TransitionException e) {
            this.innerCurrent = e.getSubsequentState();
            if (this.innerCurrent.isStarting()) {
                assistantResponse = this.innerCurrent.start(totalPolicy);
            } else {
                assistantResponse = this.innerCurrent.respond(event, totalPolicy);
            }
            this.requireSharedEventHistory().appendAssistantUtterance(assistantResponse.getText(), this);
            return assistantResponse;
        }
    }

    @Override
    public void acknowledge(Event event, StateResponsePolicy outerPolicy) throws TransitionException {
        this.requireSharedEventHistory().appendEvent(event, this);
        this.raiseIfTransit();
        StateResponsePolicy totalPolicy = this.resolveResponsePolicy(outerPolicy);
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
    public String getTotalPolicy(StateResponsePolicy outerPolicy) {
        StateResponsePolicy totalPolicy = this.resolveResponsePolicy(outerPolicy);
        return this.innerCurrent.getTotalPolicy(totalPolicy);
    }

    @Override
    public PolicyResult getPolicyBundle(StateResponsePolicy outerPolicy) {
        StateResponsePolicy totalPolicy = this.resolveResponsePolicy(outerPolicy);
        return this.innerCurrent.getPolicyBundle(totalPolicy);
    }

    @Override
    public void appendAssistantSays(String assistantSays) {
        this.innerCurrent.appendAssistantSays(assistantSays);
        this.requireSharedEventHistory().appendAssistantUtterance(assistantSays, this);
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

}
