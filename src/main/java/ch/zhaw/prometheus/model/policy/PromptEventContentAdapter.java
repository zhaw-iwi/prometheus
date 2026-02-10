package ch.zhaw.prometheus.model.policy;

import ch.zhaw.prometheus.model.event.Event;

public interface PromptEventContentAdapter {
    boolean supports(Event event);

    String toPromptContent(Event event);
}

