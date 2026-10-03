package ch.zhaw.prometheus.controllers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ch.zhaw.prometheus.application.GenericPolicyUpgradeService;

/** Explicit, authenticated maintenance of one saved instance; no automatic startup migration. */
@RestController
public class AdminGenericPolicyUpgradeController {
    public record ApplyRequest(String fingerprint) {}
    private final GenericPolicyUpgradeService upgrades;
    private final String token;
    public AdminGenericPolicyUpgradeController(GenericPolicyUpgradeService upgrades, @Value("${prometheus.admin.token:}") String token) {
        this.upgrades = upgrades; this.token = token;
    }
    @GetMapping("/admin/agents/{id}/generic-policy-upgrade")
    public ResponseEntity<?> preview(@PathVariable UUID id,
            @RequestHeader(name = AdminAccessCodeController.ADMIN_TOKEN_HEADER, required = false) String supplied) {
        if (!authorized(supplied)) return ResponseEntity.status(401).build();
        try { return upgrades.preview(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
        catch (IllegalArgumentException invalid) { return ResponseEntity.badRequest().build(); }
    }
    @PostMapping("/admin/agents/{id}/generic-policy-upgrade")
    public ResponseEntity<?> apply(@PathVariable UUID id,
            @RequestHeader(name = AdminAccessCodeController.ADMIN_TOKEN_HEADER, required = false) String supplied,
            @RequestBody(required = false) ApplyRequest request) {
        if (!authorized(supplied)) return ResponseEntity.status(401).build();
        if (request == null || request.fingerprint() == null || !request.fingerprint().matches("[a-f0-9]{64}"))
            return ResponseEntity.badRequest().build();
        try { return upgrades.apply(id, request.fingerprint()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build()); }
        catch (IllegalArgumentException invalid) { return ResponseEntity.badRequest().build(); }
        catch (IllegalStateException conflict) { return ResponseEntity.status(409).build(); }
    }
    private boolean authorized(String supplied) {
        return token != null && !token.isBlank() && supplied != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
