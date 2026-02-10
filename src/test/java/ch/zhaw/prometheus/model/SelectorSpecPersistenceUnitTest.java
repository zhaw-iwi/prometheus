package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest
class SelectorSpecPersistenceUnitTest {

    @Autowired
    private AgentRepository repository;

    @Test
    void selectorSpecsArePreservedAcrossSaveAndReload() {
        Storage storage = new Storage();
        StaticDecision decision = new StaticDecision("Return true.");
        decision.setEventSelectorSpec(EventSelectorSpec.actor(Event.ACTOR_ASSISTANT));

        StaticExtractionAction action = new StaticExtractionAction("Extract summary.", storage, "summary");
        action.setEventSelectorSpec(EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE));

        State finalState = new Final("final");
        Transition transition = new Transition(List.of(decision), List.of(action), finalState);
        State start = new State("start", new NoOpPolicy(), List.of(transition));
        start.setEventSelectorSpec(EventSelectorSpec.any());

        Agent saved = this.repository.save(new Agent("selector-spec-agent", "test", start, storage));
        Agent loaded = this.repository.findById(saved.getId()).orElseThrow();

        State loadedStart = loaded.getCurrentState();
        assertNotNull(loadedStart);
        assertEquals(EventSelectorSpec.Kind.ANY, loadedStart.getEventSelectorSpec().getKind());

        Transition loadedTransition = loadedStart.getTransitions().get(0);
        Decision loadedDecision = loadedTransition.getDecisions().get(0);
        Action loadedAction = loadedTransition.getActions().get(0);

        assertEquals(EventSelectorSpec.Kind.ACTOR, loadedDecision.getEventSelectorSpec().getKind());
        assertEquals(List.of(Event.ACTOR_ASSISTANT), loadedDecision.getEventSelectorSpec().getValues());
        assertTrue(loadedDecision.getEventSelector()
                .test(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "ok")));
        assertFalse(
                loadedDecision.getEventSelector().test(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                        "no")));

        assertEquals(EventSelectorSpec.Kind.TYPE, loadedAction.getEventSelectorSpec().getKind());
        assertEquals(List.of(Event.TYPE_USER_UTTERANCE), loadedAction.getEventSelectorSpec().getValues());
        assertTrue(loadedAction.getEventSelector()
                .test(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "u")));
        assertFalse(loadedAction.getEventSelector()
                .test(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "a")));
    }
}
