package ch.zhaw.prometheus.spi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechAudio implements AutoCloseable {
    private final InputStream content;
    private final String contentType;
    private final long contentLength;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    public SpeechAudio(byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("speech audio must not be empty");
        }
        byte[] copy = content.clone();
        this.content = new ByteArrayInputStream(copy);
        this.contentType = normalizeContentType(contentType);
        this.contentLength = copy.length;
    }

    private SpeechAudio(InputStream content, String contentType, long contentLength) {
        if (content == null) {
            throw new IllegalArgumentException("speech audio stream must not be null");
        }
        this.content = content;
        this.contentType = normalizeContentType(contentType);
        this.contentLength = contentLength >= 0 ? contentLength : -1L;
    }

    public static SpeechAudio streaming(InputStream content, String contentType, long contentLength) {
        return new SpeechAudio(content, contentType, contentLength);
    }

    public byte[] getContent() {
        try (InputStream stream = this.openStream()) {
            return stream.readAllBytes();
        } catch (IOException failure) {
            throw new SpeechSynthesisException("unable to read Speech synthesis response", failure);
        }
    }

    public void writeTo(OutputStream output) throws IOException {
        if (output == null) {
            throw new IllegalArgumentException("speech audio output must not be null");
        }
        try (InputStream stream = this.openStream()) {
            stream.transferTo(output);
        }
    }

    public String getContentType() {
        return this.contentType;
    }

    public long getContentLength() {
        return this.contentLength;
    }

    @Override
    public void close() throws IOException {
        this.consumed.set(true);
        this.content.close();
    }

    private InputStream openStream() {
        if (!this.consumed.compareAndSet(false, true)) {
            throw new IllegalStateException("speech audio stream has already been consumed");
        }
        return this.content;
    }

    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank() || raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0) {
            return "audio/mpeg";
        }
        String normalized = raw.trim();
        return normalized.toLowerCase(Locale.ROOT).startsWith("audio/") ? normalized : "audio/mpeg";
    }
}
