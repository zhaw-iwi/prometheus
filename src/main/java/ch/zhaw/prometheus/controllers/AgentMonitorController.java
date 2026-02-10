package ch.zhaw.prometheus.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ch.zhaw.prometheus.application.AgentApplicationService;

@RestController
public class AgentMonitorController {
    private final AgentApplicationService agentService;

    public AgentMonitorController(AgentApplicationService agentService) {
        this.agentService = agentService;
    }

    @GetMapping(path = "{agentID}/monitor/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable UUID agentID) {
        Optional<SseEmitter> emitter = this.agentService.subscribeMonitor(agentID);
        if (emitter.isEmpty()) {
            return new ResponseEntity<>(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(emitter.get(), org.springframework.http.HttpStatus.OK);
    }
}

