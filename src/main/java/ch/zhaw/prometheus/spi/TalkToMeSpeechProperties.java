package ch.zhaw.prometheus.spi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "prometheus.talktome.speech")
public class TalkToMeSpeechProperties {
    private String model = "gpt-4o-mini-tts";
    private String url = "https://api.openai.com/v1/audio/speech";

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
