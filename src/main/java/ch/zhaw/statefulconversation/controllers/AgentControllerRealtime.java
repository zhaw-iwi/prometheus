package ch.zhaw.statefulconversation.controllers;

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

import ch.zhaw.statefulconversation.controllers.views.PromptResponseView;
import ch.zhaw.statefulconversation.controllers.views.EventRequest;
import ch.zhaw.statefulconversation.model.Agent;
import ch.zhaw.statefulconversation.model.Event;
import ch.zhaw.statefulconversation.repositories.AgentRepository;

@RestController
public class AgentControllerRealtime {

    @Autowired
    private AgentRepository repository;

    @GetMapping("{agentID}/prompt")
    public ResponseEntity<PromptResponseView> prompt(@PathVariable UUID agentID) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        PromptResponseView view = new PromptResponseView(agentMaybe.get().getTotalPrompt(),
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
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Agent agent = agentMaybe.get();
        if (request.getType() != null && !request.getType().isBlank()
                && !Event.TYPE_USER_UTTERANCE.equals(request.getType())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Event event = Event.userUtterance(request.getContent(), agent.getCurrentState().getName());
        agent.acknowledge(event);
        this.repository.save(agent);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("{agentID}/assistant")
    public ResponseEntity<Void> assistant(@PathVariable UUID agentID, @RequestBody EventRequest request) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Agent agent = agentMaybe.get();
        if (request.getType() != null && !request.getType().isBlank()
                && !Event.TYPE_ASSISTANT_UTTERANCE.equals(request.getType())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        agent.appendAssistantResponse(request.getContent());
        this.repository.save(agent);

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
