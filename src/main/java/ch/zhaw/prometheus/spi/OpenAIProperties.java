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
    private String liveTranscriptionClientSecretUrl;
    private String liveTranscriptionWebRtcUrl;
    private String liveTranscriptionSafetyIdentifier;

    private String reasoningEffort;
    private int requestTimeoutMs = 30000;
    private Integer maxCompletionTokens;
    private java.util.Map<InferencePurpose, InferenceRoute> routes = new java.util.EnumMap<>(InferencePurpose.class);

    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String value) { reasoningEffort = value; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int value) { requestTimeoutMs = value; }
    public Integer getMaxCompletionTokens() { return maxCompletionTokens; }
    public void setMaxCompletionTokens(Integer value) { maxCompletionTokens = value; }
    public java.util.Map<InferencePurpose, InferenceRoute> getRoutes() { return routes; }
    public void setRoutes(java.util.Map<InferencePurpose, InferenceRoute> value) {
        routes = value == null ? new java.util.EnumMap<>(InferencePurpose.class) : value;
    }

    public static class InferenceRoute {
        private String model;
        private String reasoningEffort;
        private String url;
        private Integer timeoutMs;
        private Integer maxCompletionTokens;
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public String getReasoningEffort() { return reasoningEffort; }
        public void setReasoningEffort(String value) { reasoningEffort = value; }
        public String getUrl() { return url; }
        public void setUrl(String value) { url = value; }
        public Integer getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(Integer value) { timeoutMs = value; }
        public Integer getMaxCompletionTokens() { return maxCompletionTokens; }
        public void setMaxCompletionTokens(Integer value) { maxCompletionTokens = value; }
    }

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

    public String getLiveTranscriptionClientSecretUrl() {
        return this.liveTranscriptionClientSecretUrl;
    }

    public void setLiveTranscriptionClientSecretUrl(String liveTranscriptionClientSecretUrl) {
        this.liveTranscriptionClientSecretUrl = liveTranscriptionClientSecretUrl;
    }

    public String getLiveTranscriptionWebRtcUrl() {
        return this.liveTranscriptionWebRtcUrl;
    }

    public void setLiveTranscriptionWebRtcUrl(String liveTranscriptionWebRtcUrl) {
        this.liveTranscriptionWebRtcUrl = liveTranscriptionWebRtcUrl;
    }

    public String getLiveTranscriptionSafetyIdentifier() {
        return this.liveTranscriptionSafetyIdentifier;
    }

    public void setLiveTranscriptionSafetyIdentifier(String liveTranscriptionSafetyIdentifier) {
        this.liveTranscriptionSafetyIdentifier = liveTranscriptionSafetyIdentifier;
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

