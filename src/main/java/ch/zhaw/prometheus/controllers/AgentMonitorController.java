package ch.zhaw.prometheus.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.logging.AgentMonitorBroadcaster;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.repositories.AgentRepository;

@RestController
public class AgentMonitorController {

    @Autowired
    private AgentRepository repository;

    @Autowired
    private AgentMonitorBroadcaster broadcaster;

    @GetMapping(path = "{agentID}/monitor/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable UUID agentID) {
        Optional<Agent> agentMaybe = this.repository.findById(agentID);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<>(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        SseEmitter emitter = this.broadcaster.subscribe(agentID, () -> this.repository.findById(agentID));
        return new ResponseEntity<>(emitter, org.springframework.http.HttpStatus.OK);
    }
}
