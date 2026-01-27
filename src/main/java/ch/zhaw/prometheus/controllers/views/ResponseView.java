package ch.zhaw.prometheus.controllers.views;

import ch.zhaw.prometheus.model.event.Event;

public class ResponseView {
    private Event responseEvent;
    private boolean isActive;

    public ResponseView(Event responseEvent, boolean isActive) {
        this.responseEvent = responseEvent;
        this.isActive = isActive;
    }

    public Event getResponseEvent() {
        return this.responseEvent;
    }

    public boolean isActive() {
        return this.isActive;
    }
}
