package ch.zhaw.prometheus.spi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import com.google.gson.JsonObject;

@Configuration
@PropertySources({
        @PropertySource(value = "classpath:openai.properties", ignoreResourceNotFound = true),
        @PropertySource(value = "classpath:/openai-${spring.profiles.active}.properties", ignoreResourceNotFound = true)
})
@ConfigurationProperties(prefix = "openai")
public class OpenAIProperties {

    private static final String OPENAI = "openai";
    private static final String AZUREOPENAI = "azureopenai";

    private String openaivsazureopenai;
    private String url;
    private String model;
    private String key;
    private String realtimeModel;
    private String realtimeInputTranscriptionModel;
    private String realtimeTranscriptionModel;
    private String realtimeTranscriptionLanguage;
    private String realtimeTranscriptionDelay;
    private Long realtimeTranscriptBatchDelayMs;
    private String realtimeClientSecretUrl;
    private String realtimeCallsUrl;
    private String realtimeSafetyIdentifier;

    public String getOpenaivsazureopenai() {
        return this.openaivsazureopenai;
    }

    public void setOpenaivsazureopenai(String openaivsazureopenai) {
        this.openaivsazureopenai = openaivsazureopenai;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getModel() {
        return this.model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRealtimeModel() {
        return this.realtimeModel;
    }

    public void setRealtimeModel(String realtimeModel) {
        this.realtimeModel = realtimeModel;
    }

    public String getRealtimeInputTranscriptionModel() {
        return this.realtimeInputTranscriptionModel;
    }

    public void setRealtimeInputTranscriptionModel(String realtimeInputTranscriptionModel) {
        this.realtimeInputTranscriptionModel = realtimeInputTranscriptionModel;
    }

    public String getRealtimeTranscriptionModel() {
        return this.realtimeTranscriptionModel;
    }

    public void setRealtimeTranscriptionModel(String realtimeTranscriptionModel) {
        this.realtimeTranscriptionModel = realtimeTranscriptionModel;
    }

    public String getRealtimeTranscriptionLanguage() {
        return this.realtimeTranscriptionLanguage;
    }

    public void setRealtimeTranscriptionLanguage(String realtimeTranscriptionLanguage) {
        this.realtimeTranscriptionLanguage = realtimeTranscriptionLanguage;
    }

    public String getRealtimeTranscriptionDelay() {
        return this.realtimeTranscriptionDelay;
    }

    public void setRealtimeTranscriptionDelay(String realtimeTranscriptionDelay) {
        this.realtimeTranscriptionDelay = realtimeTranscriptionDelay;
    }

    public Long getRealtimeTranscriptBatchDelayMs() {
        return this.realtimeTranscriptBatchDelayMs;
    }

    public void setRealtimeTranscriptBatchDelayMs(Long realtimeTranscriptBatchDelayMs) {
        this.realtimeTranscriptBatchDelayMs = realtimeTranscriptBatchDelayMs;
    }

    public String getRealtimeClientSecretUrl() {
        return this.realtimeClientSecretUrl;
    }

    public void setRealtimeClientSecretUrl(String realtimeClientSecretUrl) {
        this.realtimeClientSecretUrl = realtimeClientSecretUrl;
    }

    public String getRealtimeCallsUrl() {
        return this.realtimeCallsUrl;
    }

    public void setRealtimeCallsUrl(String realtimeCallsUrl) {
        this.realtimeCallsUrl = realtimeCallsUrl;
    }

    public String getRealtimeSafetyIdentifier() {
        return this.realtimeSafetyIdentifier;
    }

    public void setRealtimeSafetyIdentifier(String realtimeSafetyIdentifier) {
        this.realtimeSafetyIdentifier = realtimeSafetyIdentifier;
    }

    public String headerKeyNameForAPIKey() {
        if (OpenAIProperties.OPENAI.equals(this.getOpenaivsazureopenai())) {
            return "Authorization";
        }
        if (OpenAIProperties.AZUREOPENAI.equals(this.getOpenaivsazureopenai())) {
            return "api-key";
        }
        throw new RuntimeException(
                "unexpected value for property openaivsazureopenai: " + this.getOpenaivsazureopenai());
    }

    public String getKey() {
        if (OpenAIProperties.OPENAI.equals(this.getOpenaivsazureopenai())) {
            return "Bearer " + this.key;
        }
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public JsonObject payload() {
        JsonObject result = new JsonObject();
        if (OpenAIProperties.OPENAI.equals(this.getOpenaivsazureopenai())) {
            result.addProperty("model", this.getModel());
        }
        return result;
    }
}

