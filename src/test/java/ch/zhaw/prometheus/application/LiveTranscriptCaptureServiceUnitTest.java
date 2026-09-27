package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.application.live.LivePcmActivity;
import ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Segment;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

class LiveTranscriptCaptureServiceUnitTest {
    @Test void sidebandReceiptsAreOrderedOffThreadAndCommittedOnceAfterMeasuredQuiet() throws Exception {
        var ingress = mock(LiveTranscriptIngressService.class); var events = mock(ApplicationEventPublisher.class);
        var clock = mock(Clock.class); var now = new AtomicLong(1000); when(clock.millis()).thenAnswer(call -> now.get());
        when(ingress.commit(any(), any(), any(), any())).thenAnswer(call -> {
            Segment segment = call.getArgument(2);
            return Optional.of(new LiveTranscriptIngressService.Outcome(segment.id(), "USER", segment.text(), "COMPLETE", "acknowledged", UUID.randomUUID(), 0L, 500L));
        });
        var service = new LiveTranscriptCaptureService(ingress, events, clock);
        try {
            var failure = new AtomicReference<String>();
            var capture = service.open(UUID.randomUUID(), new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()), () -> List.of("game"), failure::set);
            capture.inputMuted(false);
            var fragment = JsonParser.parseString("{\"type\":\"session.input_transcript.delta\",\"event_id\":\"one\",\"start_ms\":0,\"end_ms\":500,\"delta\":\"Hello\"}").getAsJsonObject();
            capture.receive(fragment); capture.receive(fragment);
            now.set(1800);
            var audio = new com.google.gson.JsonObject(); audio.addProperty("type", "session.input_audio.append");
            audio.addProperty("audio", Base64.getEncoder().encodeToString(new byte[38400])); capture.receive(audio);
            service.poll(); capture.close().get(3, TimeUnit.SECONDS);
            assertNull(failure.get());
            var order = inOrder(ingress, events);
            order.verify(ingress).receipt(any(), any(), any()); order.verify(ingress).commit(any(), any(), any(), eq(List.of("game")));
            order.verify(events).publishEvent(any(LiveTranscriptCaptureService.Committed.class));
            verify(ingress, times(1)).receipt(any(), any(), any());
        } finally { service.shutdown(); }
    }
    @Test void pcmGateMeasuresQuietTailWithoutRetainingOrAveragingAwayShortSpeech() {
        byte[] pcm = new byte[2880]; // 20 ms voiced, then 40 ms quiet.
        for (int i = 0; i < 480; i++) { pcm[2 * i] = (byte) 0xE8; pcm[2 * i + 1] = 3; }
        var activity = LivePcmActivity.measure(pcm);
        assertTrue(activity.voiced()); assertEquals(1440, activity.samples()); assertEquals(960, activity.trailingQuietSamples());
        assertThrows(IllegalArgumentException.class, () -> LivePcmActivity.measure(new byte[3]));
    }
}
