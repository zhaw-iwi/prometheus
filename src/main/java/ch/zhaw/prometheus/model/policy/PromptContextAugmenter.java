package ch.zhaw.prometheus.model.policy;

import java.util.List;

import ch.zhaw.prometheus.model.event.EventHistory;

public interface PromptContextAugmenter {
    List<PromptMessage> augment(EventHistory eventHistory);
}

