package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivePlaybackCueUnitTest {
    private final UUID id = UUID.randomUUID();
    private final Instant expires = Instant.parse("2026-11-05T14:09:13Z");

    @Test void receiptSelectionIsOptionalAndImmutable() {
        assertTrue(new LivePlaybackCue(id, null, expires).receiptIds().isEmpty());
        var source = new ArrayList<>(List.of("one", "two"));
        var cue = new LivePlaybackCue(id, "speech", expires, source); source.clear();
        assertEquals(List.of("one", "two"), cue.receiptIds());
        assertThrows(UnsupportedOperationException.class, () -> cue.receiptIds().clear());
    }

    @Test void invalidSelectionsCannotAuthorizePlayback() {
        assertThrows(IllegalArgumentException.class, () -> new LivePlaybackCue(id, null, expires, List.of("one")));
        for (var invalid : List.of(List.of("one", "one"), List.of(""), List.of("bad receipt"),
                List.of("x".repeat(201)), java.util.stream.IntStream.range(0, 33).mapToObj(i -> "id-" + i).toList()))
            assertThrows(IllegalArgumentException.class, () -> new LivePlaybackCue(id, "speech", expires, invalid));
    }

    @Test void multipleSegmentsAreBoundedOrderedImmutableAndRequireReceiptSelection() {
        var source = new ArrayList<>(List.of("rock", "last"));
        var cue = new LivePlaybackCue(id, "last", expires, List.of("one", "two"), true, source);
        source.clear();
        assertEquals(List.of("rock", "last"), cue.segmentIds());
        assertThrows(UnsupportedOperationException.class, () -> cue.segmentIds().clear());
        assertEquals(List.of("speech"), new LivePlaybackCue(id, "speech", expires).segmentIds());
        for (var segments : List.of(List.<String>of(), List.of("last", "last"), List.of("last", "wrong"),
                List.of("one", "two", "three", "last"), List.of("bad id", "last")))
            assertThrows(IllegalArgumentException.class,
                    () -> new LivePlaybackCue(id, "last", expires, List.of("one"), true, segments));
        assertThrows(IllegalArgumentException.class,
                () -> new LivePlaybackCue(id, "last", expires, List.of(), true, List.of("first", "last")));
    }
}
