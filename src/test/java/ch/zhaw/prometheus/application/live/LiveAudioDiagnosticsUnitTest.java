package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LiveAudioDiagnosticsUnitTest {
    @Test void onlyStructuralFragmentsOverContinuousMeasuredSilenceAreRejected() {
        var d = new LiveAudioDiagnostics(); String fragment = "t: {\"";
        assertFalse(d.silentStructuralFragment(fragment, 0));
        for (int at = 200; at <= 5000; at += 200) d.observe(true, new byte[9600], at);
        assertTrue(d.silentStructuralFragment(fragment, 5000));
        assertFalse(d.silentStructuralFragment("My answer is a piano", 5000));
        assertFalse(d.silentStructuralFragment("A", 5000));
        assertFalse(d.silentStructuralFragment(fragment, 5501));
        d.observe(true, new byte[9600], 6000); // A gap is not measured silence.
        assertFalse(d.silentStructuralFragment(fragment, 6000));
        for (int at = 6200; at <= 11000; at += 200) d.observe(true, new byte[9600], at);
        assertTrue(d.silentStructuralFragment(fragment, 11000));
        byte[] voice = new byte[9600]; java.util.Arrays.fill(voice, (byte)64);
        d.observe(true, voice, 11200);
        assertFalse(d.silentStructuralFragment(fragment, 11200)); // Spoken literals remain eligible.
    }
    @Test void separatesInputOutputActivityAndRetainsBoundedWindowsWithoutAudio() {
        var diagnostics = new LiveAudioDiagnostics();
        byte[] voiced = new byte[960];
        for (int i = 0; i < voiced.length; i += 2) { voiced[i] = (byte) 0xe8; voiced[i + 1] = 3; }
        diagnostics.observe(true, voiced, 10);
        diagnostics.observe(false, new byte[960], 30);
        var window = diagnostics.status().recent().getFirst();
        assertEquals(480, window.inputSamples()); assertEquals(480, window.outputSamples());
        assertEquals(480, window.voicedInputSamples()); assertEquals(0, window.voicedOutputSamples());
        assertEquals(1000, window.inputPeakRms()); assertEquals(0, window.outputPeakRms());
        for (int i = 1; i <= 140; i++) diagnostics.observe(false, voiced, 10 + 1000L * i);
        assertEquals(129, diagnostics.status().recent().size());
        assertEquals(12, diagnostics.status().dropped());
        assertEquals(480, diagnostics.status().recent().getLast().voicedOutputSamples());
        assertThrows(IllegalArgumentException.class, () -> diagnostics.observe(false, new byte[3], 999999));
    }
}
