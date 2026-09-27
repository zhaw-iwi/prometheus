package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import ch.zhaw.prometheus.controllers.dto.LiveSessionRequest;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.spi.live.*;

class ScopedLiveSessionServiceUnitTest {
    final UUID agent = UUID.randomUUID();
    final ScopedDemoService demo = mock(ScopedDemoService.class);
    final Clock clock = mock(Clock.class);
    final LiveProperties properties = new LiveProperties();
    final FakeGateway gateway = new FakeGateway();
    final LiveAgentContextService contexts = mock(LiveAgentContextService.class);
    final ExternalSpeechOwnership ownership = new ExternalSpeechOwnership();
    ScopedLiveSessionService service;
    @BeforeEach void setUp() {
        properties.setEnabled(true); properties.setCloseTimeoutMs(100); properties.setRequestTimeoutMs(100);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-27T00:00:00Z"));
        when(demo.getAgentInfo(anyString(), eq(agent))).thenReturn(Optional.of(new AgentInfoView(agent, "Test", "", true)));
        when(contexts.claim(anyString(), eq(agent), any())).thenAnswer(call -> {
            var epoch = UUID.randomUUID(); ownership.acquire(agent, new ch.zhaw.prometheus.model.policy.ExternalSpeech(call.getArgument(2), epoch));
            return Optional.of(new ch.zhaw.prometheus.application.live.LiveContextSnapshot(
                agent, epoch, "revision", Instant.parse("2026-09-27T00:00:00Z"), List.of("test"), "Test instructions", List.of(), 0));
        });
        service = new ScopedLiveSessionService(demo, gateway, properties, contexts, ownership, clock);
    }
    ScopedLiveSessionService.SessionView start() { return service.create("ABCDE", agent, new LiveSessionRequest("v=0 offer", "marin")).orElseThrow(); }
    @Test void scopeAndFeatureGateRunBeforeProviderAndOnlyOneSessionOwnsAgent() {
        properties.setEnabled(false);
        assertThrows(LiveSessionUnavailableException.class, this::start); assertEquals(0, gateway.created);
        properties.setEnabled(true);
        assertThrows(IllegalArgumentException.class, () -> service.create("ABCDE", agent, new LiveSessionRequest("invalid", "marin")));
        var session = start();
        assertTrue(service.status("FGHIJ", agent, session.handle()).isEmpty());
        assertTrue(service.close("ABCDE", UUID.randomUUID(), session.handle()).isEmpty());
        assertThrows(LiveSessionUnavailableException.class, this::start); assertEquals(1, gateway.created);
    }
    @Test void attachmentFailureHangsUpOrphanAndReleasesReservation() {
        gateway.failAttach = true;
        assertThrows(LiveProviderException.class, this::start); assertEquals(List.of("live_1"), gateway.hangups);
        gateway.failAttach = false; assertNotNull(start());
    }
    @Test void commandsRequireAcknowledgementsAndGracefulCloseCollectsFinalization() {
        var session = start();
        service.mute("ABCDE", agent, session.handle(), true);
        assertEquals("session.input_audio.mute", gateway.sent.getFirst().get("type").getAsString());
        var result = service.close("ABCDE", agent, session.handle()).orElseThrow();
        assertTrue(result.finalized()); assertEquals("closed", result.state()); assertTrue(gateway.hangups.isEmpty());
        assertEquals("closed", service.status("ABCDE", agent, session.handle()).orElseThrow().state());
        gateway.ack = false; session = start();
        UUID handle = session.handle();
        assertThrows(LiveProviderException.class, () -> service.mute("ABCDE", agent, handle, false));
        result = service.close("ABCDE", agent, handle).orElseThrow();
        assertFalse(result.finalized()); assertEquals(1, gateway.hangups.size());
    }
    @Test void expiryDisconnectAndDiagnosticBoundsDoNotRetainContent() {
        var session = start();
        for (int i = 0; i < 80; i++) {
            JsonObject event = event("session.input_transcript.delta"); event.addProperty("delta", "private-sentinel");
            event.add("client_event_id", com.google.gson.JsonNull.INSTANCE); gateway.receiver.accept(event);
        }
        JsonObject audio = event("session.input_audio.append"); audio.addProperty("audio", "6APoAw=="); gateway.receiver.accept(audio);
        var status = service.status("ABCDE", agent, session.handle()).orElseThrow();
        assertEquals(64, status.recent().size()); assertEquals(16, status.dropped()); assertEquals(2, status.voicedInputSamples());
        assertFalse(status.toString().contains("private-sentinel"));
        when(clock.instant()).thenReturn(Instant.parse("2026-09-28T00:00:00Z")); service.expire();
        assertEquals(1, gateway.hangups.size()); assertEquals("session_expired", service.status("ABCDE", agent, session.handle()).orElseThrow().reason());
        start(); gateway.disconnected.run(); service.expire(); assertEquals(2, gateway.hangups.size());
    }
    @Test void absentBrowserExpiresQuietSessionAndRevocationFencesCommands() {
        var session = start();
        when(clock.instant()).thenReturn(Instant.parse("2026-09-27T00:00:31Z")); service.expire();
        var result = service.status("ABCDE", agent, session.handle()).orElseThrow();
        assertEquals("client_heartbeat_expired", result.reason()); assertFalse(result.finalized());
        assertTrue(service.status("FGHIJ", agent, session.handle()).isEmpty());
        var next = start(); ownership.revoke(agent);
        assertThrows(LiveProviderException.class, () -> service.mute("ABCDE", agent, next.handle(), false));
        service.expire(); assertEquals("speech_scope_revoked", service.status("ABCDE", agent, next.handle()).orElseThrow().reason());
        when(clock.instant()).thenReturn(Instant.parse("2026-09-27T00:03:00Z")); service.expire();
        assertTrue(service.status("ABCDE", agent, next.handle()).isEmpty());
    }
    static JsonObject event(String type) { JsonObject event = new JsonObject(); event.addProperty("type", type); return event; }
    static class FakeGateway implements LiveSessionGateway {
        int created; boolean failAttach, ack = true;
        final List<String> hangups = new ArrayList<>(); final List<JsonObject> sent = new ArrayList<>();
        Consumer<JsonObject> receiver; Runnable disconnected;
        public Session create(Request request) { return new Session("live_" + ++created, "v=0 answer"); }
        public Connection attach(String id, Consumer<JsonObject> receiver, Runnable disconnected) {
            if (failAttach) throw new LiveProviderException("synthetic failure");
            this.receiver = receiver; this.disconnected = disconnected;
            return new Connection() {
                boolean open = true;
                public boolean isOpen() { return open; }
                public void close() { open = false; }
                public void send(JsonObject command) {
                    sent.add(command);
                    if (!ack) return;
                    String type = command.get("type").getAsString();
                    JsonObject response = event(type.equals("session.close") ? "session.closed" : type + "d");
                    if (command.has("event_id")) response.add("client_event_id", command.get("event_id"));
                    receiver.accept(response);
                }
            };
        }
        public void hangup(String id) { hangups.add(id); }
    }
}
