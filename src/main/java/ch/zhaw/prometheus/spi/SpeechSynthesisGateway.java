package ch.zhaw.prometheus.spi;

public interface SpeechSynthesisGateway {
    SpeechAudio synthesize(String text, String voice, double speed);

    default SpeechAudio synthesize(String text, String voice, double speed, SpeechAudioFormat format) {
        if (format != SpeechAudioFormat.MP3) {
            throw new SpeechSynthesisException("speech provider does not support the requested format");
        }
        return synthesize(text, voice, speed);
    }
}
