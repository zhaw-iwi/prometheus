package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

class SpeechAudioUnitTest {
    @Test
    void closesUpstreamStreamWhenDownstreamWriteFails() {
        TrackingInputStream input = new TrackingInputStream(new byte[] { 1, 2, 3 });
        SpeechAudio audio = SpeechAudio.streaming(input, "audio/mpeg", -1L);
        OutputStream disconnected = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("client disconnected");
            }

            @Override
            public void write(byte[] values, int offset, int length) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        assertThrows(IOException.class, () -> audio.writeTo(disconnected));
        assertTrue(input.closed);
    }

    @Test
    void guardsMetadataAndOneShotConsumption() {
        SpeechAudio audio = new SpeechAudio(new byte[] { 4, 5 }, "text/plain\r\nInjected: true");
        assertEquals("audio/mpeg", audio.getContentType());
        assertEquals(2L, audio.getContentLength());
        assertEquals(2, audio.getContent().length);
        assertThrows(IllegalStateException.class, audio::getContent);
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            super.close();
        }
    }
}
