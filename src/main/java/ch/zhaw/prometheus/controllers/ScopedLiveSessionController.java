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
public class ScopedLiveSessionController {
    private final ScopedLiveSessionService service;
    private final ch.zhaw.prometheus.application.ScopedDemoService demo;
    private final ch.zhaw.prometheus.application.LiveTranscriptIngressService ingress;
    public ScopedLiveSessionController(ScopedLiveSessionService service, ch.zhaw.prometheus.application.ScopedDemoService demo,
            ch.zhaw.prometheus.application.LiveTranscriptIngressService ingress) {
        this.service = service; this.demo = demo; this.ingress = ingress;
    }

    @GetMapping("/demo/live/capabilities")
    public Capabilities capabilities(@RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return service.capabilities(code);
    }

    @GetMapping("/demo/agents/{agentId}/live/transcripts")
    public ResponseEntity<java.util.List<ch.zhaw.prometheus.application.LiveTranscriptIngressService.Outcome>> transcripts(@PathVariable UUID agentId,
            @RequestParam UUID sessionId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return !demo.hasVisibleAgent(code, agentId) ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(ingress.history(agentId, sessionId));
    }

    @GetMapping("/demo/agents/{agentId}/live/history")
    public ResponseEntity<java.util.List<ch.zhaw.prometheus.model.event.ConversationProjection.View>> history(@PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(demo.getAgentEventHistory(code, agentId).map(events -> events.stream()
                .map(ch.zhaw.prometheus.model.event.ConversationProjection::view).toList()));
    }

    @GetMapping("/demo/agents/{agentId}/live/capabilities")
    public ResponseEntity<Capabilities> capabilities(@PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(service.capabilities(code, agentId));
    }
    @PostMapping("/demo/agents/{agentId}/live/sessions")
    public ResponseEntity<SessionView> create(@PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code,
            @RequestBody LiveSessionRequest request) {
        return service.create(code, agentId, request).map(value -> ResponseEntity.status(HttpStatus.CREATED).body(value))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    @GetMapping("/demo/agents/{agentId}/live/sessions/{handle}")
    public ResponseEntity<StatusView> status(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code) {
        return ResponseEntity.of(service.status(code, agentId, handle));
    }
    @PostMapping("/demo/agents/{agentId}/live/sessions/{handle}/input")
    public ResponseEntity<StatusView> input(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code,
            @RequestParam boolean muted) {
        return ResponseEntity.of(service.mute(code, agentId, handle, muted));
    }
    @GetMapping("/demo/agents/{agentId}/live/sessions/{handle}/updates")
    public ResponseEntity<UpdatesView> updates(@PathVariable UUID agentId, @PathVariable UUID handle,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String code,
            @RequestParam(defaultValue = "-1") long transcriptRevision) {
        return ResponseEntity.of(service.updates(code, agentId, handle, transcriptRevision));
    }
    @DeleteMapping("/demo/agents/{agentId}/live/sessions/{handle}")
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
    public record ProviderError(String code) {}
    @ExceptionHandler(LiveProviderException.class) public ResponseEntity<ProviderError> provider(LiveProviderException error) {
        org.slf4j.LoggerFactory.getLogger(ScopedLiveSessionController.class).warn(
                "Live provider failure: reason={}, providerStatus={}", error.getReason(), error.getProviderStatus());
        String code = switch (error.getReason()) {
            case QUOTA_EXHAUSTED -> "live_provider_quota_exhausted";
            case RATE_LIMITED -> "live_provider_rate_limited";
            case AUTHENTICATION -> "live_provider_authentication";
            case ACCESS_DENIED -> "live_provider_access_denied";
            case UNAVAILABLE -> null;
        };
        return code == null ? ResponseEntity.status(HttpStatus.BAD_GATEWAY).build()
                : ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ProviderError(code));
    }
}
