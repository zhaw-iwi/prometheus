package ch.zhaw.prometheus.controllers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.application.RealtimeCallOrchestrationService;
import ch.zhaw.prometheus.application.RealtimeCallSettings;
import ch.zhaw.prometheus.controllers.views.RealtimeCallView;
import ch.zhaw.prometheus.controllers.views.RealtimeSessionView;
import ch.zhaw.prometheus.spi.RealtimeCallInfo;
import ch.zhaw.prometheus.spi.RealtimeSessionClient;
import ch.zhaw.prometheus.spi.RealtimeSessionInfo;

@RestController
public class RealtimeController {
    private final RealtimeSessionClient realtimeSessionClient;
    private final RealtimeCallOrchestrationService realtimeCallService;

    public RealtimeController(RealtimeSessionClient realtimeSessionClient,
            RealtimeCallOrchestrationService realtimeCallService) {
        this.realtimeSessionClient = realtimeSessionClient;
        this.realtimeCallService = realtimeCallService;
    }

    @PostMapping(path = "{agentID}/realtime/call", consumes = { "application/sdp", MediaType.TEXT_PLAIN_VALUE })
    public ResponseEntity<RealtimeCallView> createCall(@PathVariable UUID agentID,
            @RequestBody(required = false) String offerSdp,
            @RequestParam(required = false) String voice,
            @RequestParam(required = false) String turnDetection,
            @RequestParam(defaultValue = "true") boolean generateComplement) {
        if (agentID == null || offerSdp == null || offerSdp.isBlank()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        RealtimeCallSettings settings;
        try {
            settings = new RealtimeCallSettings(voice, turnDetection, generateComplement);
        } catch (IllegalArgumentException failure) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        Optional<RealtimeCallInfo> call = this.realtimeCallService.createCall(agentID, offerSdp, settings);
        return call.map(RealtimeController::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("realtime/calls/{callId}")
    public ResponseEntity<Void> closeCall(@PathVariable String callId) {
        this.realtimeCallService.closeCall(callId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("realtime/transcription/session")
    public ResponseEntity<RealtimeSessionView> createTranscriptionSession() {
        RealtimeSessionInfo session = this.realtimeSessionClient.createTranscriptionSession();
        RealtimeSessionView view = new RealtimeSessionView(
                session.getClientSecret(),
                session.getModel(),
                session.getRealtimeCallsUrl());
        return new ResponseEntity<>(view, HttpStatus.OK);
    }

    private static RealtimeCallView toView(RealtimeCallInfo call) {
        return new RealtimeCallView(call.getSdp(), call.getModel(), call.getCallId());
    }
}

