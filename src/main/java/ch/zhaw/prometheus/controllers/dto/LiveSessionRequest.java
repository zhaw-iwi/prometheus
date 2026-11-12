package ch.zhaw.prometheus.controllers.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record LiveSessionRequest(String sdp, String voice, boolean speechApproval) {
    public LiveSessionRequest(String sdp, String voice) { this(sdp, voice, false); }
    @JsonAnySetter public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unsupported Live session setting");
    }
    @Override public String toString() { return "LiveSessionRequest[voice=" + voice + ",sdp=redacted]"; }
}
