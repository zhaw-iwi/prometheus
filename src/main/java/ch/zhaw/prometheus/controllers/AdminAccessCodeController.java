package ch.zhaw.prometheus.controllers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.application.DuplicateAccessCodeException;
import ch.zhaw.prometheus.controllers.dto.AccessCodeAgentTypesRequest;
import ch.zhaw.prometheus.controllers.dto.AccessCodeCreateRequest;
import ch.zhaw.prometheus.controllers.dto.AccessCodeUpdateRequest;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;

@RestController
public class AdminAccessCodeController {
    public static final String ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";

    private final AccessCodeAdminService service;
    private final String adminToken;

    public AdminAccessCodeController(AccessCodeAdminService service,
            @Value("${prometheus.admin.token:}") String adminToken) {
        this.service = service;
        this.adminToken = adminToken;
    }

    @GetMapping("/admin/agent-types")
    public ResponseEntity<List<AdminAgentTypeView>> agentTypes(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        return new ResponseEntity<>(this.service.listAgentTypes(), HttpStatus.OK);
    }

    @PostMapping("/admin/access-codes")
    public ResponseEntity<AccessCodeView> createAccessCode(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @RequestBody(required = false) AccessCodeCreateRequest request) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            AccessCodeView created = this.service.createAccessCode(request.getCode(), request.getEnabled());
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (DuplicateAccessCodeException exception) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        } catch (IllegalArgumentException exception) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/admin/access-codes")
    public ResponseEntity<List<AccessCodeView>> accessCodes(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        return new ResponseEntity<>(this.service.listAccessCodes(), HttpStatus.OK);
    }

    @PatchMapping("/admin/access-codes/{id}")
    public ResponseEntity<AccessCodeView> updateAccessCode(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID id,
            @RequestBody(required = false) AccessCodeUpdateRequest request) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            Optional<AccessCodeView> updated = this.service.updateAccessCodeEnabled(id, request.getEnabled());
            if (updated.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(updated.get(), HttpStatus.OK);
        } catch (IllegalArgumentException exception) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping("/admin/access-codes/{id}/agent-types")
    public ResponseEntity<AccessCodeView> replaceAllowedAgentTypes(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID id,
            @RequestBody(required = false) AccessCodeAgentTypesRequest request) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            Optional<AccessCodeView> updated = this.service.replaceAllowedAgentTypes(id, request.getAgentTypeKeys());
            if (updated.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(updated.get(), HttpStatus.OK);
        } catch (IllegalArgumentException exception) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/admin/access-codes/{id}/agents")
    public ResponseEntity<List<AgentInfoView>> accessCodeAgents(
            @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String token,
            @PathVariable UUID id) {
        if (!this.isAuthorized(token)) {
            return this.unauthorized();
        }
        Optional<List<AgentInfoView>> agents = this.service.listAgents(id);
        if (agents.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(agents.get(), HttpStatus.OK);
    }

    private boolean isAuthorized(String token) {
        return this.adminToken != null
                && !this.adminToken.isBlank()
                && token != null
                && this.adminToken.equals(token);
    }

    private <T> ResponseEntity<T> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
}
