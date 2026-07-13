package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class TalkToMePolicyUnitTest {
    private final TalkToMePolicy policy = new TalkToMePolicy();
    private final State state = new State("Talk to Me", this.policy, java.util.List.of());
    private final PromptMessageAssembler assembler = new PromptMessageAssembler();
    private final LanguageModelGateway languageModelGateway = mock(LanguageModelGateway.class);

    @Test
    void emitsLatestUserTextExactlyWithoutLanguageModelGeneration() {
        EventHistory events = new EventHistory();
        String text = "Gr\u00fcezi, \"Z\u00fcrich\"!\nLine two \ud83c\udf0d";
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, text));

        BehaviourPlan plan = this.policy.onStart(this.state, events, this.assembler, this.languageModelGateway);

        assertEquals(text, plan.getSpeech());
        verifyNoInteractions(this.languageModelGateway);
    }

    @Test
    void emitsNothingBeforeTheFirstSubmission() {
        assertNull(this.policy.onStart(this.state, new EventHistory(), this.assembler, this.languageModelGateway));
        verifyNoInteractions(this.languageModelGateway);
    }

    @Test
    void rejectsBlankAndOverLimitText() {
        EventHistory blank = historyWith("  \n");
        EventHistory overLimit = historyWith("x".repeat(TalkToMePolicy.MAX_TEXT_CODE_POINTS + 1));

        assertNull(this.policy.onRespond(this.state, blank, this.assembler, this.languageModelGateway));
        assertNull(this.policy.onRespond(this.state, overLimit, this.assembler, this.languageModelGateway));
        verifyNoInteractions(this.languageModelGateway);
    }

    @Test
    void countsUnicodeCodePointsRatherThanUtf16Units() {
        String text = "\ud83c\udf0d".repeat(TalkToMePolicy.MAX_TEXT_CODE_POINTS);

        BehaviourPlan plan = this.policy.onRespond(this.state, historyWith(text), this.assembler,
                this.languageModelGateway);

        assertEquals(text, plan.getSpeech());
    }

    private static EventHistory historyWith(String text) {
        EventHistory events = new EventHistory();
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, text));
        return events;
    }
}
