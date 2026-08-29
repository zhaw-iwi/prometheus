package ch.zhaw.prometheus.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ch.zhaw.prometheus.application.BehaviourSpeechUnavailableException;
import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.ScopedBehaviourSpeechService;
import ch.zhaw.prometheus.application.SpeechSynthesisSettings;
import ch.zhaw.prometheus.controllers.views.BehaviourSpeechReferenceView;
import ch.zhaw.prometheus.spi.SpeechSynthesisException;

@RestController
public class BehaviourSpeechController {
    private final ScopedBehaviourSpeechService speechService;

    public BehaviourSpeechController(ScopedBehaviourSpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping("/demo/agents/{agentId}/behaviours/{eventId}/speech")
    public ResponseEntity<StreamingResponseBody> speech(
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId,
            @PathVariable @NonNull UUID eventId,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String speed) {
        SpeechSynthesisSettings settings = new SpeechSynthesisSettings(voice, speed);
        return this.speechService.synthesize(accessCode(headerAccessCode, queryAccessCode), agentId, eventId, settings)
                .map(SpeechAudioHttpResponse::stream)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/demo/agents/{agentId}/behaviours/latest/speech")
    public ResponseEntity<BehaviourSpeechReferenceView> latestSpeech(
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId) {
        return this.speechService.latestAssistantSpeechEventId(accessCode(headerAccessCode, queryAccessCode), agentId)
                .map(eventId -> ResponseEntity.ok(new BehaviourSpeechReferenceView(eventId)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @ExceptionHandler(DemoAccessDeniedException.class)
    public ResponseEntity<Void> unauthorized() {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> badRequest() {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BehaviourSpeechUnavailableException.class)
    public ResponseEntity<Void> noSpeechBehaviour() {
        return new ResponseEntity<>(HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SpeechSynthesisException.class)
    public ResponseEntity<Void> speechSynthesisFailure() {
        return new ResponseEntity<>(HttpStatus.BAD_GATEWAY);
    }

    private static String accessCode(String headerAccessCode, String queryAccessCode) {
        return queryAccessCode == null || queryAccessCode.isBlank() ? headerAccessCode : queryAccessCode;
    }
}
