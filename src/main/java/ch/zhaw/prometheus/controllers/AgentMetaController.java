package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.model.Agent;

@RestController
public class AgentMetaController {
    private final AgentApplicationService agentService;

    public AgentMetaController(AgentApplicationService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("agent")
    public ResponseEntity<List<AgentInfoView>> findAll() {
        return new ResponseEntity<List<AgentInfoView>>(this.agentService.listAgents(), HttpStatus.OK);
    }

    @GetMapping("agent/eventhistory")
    public ResponseEntity<List<Agent>> findAllEventHistory() {
        return new ResponseEntity<List<Agent>>(this.agentService.listAgentAggregates(), HttpStatus.OK);
    }

    @GetMapping("agent/{id}")
    public ResponseEntity<AgentInfoView> findById(
            @PathVariable(required = true) @org.springframework.lang.NonNull UUID id) {
        Optional<AgentInfoView> agentMaybe = this.agentService.getAgentInfo(id);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<AgentInfoView>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<AgentInfoView>(agentMaybe.get(), HttpStatus.OK);
    }

    @GetMapping("agent/{id}/eventhistory")
    public ResponseEntity<Agent> findByIdEventHistory(
            @PathVariable(required = true) @org.springframework.lang.NonNull UUID id) {
        Optional<Agent> agentMaybe = this.agentService.getAgentById(id);
        if (agentMaybe.isEmpty()) {
            return new ResponseEntity<Agent>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<Agent>(agentMaybe.get(), HttpStatus.OK);
    }
}

