package ch.zhaw.prometheus.spi.live;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Opt-in provider configuration. Credentials remain in OpenAIProperties. */
@Component
@ConfigurationProperties(prefix = "prometheus.live")
public class LiveProperties {
    private boolean enabled;
    private String sessionsUrl = "https://api.openai.com/v1/live/sessions";
    private String model = "gpt-live-1";
    private int requestTimeoutMs = 15000;
    private int closeTimeoutMs = 3000;
    private int sessionLifetimeSeconds = 900;
    private int capacity = 16;
    private int clientIdleSeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getSessionsUrl() { return sessionsUrl; }
    public void setSessionsUrl(String value) { sessionsUrl = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int value) { requestTimeoutMs = bounded(value, 100, 60000); }
    public int getCloseTimeoutMs() { return closeTimeoutMs; }
    public void setCloseTimeoutMs(int value) { closeTimeoutMs = bounded(value, 100, 15000); }
    public int getSessionLifetimeSeconds() { return sessionLifetimeSeconds; }
    public void setSessionLifetimeSeconds(int value) { sessionLifetimeSeconds = bounded(value, 30, 3600); }
    public int getCapacity() { return capacity; }
    public int getClientIdleSeconds() { return clientIdleSeconds; }
    public void setClientIdleSeconds(int value) { clientIdleSeconds = bounded(value, 10, 120); }
    public void setCapacity(int value) { capacity = bounded(value, 1, 128); }

    private static int bounded(int value, int min, int max) {
        if (value < min || value > max) throw new IllegalArgumentException("Live setting outside supported bounds");
        return value;
    }
}
