package ch.zhaw.prometheus.spi;

public class RealtimeSessionInfo {
    private final String clientSecret;
    private final String model;
    private final String realtimeCallsUrl;

    public RealtimeSessionInfo(String clientSecret, String model, String realtimeCallsUrl) {
        this.clientSecret = clientSecret;
        this.model = model;
        this.realtimeCallsUrl = realtimeCallsUrl;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getModel() {
        return model;
    }

    public String getRealtimeCallsUrl() {
        return realtimeCallsUrl;
    }
}
