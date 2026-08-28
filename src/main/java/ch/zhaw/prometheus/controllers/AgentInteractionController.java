package ch.zhaw.prometheus.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.controllers.views.ResponseView;
import ch.zhaw.prometheus.model.policy.OutputProfile;

@RestController
public class AgentInteractionController {
    private final AgentApplicationService agentService;

    public AgentInteractionController(AgentApplicationService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("{agentID}/prompt")
    public ResponseEntity<PolicyResponseView> prompt(@PathVariable UUID agentID,
            @RequestParam(required = false) String profile) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        OutputProfile outputProfile = OutputProfile.fromNullable(profile);
        if (outputProfile == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        var prompt = this.agentService.prompt(agentID, outputProfile);
        if (prompt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(prompt.get(), HttpStatus.OK);
    }

    @PostMapping("{agentID}/acknowledge")
    public ResponseEntity<ResponseView> acknowledge(@PathVariable UUID agentID, @RequestBody EventRequest request,
            @RequestParam(required = false) String profile) {
        if (agentID == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        OutputProfile outputProfile = OutputProfile.fromNullable(profile);
        if (outputProfile == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        boolean hasPayload = request.getPayload() != null && !request.getPayload().isBlank();
        if (!hasPayload) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        var acknowledged = this.agentService.acknowledge(agentID, request, outputProfile);
        if (acknowledged.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(acknowledged.get(), HttpStatus.OK);
    }
}


