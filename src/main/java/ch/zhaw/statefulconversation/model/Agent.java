package ch.zhaw.statefulconversation.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.JsonElement;

import ch.zhaw.statefulconversation.spi.ContenFilterException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

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

    public Agent(String name, String description, State initialState) {
        this(name, description, initialState, null);
    }

    public Agent(String name, String description, State initialState, Storage storage) {
        this.name = name;
        this.description = description;
        this.initialState = initialState;
        this.storage = storage;
        this.currentState = this.initialState;
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

    public List<Event> getConversation() {
        if (this.isActive()) {
            return this.currentState.getEventHistory().toList();
        }
        return this.initialState.getEventHistory().toList();
    }

    public String summarise() {
        // @TODO: if this.currentState is an OuterState which is inactive due to an
        // internal final state, then this consequence does not make sense
        if (this.isActive()) {
            return this.currentState.summarise();
        }
        return this.initialState.summarise();
    }

    public Response start() {
        try {
            return this.currentState.start();
        } catch (ContenFilterException e) {
            throw e;
        }
    }

    public Response respond(Event event) {
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

    public PromptResult getTotalPrompt() {
        return this.currentState.getPromptBundle();
    }

    public Response reRespond() {

        if (!this.isActive()) {
            throw new RuntimeException("cannot rerespond if agent is inactive.");
        }

        String lastUserSays = this.currentState.getEventHistory().removeLastTwoUtteranceEvents();
        return this.respond(Event.userUtterance(lastUserSays, this.currentState.getName()));
    }

    public void appendAssistantResponse(String assistantSays) {
        this.currentState.appendAssistantSays(assistantSays);
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
    }

    public void resetCurrentState() {
        this.currentState.reset();
    }

    @Override
    public String toString() {
        return "Agent with current state " + this.currentState;
    }
}
