package ch.zhaw.prometheus.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.logging.LatencyTrace;
import ch.zhaw.prometheus.logging.SpeechDeliveryTrace;

final class SpeechAudioHttpResponse {
    private SpeechAudioHttpResponse() {
    }

    static ResponseEntity<StreamingResponseBody> stream(SpeechAudio audio) {
        return stream(audio, null);
    }

    static ResponseEntity<StreamingResponseBody> stream(SpeechAudio audio, SpeechDeliveryTrace delivery) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(audio.getContentType()));
        if (audio.getContentLength() >= 0) {
            response.contentLength(audio.getContentLength());
        }
        String timing = LatencyTrace.responseHeader();
        if (timing != null) response.header(LatencyTrace.TIMING_HEADER, timing);
        if (delivery != null) response.header(SpeechDeliveryTrace.HEADER, delivery.id().toString());
        return response.body(output -> audio.writeTo(output, delivery));
    }
}
