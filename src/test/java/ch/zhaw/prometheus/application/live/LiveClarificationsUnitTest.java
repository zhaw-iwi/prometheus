package ch.zhaw.prometheus.application.live;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.application.LiveTranscriptIngressService.Outcome;

class LiveClarificationsUnitTest {
    private final List<String> state = List.of("choice");
    private Outcome outcome(String speaker, String text, String status, long start) {
        return new Outcome(UUID.randomUUID(), speaker, text, status, "acknowledged", null, start, start + 100);
    }
    @Test void onlyALaterAdmittedRequestSupersedesAnUnresolvedChoice() {
        var queue = new LiveClarifications();
        queue.captured(outcome("USER", "ja", "CLARIFICATION", 1000), state, 0);
        var pending = queue.poll();
        for (var other : List.of(outcome("ASSISTANT", "A late recorded response", "COMPLETE", 2000),
                outcome("USER", "Personenraten bitte", "INCOMPLETE", 3000),
                outcome("USER", "Eine ältere Frage", "COMPLETE", 0),
                outcome("USER", "Okay", "COMPLETE", 4000))) {
            queue.captured(other, state, 100);
            assertTrue(queue.valid(pending, state, 100));
        }
        queue.captured(outcome("USER", "Personenraten bitte", "COMPLETE", 5000), state, 100);
        assertFalse(queue.valid(pending, state, 100));
        // Reordered notifications cannot restore obsolete recovery.
        queue.captured(outcome("USER", "Eine ältere Frage", "COMPLETE", 0), state, 100);
        assertFalse(queue.valid(pending, state, 100));
    }
    @Test void sourceStateDeadlineAndDuplicateIdentityBoundDelivery() {
        var queue = new LiveClarifications(); var input = outcome("USER", "ja", "CLARIFICATION", 0);
        queue.captured(input, state, 0); queue.captured(input, state, 1);
        assertEquals(1, queue.size()); var pending = queue.poll();
        assertTrue(queue.valid(pending, state, 9999));
        assertFalse(queue.valid(pending, state, 10000));
        assertFalse(queue.valid(pending, List.of("another activity"), 1));
        queue.captured(input, state, 2); assertEquals(0, queue.size());
        for (int i = 0; i < 32; i++) assertTrue(queue.captured(outcome("USER", "ja", "CLARIFICATION", i), state, 0));
        assertFalse(queue.captured(outcome("USER", "ja", "CLARIFICATION", 33), state, 0));
        queue.clear(); assertEquals(0, queue.size());
    }
}
