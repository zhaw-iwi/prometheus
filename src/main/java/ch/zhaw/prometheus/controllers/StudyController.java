package ch.zhaw.prometheus.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.application.study.*;

@RestController
public class StudyController {
    public static final String TOKEN_HEADER="X-Prometheus-Study-Token";
    private final StudySessions sessions;
    private final StudyTurns turns;
    private final StudyEvidence evidence;
    private final String adminToken;
    public record Invite(StudyAttempt.Mode mode, String scenario, UUID predecessor) {}
    public record Consent(String version) {}
    public StudyController(StudySessions sessions, StudyTurns turns, StudyEvidence evidence, @Value("${prometheus.admin.token:}") String adminToken) {
        this.sessions=sessions; this.turns=turns; this.evidence=evidence; this.adminToken=adminToken;
    }
    @PostMapping("/admin/study/attempts")
    public StudySessions.Invitation invite(@RequestHeader(name=AdminAccessCodeController.ADMIN_TOKEN_HEADER,required=false) String token,
            @RequestBody Invite request) {
        admin(token); return sessions.invite(request.mode(),request.scenario(),request.predecessor());
    }
    @GetMapping("/admin/study/attempts/{id}")
    public StudySessions.View inspect(@RequestHeader(name=AdminAccessCodeController.ADMIN_TOKEN_HEADER,required=false) String token,
            @PathVariable UUID id) { admin(token); return sessions.inspect(id); }
    @PostMapping("/admin/study/attempts/{id}/revoke")
    public StudySessions.View revoke(@RequestHeader(name=AdminAccessCodeController.ADMIN_TOKEN_HEADER,required=false) String token,
            @PathVariable UUID id) { admin(token); return sessions.revoke(id); }
    @GetMapping("/admin/study/attempts/{id}/export")
    public com.fasterxml.jackson.databind.JsonNode export(@RequestHeader(name=AdminAccessCodeController.ADMIN_TOKEN_HEADER,required=false) String token,
            @PathVariable UUID id) { admin(token); return evidence.export(id); }
    @GetMapping("/api/study/session")
    public StudySessions.View get(@RequestHeader(name=TOKEN_HEADER,required=false) String token) { return sessions.get(token); }
    @PostMapping("/api/study/consent")
    public StudySessions.View consent(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @RequestBody Consent consent) { return sessions.consent(token,consent.version()); }
    @PostMapping("/api/study/forms/{kind}")
    public StudySessions.View form(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @PathVariable String kind, @RequestBody Map<String,String> answers) { return sessions.form(token,kind,answers); }
    @PostMapping("/api/study/withdraw")
    public StudySessions.View withdraw(@RequestHeader(name=TOKEN_HEADER,required=false) String token) { return sessions.withdraw(token); }
    @PostMapping("/api/study/turns")
    public StudyTurns.View submit(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @RequestBody StudyTurns.Request request) { return turns.submit(token,request); }
    @GetMapping("/api/study/turns/{requestId}")
    public StudyTurns.View lookup(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @PathVariable UUID requestId) { return turns.lookup(token,requestId); }
    @GetMapping("/api/study/conversation")
    public StudyTurns.Conversation conversation(@RequestHeader(name=TOKEN_HEADER,required=false) String token) { return turns.conversation(token); }
    @PostMapping("/api/study/turns/{requestId}/cancel")
    public StudyTurns.View cancel(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @PathVariable UUID requestId) { return turns.cancel(token,requestId); }
    @PostMapping("/api/study/display")
    public ResponseEntity<Void> display(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @RequestBody StudyEvidence.Display receipt) { evidence.display(token,receipt); return ResponseEntity.noContent().build(); }
    @GetMapping("/api/study/events")
    public List<com.fasterxml.jackson.databind.JsonNode> events(@RequestHeader(name=TOKEN_HEADER,required=false) String token) {
        return evidence.participantEvents(token);
    }
    @PostMapping("/api/study/client-events")
    public ResponseEntity<Void> client(@RequestHeader(name=TOKEN_HEADER,required=false) String token,
            @RequestBody StudyEvidence.ClientEvent event) { evidence.client(token,event); return ResponseEntity.noContent().build(); }
    private void admin(String token) {
        if (token==null || adminToken.isBlank() || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),adminToken.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalid() { return ResponseEntity.badRequest().build(); }
}
