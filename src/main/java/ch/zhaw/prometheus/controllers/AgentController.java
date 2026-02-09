package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.repositories.AgentRepository;

@RestController
public class AgentController {

    @Autowired
    private AgentRepository repository;
    @Autowired
    private AgentMonitorBroadcaster monitorBroadcaster;

    @GetMapping("{agentID}/info")
    public ResponseEntity<AgentInfoView> info(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<AgentInfoView>(HttpStatus.NOT_FOUND);
        }

        AgentInfoView result = new AgentInfoView(agentMaybe.get().getId(), agentMaybe.get().getName(),
                agentMaybe.get().getDescription(), agentMaybe.get().isActive());

        return new ResponseEntity<AgentInfoView>(result, HttpStatus.OK);
    }

    @GetMapping("{agentID}/eventhistory")
    public ResponseEntity<List<Event>> eventHistory(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<List<Event>>(HttpStatus.NOT_FOUND);
        }

        List<Event> eventHistory = agentMaybe.get().getEventHistory().toList();

        return new ResponseEntity<List<Event>>(eventHistory, HttpStatus.OK);
    }

    @GetMapping("{agentID}/state")
    public ResponseEntity<AgentStateInfoView> state(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        State currentState = agentMaybe.get().getCurrentState();
        if (currentState == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String stateName = currentState.getName();
        String innerName = null;
        java.util.List<String> innerNames = java.util.List.of();
        if (currentState instanceof ch.zhaw.prometheus.model.OuterState outerState
                && outerState.getInnerCurrent() != null) {
            innerName = outerState.getInnerCurrent().getName();
            innerNames = outerState.getInnerCurrentChain();
        }
        AgentStateInfoView stateInfo = new AgentStateInfoView(stateName, innerName, innerNames);

        return new ResponseEntity<>(stateInfo, HttpStatus.OK);
    }

    @GetMapping("{agentID}/states")
    public ResponseEntity<List<String>> states(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(agentMaybe.get().listStates(), HttpStatus.OK);
    }

    @GetMapping("{agentID}/storage")
    public ResponseEntity<List<StorageEntryView>> storage(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        List<StorageEntryView> entries = agentMaybe.get().getStorage().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map((entry) -> new StorageEntryView(entry.getKey(),
                        entry.getValue() == null ? "null" : entry.getValue().toString()))
                .toList();

        return new ResponseEntity<>(entries, HttpStatus.OK);
    }

    @PostMapping("{agentID}/start")
    public ResponseEntity<ResponseView> start(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }

        Agent agent = agentMaybe.get();
        Event starter = agent.start();
        this.repository.save(agent);
        this.monitorBroadcaster.publish(agent);

        return new ResponseEntity<ResponseView>(new ResponseView(starter, agent.isActive()),
                HttpStatus.OK);
    }

    @PostMapping("{agentID}/tick")
    public ResponseEntity<ResponseView> tick(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }

        Agent agent = agentMaybe.get();
        Event response = agent.tick();
        this.repository.save(agent);
        this.monitorBroadcaster.publish(agent);

        return new ResponseEntity<ResponseView>(new ResponseView(response, agent.isActive()), HttpStatus.OK);
    }

    @PostMapping("{agentID}/respond")
    public ResponseEntity<ResponseView> respond(@PathVariable @NonNull UUID agentID,
            @RequestBody EventRequest request) {

        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return new ResponseEntity<ResponseView>(HttpStatus.BAD_REQUEST);
        }
        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        boolean hasPayload = request.getPayload() != null && !request.getPayload().isBlank();
        if (!hasContent && !hasPayload) {
            return new ResponseEntity<ResponseView>(HttpStatus.BAD_REQUEST);
        }

        Agent agent = agentMaybe.orElse(null);
        if (agent == null) {
            return new ResponseEntity<ResponseView>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getContent(),
                request.getPayload(), agent.getCurrentState().getName());
        Event response = agent.respond(event);
        this.repository.save(agent);
        this.monitorBroadcaster.publish(agent);

        return new ResponseEntity<ResponseView>(new ResponseView(response, agent.isActive()),
                HttpStatus.OK);
    }

    @DeleteMapping("{agentID}/reset")
    public ResponseEntity<ResponseView> reset(@PathVariable @NonNull UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }

        Agent agent = agentMaybe.get();
        agent.reset();
        Event response = agent.start();
        this.repository.save(agent);
        this.monitorBroadcaster.publish(agent);

        return new ResponseEntity<ResponseView>(new ResponseView(response, agent.isActive()),
                HttpStatus.OK);
    }

}
