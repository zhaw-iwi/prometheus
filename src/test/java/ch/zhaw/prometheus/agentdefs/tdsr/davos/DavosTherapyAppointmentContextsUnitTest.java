package ch.zhaw.prometheus.agentdefs.tdsr.davos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Storage;

class DavosTherapyAppointmentContextsUnitTest {

    @Test
    void exposesThreeTherapyContextsForUniformSelection() {
        List<String> types = DavosTherapyAppointmentContexts.all().stream()
                .map(DavosTherapyAppointmentContexts.TherapyAppointmentContext::type)
                .toList();

        assertEquals(List.of(
                "physiotherapy",
                "occupational_therapy",
                "activation"), types);
    }

    @Test
    void preselectUsesRandomIndexAndStoresSelectedContext() {
        Storage storage = new Storage();

        DavosTherapyAppointmentContexts.preselect(storage, new FixedIndexRandom(2));

        assertTrue(storage.containsKey(DavosTherapyAppointmentContexts.STORAGE_KEY));
        assertEquals("activation",
                storage.get(DavosTherapyAppointmentContexts.STORAGE_KEY).getAsJsonObject().get("type").getAsString());
        assertEquals("activation",
                storage.get(DavosTherapyAppointmentContexts.STORAGE_KEY).getAsJsonObject().get("label").getAsString());
    }

    @Test
    void preselectDoesNotReplaceExistingContext() {
        Storage storage = new Storage();
        storage.put(DavosTherapyAppointmentContexts.STORAGE_KEY, new JsonPrimitive("already_selected"));

        DavosTherapyAppointmentContexts.preselect(storage, new FixedIndexRandom(1));

        assertEquals("already_selected", storage.get(DavosTherapyAppointmentContexts.STORAGE_KEY).getAsString());
    }

    @Test
    void selectionCanReachEveryContextWithEqualIndexChoices() {
        AtomicInteger index = new AtomicInteger();
        RandomGenerator random = new RandomGenerator() {
            @Override
            public int nextInt(int bound) {
                return index.getAndIncrement() % bound;
            }

            @Override
            public long nextLong() {
                return 0L;
            }
        };

        List<String> selected = java.util.stream.IntStream.range(0, 3)
                .mapToObj(ignored -> DavosTherapyAppointmentContexts.select(random).type())
                .toList();

        assertEquals(List.of(
                "physiotherapy",
                "occupational_therapy",
                "activation"), selected);
    }

    private static final class FixedIndexRandom implements RandomGenerator {
        private final int index;

        private FixedIndexRandom(int index) {
            this.index = index;
        }

        @Override
        public int nextInt(int bound) {
            return this.index;
        }

        @Override
        public long nextLong() {
            return 0L;
        }
    }
}
