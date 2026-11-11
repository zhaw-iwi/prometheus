package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.SpeechProvenance;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.LiveSpeechPart;

class GalaDinnerCountdownReceiptUnitTest {
    private static final Instant BOUNDARY = Instant.parse("2026-11-05T14:08:43.962Z");
    private final Storage memory = new Storage();
    private final ExternalSpeech owner = new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID());

    private void round(boolean preparation) {
        if (preparation) GalaDinnerRpsPreparation.enable(memory);
        put(memory, MODE, "RPS"); put(memory, COUNT, "1"); put(memory, SEEN, BOUNDARY.toString());
        put(memory, GalaDinnerRps.PHASE, "INTRO"); put(memory, GalaDinnerRps.INTRO, UUID.randomUUID().toString());
        put(memory, GalaDinnerRps.INTRO_AT, BOUNDARY.toString()); put(memory, LAST_INPUT, BOUNDARY.toString());
        put(memory, GalaDinnerRps.DEADLINE, BOUNDARY.plusSeconds(30).toString());
        put(memory, GalaDinnerRps.SESSION_ID, owner.sessionId().toString());
        put(memory, GalaDinnerRps.EPOCH, owner.epoch().toString()); put(memory, SIGN, "rock");
    }
    private LiveSpeechPart part(String id, String text, long millis) {
        return new LiveSpeechPart(text, BOUNDARY.plusMillis(millis), id);
    }
    private boolean capture(List<LiveSpeechPart> parts) {
        String words = parts.stream().map(LiveSpeechPart::text).collect(java.util.stream.Collectors.joining());
        var speech = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                new BehaviourPlan(words, null, null, null).toJsonObject().toString());
        speech.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                "mixed-response", SpeechProvenance.Association.NONE, List.of(), true));
        return GalaDinnerRps.captured(memory, speech, owner, parts.getFirst().receivedAt(), BOUNDARY.plusSeconds(9), parts);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void mixedResponseSelectsOnlyTheFreshCountdownReceiptsAndClearsThemAfterPlayback(boolean preparation) {
        round(preparation);
        assertTrue(capture(List.of(part("discarded-prefix", "Infos zum Abend. ", -5000),
                part("fresh-intro", "Und jetzt zuerst das Spiel: ", 1000),
                part("first", preparation ? "Schere," : "Eins,", 2000),
                part("second", preparation ? " Stein," : " zwei,", 2400),
                part("third", preparation ? " Papier" : " drei", 2800), part("punctuation", ".", 3000))));
        var cue = GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10));
        assertEquals("mixed-response", cue.segmentId());
        assertEquals(List.of("first", "second", "third"), cue.receiptIds());
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNotNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
        assertTrue(memory.containsKey(CAPTURE_ID)); assertFalse(memory.containsKey(GalaDinnerRps.RECEIPTS));
        assertNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
    }

    @Test void wordBoundariesCanSpanMultipleProviderFragments() {
        round(true);
        assertTrue(capture(List.of(part("prefix", "Ein kurzer Countdown: ", 1000),
                part("s1", "Sche", 2000), part("s2", "re,", 2100), part("s3", " Stein,", 2500),
                part("s4", " Pa", 2800), part("s5", "pier.", 2900))));
        assertEquals(List.of("s1", "s2", "s3", "s4", "s5"), GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).receiptIds());
    }

    @Test void anEarlyFirstWordStillCannotAuthorizeTheCountdown() {
        round(true);
        assertFalse(capture(List.of(part("too-early", "Schere,", -209), part("second", " Stein,", 434),
                part("third", " Papier.", 1450))));
        assertEquals("INTRO", text(memory, GalaDinnerRps.PHASE, ""));
        assertNull(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).segmentId());
        assertFalse(memory.containsKey(GalaDinnerRps.RECEIPTS));
    }

    @Test void olderRecordingCallersKeepWholeSegmentEvidence() {
        round(true);
        assertTrue(capture(List.of(new LiveSpeechPart("Schere, Stein, Papier.", BOUNDARY.plusSeconds(2)))));
        assertTrue(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).receiptIds().isEmpty());
    }

    @Test void oversizedSelectionsKeepTheBoundedWholeSegmentFallback() {
        round(true);
        var parts = new ArrayList<LiveSpeechPart>(); parts.add(part("first", "Schere", 1000));
        for (int i = 0; i < 31; i++) parts.add(part("separator-" + i, ",", 1500 + i));
        parts.add(part("last", " Stein, Papier.", 2000));
        assertTrue(capture(parts));
        assertTrue(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).receiptIds().isEmpty());
        GalaDinnerRps.clear(memory); assertFalse(memory.containsKey(GalaDinnerRps.RECEIPTS));
    }
}
