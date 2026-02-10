package ch.zhaw.prometheus.controllers.views;

public class EventRequest {
    private String type;
    private String actor;
    private String kind;
    private String payload;

    public EventRequest() {
    }

    public EventRequest(String type, String actor, String kind, String payload) {
        this.type = type;
        this.actor = actor;
        this.kind = kind;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
