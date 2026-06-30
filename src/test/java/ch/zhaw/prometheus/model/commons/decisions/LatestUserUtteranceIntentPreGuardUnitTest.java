package ch.zhaw.prometheus.model.commons.decisions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.commons.decisions.LatestUserUtteranceIntentPreGuard.Mode;

class LatestUserUtteranceIntentPreGuardUnitTest {

    @Test
    void sessionClosureGuardAllowsExplicitConversationStop() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                "Please stop, I am done."));

        assertTrue(new LatestUserUtteranceIntentPreGuard(Mode.SESSION_CLOSURE).decide(history, null));
    }

    @Test
    void sessionClosureGuardRejectsOrdinaryQuestion() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                "What therapy is this for?"));

        assertFalse(new LatestUserUtteranceIntentPreGuard(Mode.SESSION_CLOSURE).decide(history, null));
    }

    @Test
    void taskOutcomeGuardRequiresClosingContextForShortYes() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Is it something from your room?\"}"));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "yes"));

        assertFalse(new LatestUserUtteranceIntentPreGuard(Mode.TASK_OUTCOME).decide(history, null));
    }

    @Test
    void taskOutcomeGuardAllowsShortYesAfterClosingQuestion() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"Nice, I guessed it. Shall we leave it there?\"}"));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "yes"));

        assertTrue(new LatestUserUtteranceIntentPreGuard(Mode.TASK_OUTCOME).decide(history, null));
    }
}
