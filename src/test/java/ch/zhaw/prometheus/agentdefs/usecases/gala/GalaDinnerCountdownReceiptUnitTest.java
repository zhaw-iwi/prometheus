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
        return capture(parts, true);
    }
    private boolean capture(List<LiveSpeechPart> parts, boolean complete) {
        return capture("mixed-response", parts, complete);
    }
    private boolean capture(String segment, List<LiveSpeechPart> parts, boolean complete) {
        String words = parts.stream().map(LiveSpeechPart::text).collect(java.util.stream.Collectors.joining());
        var speech = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                new BehaviourPlan(words, null, null, null).toJsonObject().toString());
        speech.speechProvenance(new SpeechProvenance(SpeechProvenance.Origin.NATIVE, owner.sessionId(), owner.epoch(),
                segment, SpeechProvenance.Association.NONE, List.of(), complete));
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

    @Test void englishCountdownSelectsFreshReceiptsAndRequiresPlaybackBeforeCapture() {
        round(true);
        assertTrue(capture(List.of(part("ready", "Get ready. ", 1000), part("rock", "Rock,", 2000),
                part("paper", " Paper,", 3000), part("s1", " Scis", 4000), part("s2", "sors.", 4100))));
        var cue = GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10));
        assertEquals(List.of("rock", "paper", "s1", "s2"), cue.receiptIds());
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNotNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
        assertTrue(memory.containsKey(CAPTURE_ID));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void slowCountdownBridgesThreeCompleteNativeSegmentsAndKeepsEveryReceipt(boolean english) {
        round(true);
        var words = english ? List.of("Get ready. Rock", "Paper", "Scissors.") : List.of("Mach dich bereit. Schere", "Stein", "Papier.");
        assertFalse(capture("first", List.of(part("one", words.get(0), 1000)), true));
        assertFalse(capture("second", List.of(part("two", words.get(1), 2500)), true));
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNull(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(3)).segmentId());
        assertTrue(capture("third", List.of(part("three", words.get(2), 4000)), true));
        var cue = GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10));
        assertEquals("third", cue.segmentId());
        assertEquals(List.of("first", "second", "third"), cue.segmentIds());
        assertEquals(List.of("one", "two", "three"), cue.receiptIds());
        assertFalse(memory.containsKey(GalaDinnerCountdown.PENDING));
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNotNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
        assertFalse(memory.containsKey(GalaDinnerRps.SEGMENTS));
        assertNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
    }

    @Test void telemetryRetryWithRockThenPaperScissorsSurvivesStorageRehydration() {
        round(true);
        assertFalse(capture("ready", List.of(part("ready", "Ready?", 500)), true));
        assertFalse(capture("rock", List.of(part("rock", "Rock.", 1000)), true));
        var persisted = com.google.gson.JsonParser.parseString(memory.get(GalaDinnerCountdown.PENDING).toString());
        memory.put(GalaDinnerCountdown.PENDING, persisted);
        assertTrue(capture("last", List.of(part("paper", "Paper.", 2500), part("scissors", "Scissors.", 3500)), true));
        var cue = GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10));
        assertEquals(List.of("rock", "last"), cue.segmentIds());
        assertEquals(List.of("rock", "paper", "scissors"), cue.receiptIds());
    }

    @ParameterizedTest @ValueSource(strings = {"input", "incomplete", "other_speech", "long_pause", "new_round", "quoted"})
    void partialCountdownCannotCrossAnInterruptionFreshnessBoundaryOrRound(String barrier) {
        round(true);
        assertFalse(capture("first", List.of(part("rock", "Rock.", 1000)), true));
        switch (barrier) {
            case "input" -> put(memory, LAST_INPUT, BOUNDARY.plusSeconds(2).toString());
            case "incomplete" -> assertFalse(capture("uncertain", List.of(part("uncertain", "Paper.", 2000)), false));
            case "other_speech" -> assertFalse(capture("other", List.of(part("other", "Wait a moment.", 2000)), true));
            case "quoted" -> assertFalse(capture("quote", List.of(part("quote", "\"Rock\"", 2000)), true));
            case "new_round" -> { GalaDinnerRps.clear(memory); round(true); }
            default -> { /* Gap is applied to the next segment below. */ }
        }
        long at = barrier.equals("long_pause") ? 7001 : 3000;
        assertFalse(capture("last", List.of(part("rest", "Paper. Scissors.", at)), true));
        assertEquals("INTRO", text(memory, GalaDinnerRps.PHASE, ""));
        assertNull(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).segmentId());
        assertFalse(memory.containsKey(GalaDinnerCountdown.PENDING));
    }

    @Test void englishCountdownCannotBypassFreshnessCompletionOrFollowUpGuards() {
        round(true);
        assertFalse(capture(List.of(part("speech", "Ready? Rock, Paper, Scissors.", 2000)), false));
        assertFalse(capture(List.of(part("early", "Rock,", -1), part("fresh", " Paper, Scissors.", 2000))));
        for (String words : List.of("Rock, Paper.", "Rock, Scissors, Paper.", "\"Rock, Paper, Scissors\"",
                "Rock, Paper, Scissors. Show your hand.", "Rock, Paper, Scissors. Stop.",
                "Rock, Paper, Scissors. Are you ready?"))
            assertFalse(capture(List.of(part("speech", words, 2000))), words);
        assertEquals("INTRO", text(memory, GalaDinnerRps.PHASE, ""));
        assertNull(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).segmentId());
    }

    @ParameterizedTest @ValueSource(strings = {" Okay, ich schaue.", " Ich schaue auf Ihre Hand."})
    void observedPostCountdownStatusDoesNotRejectCompleteFreshSpeechOrBecomePartOfTheReceiptSelection(String tail) {
        round(true);
        assertTrue(capture(List.of(part("schere", " Schere", 1000), part("comma", ",", 1180),
                part("stein", " Stein,", 2207), part("papier", " Papier.", 3858),
                part("status", tail, 5231))));
        var cue = GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10));
        assertEquals(List.of("schere", "comma", "stein", "papier"), cue.receiptIds());
        assertFalse(memory.containsKey(CAPTURE_ID));
        assertNotNull(GalaDinnerRps.reveal(memory, owner, cue.id().toString(), BOUNDARY.plusSeconds(10)));
        assertTrue(memory.containsKey(CAPTURE_ID));
    }

    @Test void handStatusCannotMakeIncompleteSpeechOrAnActualFollowUpExecutable() {
        round(true);
        assertFalse(capture(List.of(part("speech", "Bereit? Schere, Stein, Papier. Ich schaue auf Ihre Hand.", 2000)), false));
        for (String tail : List.of(" Bist du bereit?", " Zeigen Sie Ihre Hand.", " Stopp.", " Noch nicht.", " Papier."))
            assertFalse(capture(List.of(part("speech", "Schere, Stein, Papier." + tail, 2000))), tail);
        assertEquals("INTRO", text(memory, GalaDinnerRps.PHASE, ""));
        assertNull(GalaDinnerRps.cue(memory, BOUNDARY.plusSeconds(10)).segmentId());
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
