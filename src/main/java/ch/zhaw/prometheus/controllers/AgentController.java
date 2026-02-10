package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.controllers.views.AgentStateInfoView;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.controllers.views.StorageEntryView;
import ch.zhaw.prometheus.model.event.Event;

@RestController
public class AgentController {
    private final AgentApplicationService agentService;

    public AgentController(AgentApplicationService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("{agentID}/info")
    public ResponseEntity<AgentInfoView> info(@PathVariable @NonNull UUID agentID) {
        var info = this.agentService.getAgentInfo(agentID);
        if (info.isEmpty()) {
            return new ResponseEntity<AgentInfoView>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<AgentInfoView>(info.get(), HttpStatus.OK);
    }

    @GetMapping("{agentID}/eventhistory")
    public ResponseEntity<List<Event>> eventHistory(@PathVariable @NonNull UUID agentID) {
        var events = this.agentService.getAgentEventHistory(agentID);
        if (events.isEmpty()) {
            return new ResponseEntity<List<Event>>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<List<Event>>(events.get(), HttpStatus.OK);
    }

    @GetMapping("{agentID}/state")
    public ResponseEntity<AgentStateInfoView> state(@PathVariable @NonNull UUID agentID) {
        var stateInfo = this.agentService.getAgentState(agentID);
        if (stateInfo.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(stateInfo.get(), HttpStatus.OK);
    }

    @GetMapping("{agentID}/states")
    public ResponseEntity<List<String>> states(@PathVariable @NonNull UUID agentID) {
        var states = this.agentService.getAgentStates(agentID);
        if (states.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(states.get(), HttpStatus.OK);
    }

    @GetMapping("{agentID}/storage")
    public ResponseEntity<List<StorageEntryView>> storage(@PathVariable @NonNull UUID agentID) {
        var storage = this.agentService.getAgentStorage(agentID);
        if (storage.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(storage.get(), HttpStatus.OK);
    }

    @PostMapping("{agentID}/start")
    public ResponseEntity<ResponseView> start(@PathVariable @NonNull UUID agentID) {
        var response = this.agentService.start(agentID);
        if (response.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<ResponseView>(response.get(), HttpStatus.OK);
    }

    @PostMapping("{agentID}/tick")
    public ResponseEntity<ResponseView> tick(@PathVariable @NonNull UUID agentID) {
        var response = this.agentService.tick(agentID);
        if (response.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<ResponseView>(response.get(), HttpStatus.OK);
    }

    @PostMapping("{agentID}/respond")
    public ResponseEntity<ResponseView> respond(@PathVariable @NonNull UUID agentID,
            @RequestBody EventRequest request) {
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return new ResponseEntity<ResponseView>(HttpStatus.BAD_REQUEST);
        }
        boolean hasPayload = request.getPayload() != null && !request.getPayload().isBlank();
        if (!hasPayload) {
            return new ResponseEntity<ResponseView>(HttpStatus.BAD_REQUEST);
        }
        var response = this.agentService.respond(agentID, request);
        if (response.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<ResponseView>(response.get(), HttpStatus.OK);
    }

    @DeleteMapping("{agentID}/reset")
    public ResponseEntity<ResponseView> reset(@PathVariable @NonNull UUID agentID) {
        var response = this.agentService.reset(agentID);
        if (response.isEmpty()) {
            return new ResponseEntity<ResponseView>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<ResponseView>(response.get(), HttpStatus.OK);
    }

}


