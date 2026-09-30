package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.AgentEmbodiment;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.interaction.AgentCapabilityDescription;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

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
                "{\"userName\":\"Alice\",\"emotion\":\"happy\",\"confidence\":0.83,\"valence\":0.7,\"arousal\":0.4,\"ts\":\"2026-02-10T16:00:00Z\"}"));

        List<PromptMessage> messages = assembler.compose(history, "be helpful");

        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("be helpful", messages.get(0).getContent());
        assertEquals("assistant", messages.get(1).getRole());
        assertEquals("hi there", messages.get(1).getContent());
        assertEquals("user", messages.get(2).getRole());
        assertEquals("User Alice facial emotion: happy (confidence 0.83)", messages.get(2).getContent());
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

    @Test
    void robotEmbodimentUsesGigiAcrossAllSystemPromptPositions() {
        EventHistory history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER,
                "Please tell Valerian I am ready."));

        List<PromptMessage> messages = assembler.forEmbodiment(AgentEmbodiment.ROBOT)
                .composeCondensed(
                        history,
                        "You are Valerian. Introduce yourself as Valerian.",
                        "Valerian should answer now.");

        assertEquals("You are Gigi. Introduce yourself as Gigi.", messages.get(0).getContent());
        assertTrue(messages.get(1).getContent().contains("Please tell Valerian I am ready."));
        assertEquals("Gigi should answer now.", messages.get(2).getContent());
    }

    @Test
    void cockpitEmbodimentKeepsValerianPrompts() {
        List<PromptMessage> messages = assembler.forEmbodiment(AgentEmbodiment.COCKPIT)
                .compose(null, "You are GIGI.", "Introduce yourself as Valerian.");

        assertEquals("You are Valerian.", messages.get(0).getContent());
        assertEquals("Introduce yourself as Valerian.", messages.get(1).getContent());
    }

    @Test
    void capabilityAndPersonaBindingsComposeInEitherOrderWithoutChangingDialogue() {
        var profile = AgentInteractionProfile.empty().withCapabilityAwareness(true);
        var history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Tell Valerian hello."));
        for (var bound : List.of(
                assembler.forCapabilities(profile).forEmbodiment(AgentEmbodiment.ROBOT),
                assembler.forEmbodiment(AgentEmbodiment.ROBOT).forCapabilities(profile))) {
            var messages = bound.compose(history, "You are Valerian.", "Valerian should answer.");
            assertEquals("You are Gigi.", messages.getFirst().getContent());
            assertEquals("Gigi should answer.", messages.getLast().getContent());
            assertEquals(1, messages.stream().filter(m -> m.getContent().startsWith(AgentCapabilityDescription.MARKER)).count());
            assertTrue(messages.stream().anyMatch(m -> m.getContent().equals("Tell Valerian hello.")));
            // Nonverbal response generation resolves instructions directly through this method.
            assertEquals("Gigi gestures.", bound.resolveSystemPrompt("Valerian gestures."));
            var condensed = bound.composeCondensed(history, "Valerian decides.");
            assertEquals("Gigi decides.", condensed.getFirst().getContent());
            assertEquals(2, condensed.size());
            assertEquals("Valerian returns.", bound.forEmbodiment(AgentEmbodiment.COCKPIT).resolveSystemPrompt("Gigi returns."));
            assertEquals("Gigi stays.", bound.forCapabilities(profile.withCapabilityAwareness(false)).resolveSystemPrompt("Valerian stays."));
        }
        assertEquals("Valerian stays.", assembler.resolveSystemPrompt("Gigi stays."));
    }
}
