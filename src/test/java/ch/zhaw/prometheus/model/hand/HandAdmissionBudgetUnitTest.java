package ch.zhaw.prometheus.model.hand;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import ch.zhaw.prometheus.model.event.Event;
import fixtures.behaviour.HandSequenceFixture;

class HandAdmissionBudgetUnitTest {
    @Test void retriesAreIdempotentButChangedAndStaleCapturesAreRejected() {
        Instant now=Instant.now(); var event=HandSequenceFixture.event("capture",1,false,now);
        assertTrue(HandObservationAdmission.freshRepresentation(event,List.of(),now));
        assertFalse(HandObservationAdmission.freshRepresentation(event,List.of(event),now));
        var changed=HandSequenceFixture.event("capture",2,false,now);
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.freshRepresentation(changed,List.of(event),now));
        assertThrows(IllegalArgumentException.class,()->HandObservationAdmission.freshRepresentation(event,List.of(),now.plusSeconds(16)));
        var companion=Event.observation(Event.TYPE_HAND_SIGNS,"camera",fixtures.behaviour.NamedHandSignFixture.observation("capture","fist","right",now).toString());
        assertTrue(HandObservationAdmission.freshRepresentation(companion,List.of(event),now));
    }
    @Test void budgetCountsUtf8AndCurrentContractsWithoutHistoricalJournal() {
        var values=new HashMap<String,JsonElement>();values.put("hand.scene",new JsonPrimitive("a".repeat(65000)));
        assertDoesNotThrow(()->HandStateBudget.require(values));
        values.put("coordination.outcomes",new JsonPrimitive("a".repeat(100000)));
        assertDoesNotThrow(()->HandStateBudget.require(values));
        values.put("coordination.draft",new JsonPrimitive("界".repeat(200)));
        assertTrue(HandStateBudget.bytes(values)>65536);
        assertThrows(IllegalArgumentException.class,()->HandStateBudget.require(values));
    }
}
