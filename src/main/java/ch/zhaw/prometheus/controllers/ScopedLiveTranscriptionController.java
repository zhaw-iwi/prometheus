package ch.zhaw.prometheus.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.ScopedLiveTranscriptionService;
import ch.zhaw.prometheus.controllers.dto.LiveTranscriptionSettingsRequest;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionCapabilitiesView;
import ch.zhaw.prometheus.controllers.views.LiveTranscriptionSessionView;
import ch.zhaw.prometheus.spi.LiveTranscriptionProviderException;

@RestController
public class ScopedLiveTranscriptionController {

    private final ScopedLiveTranscriptionService transcriptionService;

    public ScopedLiveTranscriptionController(ScopedLiveTranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @GetMapping("/demo/agents/{agentId}/transcription/capabilities")
    public ResponseEntity<LiveTranscriptionCapabilitiesView> capabilities(
            @PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode) {
        return this.transcriptionService.capabilities(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/demo/agents/{agentId}/transcription/session")
    public ResponseEntity<LiveTranscriptionSessionView> createSession(
            @PathVariable UUID agentId,
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @RequestBody(required = false) LiveTranscriptionSettingsRequest request) {
        if (request == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return this.transcriptionService.createSession(accessCode(headerAccessCode, queryAccessCode), agentId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(DemoAccessDeniedException.class)
    public ResponseEntity<Void> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> badRequest() {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LiveTranscriptionProviderException.class)
    public ResponseEntity<Void> providerFailure() {
        return new ResponseEntity<>(HttpStatus.BAD_GATEWAY);
    }

    private static String accessCode(String headerAccessCode, String queryAccessCode) {
        return queryAccessCode == null || queryAccessCode.isBlank() ? headerAccessCode : queryAccessCode;
    }
}
