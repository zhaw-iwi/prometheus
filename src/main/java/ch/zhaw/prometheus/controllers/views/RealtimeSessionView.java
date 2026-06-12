package ch.zhaw.prometheus.controllers.views;

public class RealtimeSessionView {
    private String clientSecret;
    private String model;
    private String realtimeCallsUrl;

    public RealtimeSessionView(String clientSecret, String model, String realtimeCallsUrl) {
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
