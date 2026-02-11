package ch.zhaw.prometheus.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.application.BehaviourGenerationOutcome;
import ch.zhaw.prometheus.controllers.views.BehaviourGenerateRequest;

@RestController
public class AgentBehaviourController {
    private final AgentApplicationService agentService;

    public AgentBehaviourController(AgentApplicationService agentService) {
        this.agentService = agentService;
    }

    @GetMapping(path = "{agentID}/behaviour/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable UUID agentID) {
        Optional<SseEmitter> emitter = this.agentService.subscribeBehaviour(agentID);
        if (emitter.isEmpty()) {
            return new ResponseEntity<>(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(emitter.get(), org.springframework.http.HttpStatus.OK);
    }

    @PostMapping(path = "{agentID}/behaviour/generate")
    public ResponseEntity<Void> generate(@PathVariable UUID agentID,
            @RequestBody(required = false) BehaviourGenerateRequest request) {
        BehaviourGenerationOutcome outcome = this.agentService.generate(agentID,
                request == null ? null : request.getOmitModalities());
        return switch (outcome) {
            case GENERATED -> new ResponseEntity<>(org.springframework.http.HttpStatus.OK);
            case NO_BEHAVIOUR_GENERATED -> new ResponseEntity<>(org.springframework.http.HttpStatus.CONFLICT);
            case AGENT_NOT_FOUND -> new ResponseEntity<>(org.springframework.http.HttpStatus.NOT_FOUND);
        };
    }
}
