package ch.zhaw.prometheus.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.zhaw.prometheus.controllers.views.RealtimeSessionView;
import ch.zhaw.prometheus.spi.RealtimeSessionClient;
import ch.zhaw.prometheus.spi.RealtimeSessionInfo;

@RestController
public class RealtimeController {
    private final RealtimeSessionClient realtimeSessionClient;

    public RealtimeController(RealtimeSessionClient realtimeSessionClient) {
        this.realtimeSessionClient = realtimeSessionClient;
    }

    @PostMapping("realtime/session")
    public ResponseEntity<RealtimeSessionView> createSession() {
        RealtimeSessionInfo session = this.realtimeSessionClient.createSession();
        RealtimeSessionView view = new RealtimeSessionView(
                session.getClientSecret(),
                session.getModel(),
                session.getRealtimeCallsUrl());
        return new ResponseEntity<>(view, HttpStatus.OK);
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
}

