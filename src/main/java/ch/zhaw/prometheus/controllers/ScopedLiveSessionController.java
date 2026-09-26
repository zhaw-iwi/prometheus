package ch.zhaw.prometheus.controllers;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.LiveSessionUnavailableException;
import ch.zhaw.prometheus.application.ScopedLiveSessionService;
import ch.zhaw.prometheus.application.ScopedLiveSessionService.*;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.spi.live.LiveProviderException;

@RestController
@RequestMapping("/demo/agents/{agentId}/live")
public class ScopedLiveSessionController {
    private final ScopedLiveSessionService service;
    public ScopedLiveSessionController(ScopedLiveSessionService service) { this.service = service; }

    @GetMapping("/capabilities")
    public ResponseEntity<Capabilities> capabilities(@PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(service.capabilities(code, agentId));
    }
    @PostMapping("/sessions")
    public ResponseEntity<SessionView> create(@PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code,
            @RequestBody LiveSessionRequest request) {
        return service.create(code, agentId, request).map(value -> ResponseEntity.status(HttpStatus.CREATED).body(value))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/sessions/{handle}")
    public ResponseEntity<StatusView> status(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(service.status(code, agentId, handle));
    }
    @PostMapping("/sessions/{handle}/input")
    public ResponseEntity<StatusView> input(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code,
            @RequestParam boolean muted) {
        return ResponseEntity.of(service.mute(code, agentId, handle, muted));
    }
    @DeleteMapping("/sessions/{handle}")
    public ResponseEntity<StatusView> close(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(service.close(code, agentId, handle));
    }
    @ExceptionHandler(DemoAccessDeniedException.class) public ResponseEntity<Void> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<Void> invalid() {
        return ResponseEntity.badRequest().build();
    }
    @ExceptionHandler(LiveSessionUnavailableException.class) public ResponseEntity<Void> unavailable(LiveSessionUnavailableException error) {
        return ResponseEntity.status(error.isConflict() ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    @ExceptionHandler(LiveProviderException.class) public ResponseEntity<Void> provider() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
}
