package ch.zhaw.prometheus.application.live;

/** Coarse 20 ms RMS activity gate. An experimental acoustic cue, not a provider turn or echo detector. */
public record LivePcmActivity(int samples, int trailingQuietSamples, boolean voiced, int voicedSamples, int peakRms) {
    public static LivePcmActivity measure(byte[] pcm) {
        if (pcm.length % 2 != 0 || pcm.length > 200000) throw new IllegalArgumentException("Invalid reflected PCM");
        int samples = pcm.length / 2, quiet = 0, voicedSamples = 0, peak = 0; boolean voiced = false;
        for (int start = 0; start < samples; start += 480) {
            int count = Math.min(480, samples - start); long energy = 0;
            for (int i = start; i < start + count; i++) {
                int value = (short) ((pcm[i * 2] & 255) | (pcm[i * 2 + 1] << 8)); energy += (long) value * value;
            }
            double rms = Math.sqrt((double) energy / count); peak = Math.max(peak, (int) rms);
            if (rms > 500) { quiet = 0; voiced = true; voicedSamples += count; }
            else quiet += count;
        }
        return new LivePcmActivity(samples, quiet, voiced, voicedSamples, peak);
    }
}
