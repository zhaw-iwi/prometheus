package ch.zhaw.statefulconversation.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VerbalEventSmokeTest {

    @Test
    void userUtteranceProducesVerbalHistory() {
        State state = new State("", "verbalSmoke", "", List.of());
        Agent agent = new Agent("Verbal Agent", "Verifies verbal events", state);

        Event userEvent = Event.userUtterance("Hello there", state.getName());
        agent.acknowledge(userEvent);
        agent.appendAssistantResponse("Hi, how can I help?");

        List<Event> conversation = agent.getConversation();
        assertEquals(2, conversation.size());

        Event first = conversation.get(0);
        assertEquals(Event.TYPE_USER_UTTERANCE, first.getType());
        assertEquals("user", first.getRole());
        assertEquals("Hello there", first.getContent());

        Event second = conversation.get(1);
        assertEquals(Event.TYPE_ASSISTANT_UTTERANCE, second.getType());
        assertEquals("assistant", second.getRole());
        assertEquals("Hi, how can I help?", second.getContent());

        List<Event> verbalEvents = state.getEventHistory()
                .filter(EventFilter.type(Event.TYPE_USER_UTTERANCE, Event.TYPE_ASSISTANT_UTTERANCE));
        assertEquals(2, verbalEvents.size());

        assertNotNull(agent.getCurrentState());
        assertTrue(agent.isActive());
        assertFalse(agent.getConversation().isEmpty());
    }
}
