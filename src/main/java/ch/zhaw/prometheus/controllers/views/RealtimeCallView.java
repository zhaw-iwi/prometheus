package ch.zhaw.prometheus.controllers.views;

public class RealtimeCallView {
    private final String sdp;
    private final String model;
    private final String callId;

    public RealtimeCallView(String sdp, String model, String callId) {
        this.sdp = sdp;
        this.model = model;
        this.callId = callId;
    }

    public String getSdp() {
        return this.sdp;
    }

    public String getModel() {
        return this.model;
    }

    public String getCallId() {
        return this.callId;
    }
}
