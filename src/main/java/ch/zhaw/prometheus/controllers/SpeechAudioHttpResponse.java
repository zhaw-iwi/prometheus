package ch.zhaw.prometheus.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import ch.zhaw.prometheus.spi.SpeechAudio;

final class SpeechAudioHttpResponse {
    private SpeechAudioHttpResponse() {
    }

    static ResponseEntity<StreamingResponseBody> stream(SpeechAudio audio) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(audio.getContentType()));
        if (audio.getContentLength() >= 0) {
            response.contentLength(audio.getContentLength());
        }
        return response.body(audio::writeTo);
    }
}
