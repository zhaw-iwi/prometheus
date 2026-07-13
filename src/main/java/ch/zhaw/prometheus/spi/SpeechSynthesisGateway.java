package ch.zhaw.prometheus.spi;

public interface SpeechSynthesisGateway {
    SpeechAudio synthesize(String text, String voice, double speed);
}
