package ch.zhaw.prometheus.model.task;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;

class TaskMemoryPromptUnitTest {
    @Test void unheardDraftsCannotEnterTaskDialogueOrDisplaceTheHeardQuestion() {
        var history = new EventHistory();
        append(history, "Legacy spoken reply", null);
        append(history, "What has keys but cannot open a door?", nativeSpeech(true, true));
        append(history, "Legacy native playback without approval", nativeSpeech(true, null));
        var owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());
        append(history, "Existing backend task intent", SpeechProvenance.intent(owner));
        for (int i = 0; i < 25; i++) append(history, "Unheard space clue " + i, nativeSpeech(i % 2 == 0, false));
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "My answer is a piano."));
        var before = history.toList().stream().map(Event::getPayload).toList();

        var dialogue = TaskMemory.messages(history, "Instructions", new PromptMessageAssembler(), Instant.now()).stream()
                .filter(message -> !message.getRole().equals("system")).toList();
        assertEquals(List.of("Legacy spoken reply", "What has keys but cannot open a door?",
                "Legacy native playback without approval", "Existing backend task intent", "My answer is a piano."),
                dialogue.stream().map(PromptMessage::getContent).toList());
        assertEquals(List.of("assistant", "assistant", "assistant", "assistant", "user"),
                dialogue.stream().map(PromptMessage::getRole).toList());
        assertEquals(before, history.toList().stream().map(Event::getPayload).toList());
        assertEquals(25, history.toList().stream().filter(ConversationProjection::isUnplayed).count());
    }

    private static SpeechProvenance nativeSpeech(boolean complete, Boolean played) {
        return new SpeechProvenance(SpeechProvenance.Origin.NATIVE, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString(), SpeechProvenance.Association.NONE, List.of(), complete, played);
    }
    private static void append(EventHistory history, String text, SpeechProvenance source) {
        var event = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, BehaviourPlan.speechOnly(text).toJson());
        event.speechProvenance(source); history.appendEvent(event);
    }
}
