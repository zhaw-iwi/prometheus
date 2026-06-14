package ch.zhaw.prometheus.spi;

public class RealtimeCallInfo {
    private final String sdp;
    private final String model;
    private final String callId;
    private final String sidebandUrl;

    public RealtimeCallInfo(String sdp, String model, String callId, String sidebandUrl) {
        this.sdp = sdp;
        this.model = model;
        this.callId = callId;
        this.sidebandUrl = sidebandUrl;
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

    public String getSidebandUrl() {
        return this.sidebandUrl;
    }
}
