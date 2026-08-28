package ch.zhaw.prometheus.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ch.zhaw.prometheus.application.DemoAccessDeniedException;
import ch.zhaw.prometheus.application.ScopedTalkToMeSpeechService;
import ch.zhaw.prometheus.application.SpeechSynthesisSettings;
import ch.zhaw.prometheus.application.TalkToMeSpeechUnavailableException;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.spi.SpeechSynthesisException;

@RestController
public class TalkToMeSpeechController {
    private final ScopedTalkToMeSpeechService speechService;

    public TalkToMeSpeechController(ScopedTalkToMeSpeechService speechService) {
        this.speechService = speechService;
    }

    @PostMapping(path = "/demo/talktome/agents/{agentId}/speech", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> speech(
            @RequestHeader(value = ScopedDemoController.ACCESS_CODE_HEADER, required = false) String headerAccessCode,
            @RequestParam(value = "accessCode", required = false) String queryAccessCode,
            @PathVariable @NonNull UUID agentId,
            @RequestBody(required = false) EventRequest request,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String speed) {
        if (!isValidEventRequest(request)) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        SpeechSynthesisSettings settings = new SpeechSynthesisSettings(voice, speed);
        return this.speechService.synthesize(accessCode(headerAccessCode, queryAccessCode), agentId, request, settings)
                .map(SpeechAudioHttpResponse::stream)
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

    @ExceptionHandler(TalkToMeSpeechUnavailableException.class)
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

    private static boolean isValidEventRequest(EventRequest request) {
        if (request == null || request.getType() == null || request.getType().isBlank()
                || request.getActor() == null || request.getActor().isBlank()
                || request.getKind() == null || request.getKind().isBlank()) {
            return false;
        }
        return request.getPayload() != null && !request.getPayload().isBlank();
    }

}
