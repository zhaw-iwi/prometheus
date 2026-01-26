package ch.zhaw.prometheus.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;

@Entity
public class Transition {
    private static final Logger LOGGER = LoggerFactory.getLogger(Transition.class);

    @Id
    @GeneratedValue
    private UUID id;

    protected Transition() {

    }

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderColumn(name = "decision_index")
    private List<Decision> decisions;
    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderColumn(name = "action_index")
    private List<Action> actions;
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private State subsequentState;

    public Transition(List<Decision> decisions, List<Action> actions, State subsequentState) {
        this.decisions = new ArrayList<Decision>(decisions);
        this.actions = new ArrayList<Action>(actions);
        this.subsequentState = subsequentState;
    }

    public Transition(Decision decision, Action action, State subsequentState) {
        this(List.of(decision), List.of(action), subsequentState);
    }

    public Transition(Decision decision, State subsequentState) {
        this(List.of(decision), List.of(), subsequentState);
    }

    public Transition(Action action, State subsequentState) {
        this(List.of(), List.of(action), subsequentState);
    }

    public Transition(State subsequentState) {
        this(List.of(), List.of(), subsequentState);
    }

    public State getSubsequentState() {
        return this.subsequentState;
    }

    public void addDecision(Decision decision) {
        this.decisions.add(decision);
    }

    public void addAction(Action action) {
        this.actions.add(action);
    }

    public void setSubsequenState(State subsequentState) {
        this.subsequentState = subsequentState;
    }

    public boolean decide(State state) {
        Transition.LOGGER.info("Checking decisions if transition to " + this.subsequentState.getName());
        if (this.decisions.isEmpty()) {
            Transition.LOGGER.info("No decisions present");
            return true;
        }
        EventHistory sharedEvents = state.getSharedEventHistory();
        EventSelector defaultSelector = state.getEventSelector();
        for (Decision current : this.decisions) {
            EventSelector selector = current.getEventSelector() == null ? defaultSelector : current.getEventSelector();
            EventHistory selected = sharedEvents.select(selector);
            boolean currentDecision = current.decide(selected);
            if (!currentDecision) {
                return false;
            }
        }
        return true;
    }

    public void action(State state) {
        Transition.LOGGER.info("Executing actions while transitioning to " + this.subsequentState.getName());
        if (this.actions.isEmpty()) {
            Transition.LOGGER.info("No actions present");
            return;
        }
        EventHistory sharedEvents = state.getSharedEventHistory();
        EventSelector defaultSelector = state.getEventSelector();
        for (Action current : this.actions) {
            EventSelector selector = current.getEventSelector() == null ? defaultSelector : current.getEventSelector();
            EventHistory selected = sharedEvents.select(selector);
            current.execute(selected);
        }
    }

    @Override
    public String toString() {
        return "Transition to " + this.subsequentState + " decided by " + this.decisions + " and affected by "
                + this.actions;
    }
}
