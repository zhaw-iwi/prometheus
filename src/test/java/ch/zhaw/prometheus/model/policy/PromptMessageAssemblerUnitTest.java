package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

class PromptMessageAssemblerUnitTest {
    private final PromptMessageAssembler assembler = new PromptMessageAssembler();

    @Test
    void mapsRolesForSystemUserAndAssistantEvents() {
        assertEquals("system", assembler.mapRole(Event.systemPrompt("system prompt")));
        assertEquals("user", assembler.mapRole(
                Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello")));
        assertEquals("assistant", assembler.mapRole(
                Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"hi\"}")));
    }

    @Test
    void serializesBehaviourPlanAndFaceEmotionForPromptContent() {
        EventHistory history = new EventHistory();
        history.appendEvent(
                Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{\"speech\":\"hi there\"}"));
        history.appendEvent(Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"happy\",\"confidence\":0.83,\"valence\":0.7,\"arousal\":0.4,\"ts\":\"2026-02-10T16:00:00Z\"}"));

        List<PromptMessage> messages = assembler.compose(history, "be helpful");

        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("be helpful", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("hi there", messages.get(1).getContent());
        assertEquals("user", messages.get(2).getRole());
        assertEquals("User facial emotion: happy (confidence 0.83)", messages.get(2).getContent());
        assertEquals("system", messages.get(3).getRole());
        org.junit.jupiter.api.Assertions.assertTrue(messages.get(3).getContent().contains("Nonverbal summary"));
    }

    @Test
    void composeCondensedRejectsEmptyHistory() {
        assertThrows(RuntimeException.class,
                () -> assembler.composeCondensed(new EventHistory(), "system"));
    }

    @Test
    void composeRejectsNullSystemPrompt() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello"));

        assertThrows(NullPointerException.class, () -> assembler.compose(history, null));
    }

    @Test
    void composeCondensedRejectsNullAppendPrompt() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "hello"));

        assertThrows(NullPointerException.class,
                () -> assembler.composeCondensed(history, "system", null));
    }

    @Test
    void roleMappingUsesDeterministicPrecedence() {
        Event conflictingSystem = Event.response("custom.response", Event.ACTOR_ASSISTANT, "text");
        Event conflictingAssistant = Event.observation("custom.observation", Event.ACTOR_USER, "text");
        Event explicitSystem = Event.response(Event.TYPE_SYSTEM_PROMPT, Event.ACTOR_ASSISTANT, "text");

        assertEquals("assistant", assembler.mapRole(conflictingSystem));
        assertEquals("user", assembler.mapRole(conflictingAssistant));
        assertEquals("system", assembler.mapRole(explicitSystem));
    }
}

