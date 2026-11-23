package ch.zhaw.prometheus.controllers.views;

import ch.zhaw.prometheus.model.event.Event;

public class ResponseView {
    private Event responseEvent;
    private boolean isActive;
    private String sceneCapture;

    public ResponseView(Event responseEvent, boolean isActive) {
        this.responseEvent = responseEvent;
        this.isActive = isActive;
    }
    public ResponseView(Event responseEvent,boolean isActive,String sceneCapture) {
        this(responseEvent,isActive);this.sceneCapture=sceneCapture;
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String getSceneCapture() { return sceneCapture; }

    public Event getResponseEvent() {
        return this.responseEvent;
    }

    public boolean isActive() {
        return this.isActive;
    }
}
