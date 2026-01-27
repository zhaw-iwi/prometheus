package ch.zhaw.prometheus.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.repositories.AgentRepository;

@RestController
public class AgentControllerRealtime {

    @Autowired
    private AgentRepository repository;

    @GetMapping("{agentID}/prompt")
    public ResponseEntity<PolicyResponseView> prompt(@PathVariable UUID agentID) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        PolicyResponseView view = new PolicyResponseView(agentMaybe.get().getTotalPolicy(),
                agentMaybe.get().isActive());
        return new ResponseEntity<>(view, HttpStatus.OK);
    }

    @PostMapping("{agentID}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID agentID, @RequestBody EventRequest request) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        boolean hasContent = request.getContent() != null && !request.getContent().isBlank();
        boolean hasPayload = request.getPayload() != null && !request.getPayload().isBlank();
        if (!hasContent && !hasPayload) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Agent agent = agentMaybe.get();
        Event event = new Event(request.getType(), request.getActor(), request.getKind(), request.getContent(),
                request.getPayload(), agent.getCurrentState().getName());
        agent.acknowledge(event);
        this.repository.save(agent);

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
