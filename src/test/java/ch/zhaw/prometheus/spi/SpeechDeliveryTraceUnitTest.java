package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.logging.SpeechDeliveryTrace;

class SpeechDeliveryTraceUnitTest {
    @Test void separatesProviderReadWriteAndFlushWaitsWithoutChangingAudio() throws Exception {
        AtomicLong clock = new AtomicLong();
        SpeechDeliveryTrace trace = new SpeechDeliveryTrace(clock::get);
        InputStream provider = new ByteArrayInputStream(new byte[] {1, 2, 3, 4}) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                clock.addAndGet(30_000_000); return super.read(bytes, offset, Math.min(2, length));
            }
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public synchronized void write(byte[] bytes, int offset, int length) {
                clock.addAndGet(5_000_000); super.write(bytes, offset, length);
            }
            @Override public void flush() { clock.addAndGet(10_000_000); }
        };
        clock.addAndGet(7_000_000); // Async dispatch delay is visible before the first read.
        SpeechAudio.streaming(provider, "audio/pcm", -1).writeTo(output, trace);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, output.toByteArray());
        var snapshot = trace.snapshot();
        assertEquals("complete", snapshot.status()); assertNull(snapshot.phase());
        assertEquals(4, snapshot.bytesRead()); assertEquals(4, snapshot.bytesFlushed());
        assertEquals(7, snapshot.steps().getFirst().startedMs());
        for (var step : snapshot.steps()) {
            double expected = switch (step.phase()) { case "read" -> 30; case "write" -> 5; case "flush" -> 10; default -> 0; };
            assertEquals(expected, step.completedMs() - step.startedMs());
        }
        assertEquals(127, snapshot.finishedMs());
    }

    @Test void failedFlushKeepsReadBytesAndFailurePhaseButNoExceptionContent() throws Exception {
        SpeechDeliveryTrace trace = new SpeechDeliveryTrace();
        final boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(new byte[] {1, 2}) {
            @Override public void close() { closed[0] = true; }
        };
        OutputStream output = new ByteArrayOutputStream() {
            @Override public void flush() throws IOException { throw new IOException("private-provider-data"); }
        };
        assertThrows(IOException.class, () -> SpeechAudio.streaming(input, "audio/pcm", -1).writeTo(output, trace));
        var snapshot = trace.snapshot();
        assertTrue(closed[0]); assertEquals("error", snapshot.status()); assertEquals("flush", snapshot.phase());
        assertNotNull(snapshot.phaseStartedMs()); assertEquals(2, snapshot.bytesRead()); assertEquals(0, snapshot.bytesFlushed());
        assertFalse(new com.google.gson.Gson().toJson(snapshot).contains("private"));
    }

    @Test void boundedSnapshotsPreserveStartupAndTailAndExposePendingReads() {
        SpeechDeliveryTrace trace = new SpeechDeliveryTrace();
        for (int index = 0; index < 300; index++) { trace.begin("read"); trace.end(1, index + 1); }
        var snapshot = trace.snapshot();
        assertEquals(256, snapshot.steps().size()); assertEquals(44, snapshot.dropped());
        assertEquals(1, snapshot.steps().getFirst().sequence()); assertEquals(128, snapshot.steps().get(127).sequence());
        assertEquals(300, snapshot.steps().getLast().sequence());
        trace.begin("read");
        assertEquals("read", trace.snapshot().phase()); assertNull(trace.snapshot().finishedMs());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.steps().clear());
    }
}
