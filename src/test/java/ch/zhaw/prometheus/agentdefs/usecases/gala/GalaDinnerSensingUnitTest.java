package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerSensingUnitTest {
    final Instant start = Instant.parse("2026-10-22T08:00:00Z");
    final Storage storage = new Storage();
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    GalaDinnerSensingUnitTest() {
        put(storage, COUNT, "1"); put(storage, SEEN, start.toString()); GalaDinnerSensing.begin(storage, start);
    }
    void group(int count, int seconds) { group(count, seconds, seconds); }
    void group(int count, int observed, int received) {
        reply(storage, null); var history = new EventHistory();
        history.appendEvent(Event.observation(Event.TYPE_SOCIAL_GROUPING, "camera",
                "{\"humanCount\":" + count + ",\"ts\":\"" + start.plusSeconds(observed) + "\"}"));
        GalaDinnerSensing.presence(storage, history, runtime, start.plusSeconds(received));
    }
    void face(String label, int seconds, String extra) {
        reply(storage, null); put(storage, SEEN, start.plusSeconds(seconds).toString());
        var event = Event.observation(Event.TYPE_FACE_EMOTION, "camera", "{\"emotion\":\"" + label
                + "\",\"confidence\":0.99,\"ts\":\"" + start.plusSeconds(seconds) + "\"" + extra + "}");
        GalaDinnerSensing.face(storage, event, runtime, start.plusSeconds(seconds));
    }
    @Test void stableCountChangeProducesOneShortOccasionButInitialAndRepeatedCountsDoNot() {
        group(1, 1); group(1, 3); assertFalse(storage.containsKey(CONTINUATION_ID));
        group(1, 24);
        group(2, 26); assertFalse(storage.containsKey(CONTINUATION_ID)); group(2, 28);
        String id = text(storage, CONTINUATION_ID, ""); assertFalse(id.isBlank());
        assertEquals("PROACTIVE", text(storage, CONTINUATION_OCCASION, ""));
        assertEquals("true", text(storage, CONTINUATION_AMBIENT, ""));
        assertEquals(start.plusSeconds(36), time(storage, CONTINUATION_EXPIRES));
        group(2, 29); group(4, 28, 30); group(4, 40, 30);
        assertEquals("2", text(storage, COUNT, "")); assertEquals(id, text(storage, CONTINUATION_ID, ""));
        group(2, 42); group(2, 52); group(1, 54); group(1, 56); assertNotEquals(id, text(storage, CONTINUATION_ID, ""));
        group(0, 57); assertFalse(storage.containsKey(CONTINUATION_ID)); assertEquals("0", text(storage, COUNT, ""));
        verifyNoInteractions(gateway);
    }
    @Test void rapidCountAndFaceOscillationDoesNotBecomeSpeech() {
        for (int i = 26; i < 36; i++) group(i % 2 + 1, i);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        face("neutral", 40, ""); face("neutral", 42, "");
        for (int i = 43; i < 53; i++) face(i % 2 == 0 ? "happy" : "sad", i, "");
        assertFalse(storage.containsKey(CONTINUATION_ID)); verifyNoInteractions(gateway);
    }
    @Test void aNewUnstableCountWithdrawsAnOldGroupCommentWithoutImmediatelyReplacingIt() {
        group(1, 24);
        group(2, 26); group(2, 28); assertTrue(storage.containsKey(CONTINUATION_ID));
        group(3, 29); assertFalse(storage.containsKey(CONTINUATION_ID));
        group(3, 31); assertFalse(storage.containsKey(CONTINUATION_ID));
    }
    @Test void stableExpressionChangeIsTentativeAndAbsenceWithdrawsIt() {
        face("neutral", 26, ""); face("neutral", 28, ""); assertFalse(storage.containsKey(CONTINUATION_ID));
        face("happy", 29, ""); face("happy", 31, "");
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("uncertain camera estimate"));
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("neutral to happy"));
        face("happy", 32, ",\"facePresent\":false");
        assertFalse(storage.containsKey(CONTINUATION_ID)); assertFalse(storage.containsKey(FACE));
        face("happy", 33, ",\"faceDetectionConfidence\":0.2"); assertFalse(storage.containsKey(FACE));
        verifyNoInteractions(gateway);
    }
    @ParameterizedTest
    @ValueSource(strings = {"TASK", "GUESS", "RPS", "QUIET", "recent_user", "pending_result", "pending_reveal", "closed"})
    void conversationAndActivityPriorityConsumeChangesWithoutACommentBacklog(String block) {
        group(1, 24);
        switch (block) {
            case "TASK", "GUESS", "RPS" -> put(storage, MODE, block);
            case "QUIET" -> put(storage, QUIET, "true");
            case "recent_user" -> put(storage, LAST_INPUT, start.plusSeconds(25).toString());
            case "pending_result" -> { put(storage, CONTINUATION_ID, "existing"); put(storage, CONTINUATION_EXPIRES, start.plusSeconds(40).toString()); }
            case "pending_reveal" -> { put(storage, OUTCOME, "existing"); put(storage, OUTCOME_EXPIRES, start.plusSeconds(40).toString()); }
            case "closed" -> runtime = runtime.withTaskContinuation(() -> false);
        }
        group(2, 26); group(2, 28); assertFalse(storage.containsKey(CONTINUATION_AMBIENT));
        remove(storage, QUIET); remove(storage, LAST_INPUT); remove(storage, CONTINUATION_ID); remove(storage, OUTCOME);
        put(storage, MODE, "RECEPTION"); runtime = runtime.withTaskContinuation(() -> true);
        group(2, 60); group(2, 62); assertFalse(storage.containsKey(CONTINUATION_ID)); verifyNoInteractions(gateway);
    }
    @Test void correctionPersistsAcrossActivitiesUntilTheEncounterEnds() {
        face("neutral", 26, ""); face("neutral", 28, ""); GalaDinnerSensing.corrected(storage);
        reception(storage); face("neutral", 29, ""); face("neutral", 31, ""); face("sad", 32, ""); face("sad", 34, "");
        assertFalse(storage.containsKey(CONTINUATION_ID)); assertEquals("true", text(storage, GalaDinnerSensing.CORRECTED, ""));
        group(0, 35); assertTrue(storage.containsKey(GalaDinnerSensing.CORRECTED));
        group(0, 37); assertTrue(storage.containsKey(GalaDinnerSensing.CORRECTED));
        endEncounter(storage, 1); assertFalse(storage.containsKey(GalaDinnerSensing.CORRECTED));
    }
    @Test void anAmbientOccasionCannotOutliveItsSourceAndSensingGapsResetTheBaseline() {
        group(1, 24); group(2, 26); group(2, 28, 40);
        assertEquals(start.plusSeconds(43), time(storage, CONTINUATION_EXPIRES));
        group(3, 60); group(3, 62); assertFalse(storage.containsKey(CONTINUATION_ID));
    }
    @Test void unchangedSensingRetainsFactVersionsButRefreshesEvidenceAndStillDetectsChanges() {
        group(1, 1); group(1, 3); face("neutral", 3, ""); face("neutral", 5, "");
        var keys = java.util.List.of(COUNT, GalaDinnerSensing.GROUP_BASE, GalaDinnerSensing.GROUP_CANDIDATE,
                FACE, GalaDinnerSensing.FACE_BASE, GalaDinnerSensing.FACE_CANDIDATE);
        var versions = new java.util.HashMap<String, String>();
        keys.forEach(key -> versions.put(key, storage.writeVersion(key)));
        String seen = storage.writeVersion(SEEN), faceAt = storage.writeVersion(FACE_AT);
        group(1, 7); face("neutral", 7, "");
        keys.forEach(key -> assertEquals(versions.get(key), storage.writeVersion(key), key));
        assertNotEquals(seen, storage.writeVersion(SEEN)); assertNotEquals(faceAt, storage.writeVersion(FACE_AT));
        assertTrue(present(storage, start.plusSeconds(21))); assertFalse(present(storage, start.plusSeconds(22)));
        group(1, 20); group(2, 26); group(2, 28);
        assertNotEquals(versions.get(COUNT), storage.writeVersion(COUNT));
        assertTrue(storage.containsKey(CONTINUATION_ID));
        group(0, 29); assertFalse(present(storage, start.plusSeconds(29)));
        assertFalse(storage.containsKey(CONTINUATION_ID));
    }
    @Test void repeatedReceptionDoesNotRewriteModeButEachNewRequestHasItsOwnIdentity() {
        reception(storage); String mode = storage.writeVersion(MODE);
        reception(storage); assertEquals(mode, storage.writeVersion(MODE));
        continueRequest(storage, "Same facts"); String first = text(storage, CONTINUATION_ID, "");
        String factsVersion = storage.writeVersion(CONTINUATION_FACTS);
        continueRequest(storage, "Same facts");
        assertNotEquals(first, text(storage, CONTINUATION_ID, ""));
        assertEquals(factsVersion, storage.writeVersion(CONTINUATION_FACTS));
    }

    @ParameterizedTest @ValueSource(strings = {"GUESS", "RPS", "TASK"})
    void dropoutPreservesActivityButConfirmedDepartureClearsItOnce(String mode) {
        put(storage, MODE, mode); put(storage, PERSON, "Marie Curie"); put(storage, SIGN, "rock");
        put(storage, "task.actions", "2"); put(storage, "unrelated", "keep");
        group(0, 1); String deadline = text(storage, ABSENT_SINCE, "");
        assertEquals(mode, mode(storage)); assertFalse(present(storage, start.plusSeconds(1)));
        assertTrue(conversationPresent(storage, start.plusSeconds(2)));
        group(0, 2); assertEquals(deadline, text(storage, ABSENT_SINCE, ""));
        group(1, 2, 2); // Equal source time is rejected, even if positive.
        assertEquals("0", text(storage, COUNT, ""));
        assertFalse(departureDue(storage, start.plusMillis(2999)));
        assertTrue(departureDue(storage, start.plusSeconds(3)));
        group(0, 3); assertEquals(mode, mode(storage));
        endEncounter(storage, 1); assertEquals("RECEPTION", mode(storage));
        assertFalse(storage.containsKey(PERSON)); assertFalse(storage.containsKey(SIGN));
        assertFalse(storage.containsKey("task.actions")); assertFalse(storage.containsKey(ABSENT_SINCE));
        String sessionVersion = storage.writeVersion(SESSION);
        group(0, 4); assertEquals(sessionVersion, storage.writeVersion(SESSION));
        assertEquals("keep", text(storage, "unrelated", "")); verifyNoInteractions(gateway);
    }

    @Test void freshReturnCancelsAbsenceWithoutGreetingReinitializationOrCueReplay() {
        put(storage, MODE, "GUESS"); put(storage, PERSON, "Marie Curie"); put(storage, SESSION, "7");
        group(0, 1); group(1, 2);
        assertEquals("GUESS", mode(storage)); assertEquals("Marie Curie", text(storage, PERSON, ""));
        assertEquals("7", text(storage, SESSION, "")); assertFalse(storage.containsKey(ABSENT_SINCE));
        group(0, 1, 3); assertEquals("1", text(storage, COUNT, ""));
        group(1, 4); assertFalse(storage.containsKey(CONTINUATION_ID)); verifyNoInteractions(gateway);
    }
}
