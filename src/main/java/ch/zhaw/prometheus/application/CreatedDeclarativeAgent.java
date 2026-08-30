package ch.zhaw.prometheus.application;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

public record CreatedDeclarativeAgent(Agent agent, Event startupEvent) {
}
