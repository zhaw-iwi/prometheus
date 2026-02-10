package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.model.event.Event;

public class DefaultPayloadPromptEventContentAdapter implements PromptEventContentAdapter {
    @Override
    public boolean supports(Event event) {
        return true;
    }

    @Override
    public String toPromptContent(Event event) {
        if (event == null || event.getPayload() == null) {
            return "";
        }
        return event.getPayload();
    }
}

