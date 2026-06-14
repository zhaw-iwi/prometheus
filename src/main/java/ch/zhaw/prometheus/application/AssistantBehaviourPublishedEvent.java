package ch.zhaw.prometheus.application;

import java.util.UUID;

import ch.zhaw.prometheus.model.event.Event;

public record AssistantBehaviourPublishedEvent(UUID agentId, Event event) {
}
