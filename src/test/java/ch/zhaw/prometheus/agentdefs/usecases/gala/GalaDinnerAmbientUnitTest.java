package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReception;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerAmbientUnitTest {
    final Instant start = Instant.parse("2026-10-22T08:00:00Z");
    final Storage storage = new Storage();
    final EventHistory history = new EventHistory();
    final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
    PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
            .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
    GalaDinnerAmbientUnitTest() {
        put(storage, COUNT, "1"); GalaDinnerSensing.begin(storage, start);
    }
    JsonObject weather(String condition, double temperature) {
        var data = new JsonObject(); data.addProperty("location_label", "Zurich, Switzerland");
        data.addProperty("condition", condition); data.addProperty("temperature_c", temperature);
        return data;
    }
    JsonObject hand(String sign) {
        var data = new JsonObject(); data.addProperty("sign", sign); data.addProperty("confidence", .9);
        data.addProperty("source", "valerian.hand.camera"); data.addProperty("stabilityFrames", 3);
        return data;
    }
    void send(String type, JsonObject data, int observed, int received) {
        data.addProperty("observed_at", start.plusSeconds(observed).toString());
        data.addProperty("ts", start.plusSeconds(received).toString());
        put(storage, SEEN, start.plusSeconds(received).toString());
        history.appendEvent(Event.observation(type, "sensor", data.toString()));
        GalaDinnerSensing.ambient(storage, history, runtime, start.plusSeconds(received));
    }
    void delivered() { GalaDinnerSensing.cancelAmbient(storage); }

    @Test void weatherUsesItsOwnSourceExpiryAndRepeatsOrSmallFluctuationsStayQuiet() {
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), -570, 26);
        assertEquals(start.plusSeconds(30), time(storage, CONTINUATION_EXPIRES));
        assertEquals("PROACTIVE", text(storage, CONTINUATION_OCCASION, ""));
        assertEquals("true", text(storage, CONTINUATION_AMBIENT, ""));
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("Zurich, Switzerland"));
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("do not assume the venue"));
        delivered();
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), -570, 60);
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9.5), 60, 60);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        send(Event.TYPE_WEATHER_CURRENT, weather("rain", 9.5), 61, 61);
        assertTrue(storage.containsKey(CONTINUATION_ID));
        assertEquals(start.plusSeconds(69), time(storage, CONTINUATION_EXPIRES));
        verifyNoInteractions(gateway);
    }

    @Test void locationAndMeaningfulTemperatureChangesCanCommentButFetchTimeCannotRefreshEvidence() {
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), 26, 26); delivered();
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 11), 52, 52);
        assertTrue(storage.containsKey(CONTINUATION_ID)); delivered();
        var otherPlace = weather("clear", 11); otherPlace.addProperty("location_label", "Bern");
        send(Event.TYPE_WEATHER_CURRENT, otherPlace, 78, 78);
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("Bern")); delivered();
        send(Event.TYPE_WEATHER_CURRENT, weather("storm", 20), -600, 110);
        send(Event.TYPE_WEATHER_CURRENT, weather("storm", 20), 111, 110);
        send(Event.TYPE_WEATHER_CURRENT, weather("storm", 20), 77, 110);
        assertFalse(storage.containsKey(CONTINUATION_ID));
    }

    @Test void stableHandCommentHasNoGameEffectAndHeldSignsNeverRepeatAcrossCooldowns() {
        send(Event.TYPE_HAND_SIGN, hand("paper"), 26, 26);
        assertTrue(text(storage, CONTINUATION_FACTS, "").contains("no game invitation/start/result"));
        assertEquals("RECEPTION", mode(storage)); assertFalse(storage.containsKey(SIGN));
        assertFalse(storage.containsKey(REPLY)); delivered();
        send(Event.TYPE_HAND_SIGN, hand("paper"), 60, 60);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        send(Event.TYPE_HAND_SIGN, hand("scissor"), 61, 61);
        assertTrue(storage.containsKey(CONTINUATION_ID)); delivered();
        var oldRound = hand("rock"); oldRound.addProperty("captureId", "closed-round");
        send(Event.TYPE_HAND_SIGN, oldRound, 90, 90);
        send(Event.TYPE_HAND_SIGN, hand("rock"), 91, 91);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        verifyNoInteractions(gateway);
    }

    @Test void badSamplesCannotCreateCommentsAndNewUnreliableEvidenceWithdrawsPendingHandComment() {
        for (String patch : List.of("{\"confidence\":0.69}", "{\"stabilityFrames\":2}",
                "{\"sign\":\"unknown\"}", "{\"confidence\":1.1}")) {
            var data = hand("paper"); JsonParser.parseString(patch).getAsJsonObject().entrySet()
                    .forEach(entry -> data.add(entry.getKey(), entry.getValue()));
            send(Event.TYPE_HAND_SIGN, data, 26 + history.toList().size(), 26 + history.toList().size());
            assertFalse(storage.containsKey(CONTINUATION_ID));
        }
        send(Event.TYPE_HAND_SIGN, hand("paper"), 35, 35);
        assertTrue(storage.containsKey(CONTINUATION_ID));
        var uncertain = hand("rock"); uncertain.addProperty("confidence", .3);
        send(Event.TYPE_HAND_SIGN, uncertain, 36, 36);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        var badWeather = weather("rain", 9); badWeather.remove("location_label");
        send(Event.TYPE_WEATHER_CURRENT, badWeather, 62, 62);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        var undated = weather("clear", 10); undated.addProperty("observed_at", "invalid");
        undated.addProperty("ts", start.plusSeconds(63).toString());
        history.appendEvent(Event.observation(Event.TYPE_WEATHER_CURRENT, "sensor", undated.toString()));
        GalaDinnerSensing.ambient(storage, history, runtime, start.plusSeconds(63));
        assertFalse(storage.containsKey(CONTINUATION_ID));
    }

    @ParameterizedTest @ValueSource(strings = {"TASK", "GUESS", "RPS", "quiet", "recent_user", "pending_result", "absent", "closed", "no_live"})
    void blockedSamplesAreConsumedWithoutReplayOrMutationOfTheActivity(String block) {
        switch (block) {
            case "TASK", "GUESS", "RPS" -> put(storage, MODE, block);
            case "quiet" -> put(storage, QUIET, "true");
            case "recent_user" -> put(storage, LAST_INPUT, start.plusSeconds(25).toString());
            case "pending_result" -> { put(storage, OUTCOME, "keep"); put(storage, OUTCOME_EXPIRES, start.plusSeconds(90).toString()); }
            case "absent" -> put(storage, COUNT, "0");
            case "closed" -> runtime = runtime.withTaskContinuation(() -> false);
            case "no_live" -> runtime = runtime.withExternalSpeech(null);
        }
        String originalMode = mode(storage);
        put(storage, PERSON, "Marie Curie"); put(storage, SIGN, "rock");
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), 26, 26);
        send(Event.TYPE_HAND_SIGN, hand("paper"), 27, 27);
        assertFalse(storage.containsKey(CONTINUATION_ID)); assertEquals(originalMode, mode(storage));
        assertEquals("Marie Curie", text(storage, PERSON, "")); assertEquals("rock", text(storage, SIGN, ""));
        put(storage, MODE, "RECEPTION"); put(storage, COUNT, "1");
        remove(storage, QUIET); remove(storage, LAST_INPUT); remove(storage, OUTCOME);
        runtime = runtime.withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID())).withTaskContinuation(() -> true);
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), 60, 60);
        send(Event.TYPE_HAND_SIGN, hand("paper"), 61, 61);
        assertFalse(storage.containsKey(CONTINUATION_ID)); verifyNoInteractions(gateway);
    }

    @Test void allAmbientSourcesShareCooldownAndSupersededFactsAreNeverQueued() {
        send(Event.TYPE_WEATHER_CURRENT, weather("clear", 9), 26, 26);
        send(Event.TYPE_WEATHER_CURRENT, weather("rain", 9), 27, 27);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        send(Event.TYPE_HAND_SIGN, hand("paper"), 30, 30);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        send(Event.TYPE_HAND_SIGN, hand("paper"), 60, 60);
        assertFalse(storage.containsKey(CONTINUATION_ID));
        send(Event.TYPE_HAND_SIGN, hand("rock"), 61, 61);
        assertTrue(storage.containsKey(CONTINUATION_ID));
        var id = text(storage, CONTINUATION_ID, "");
        send(Event.TYPE_WEATHER_CURRENT, weather("snow", 0), 62, 62);
        assertEquals(id, text(storage, CONTINUATION_ID, ""));
    }

    @Test void receptionGraphPublishesLiveOccasionWithoutSpeechInferenceOrStateChange() {
        var agent = new GalaDinnerReception().createAgent();
        var memory = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
        Instant now = Instant.now();
        put(memory, COUNT, "1"); put(memory, SEEN, now.toString());
        GalaDinnerSensing.begin(memory, now.minusSeconds(30));
        var data = weather("clear", 9); data.addProperty("observed_at", now.minusSeconds(100).toString());
        assertNull(agent.acknowledge(Event.observation(Event.TYPE_WEATHER_CURRENT, "sensor", data.toString()), runtime));
        assertEquals("Gala reception", agent.getCurrentState().getName()); assertNull(agent.generate(runtime));
        var context = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).liveActivityContext();
        assertNotNull(context.continuation()); assertTrue(context.continuation().interruptible());
        assertEquals(LiveActivityContext.Occasion.PROACTIVE, context.continuation().occasion());
        verifyNoInteractions(gateway);
    }

    @Test void aNewEncounterDoesNotDeduplicateAgainstThePreviousVisitorsHand() {
        send(Event.TYPE_HAND_SIGN, hand("paper"), 26, 26);
        endEncounter(storage, history.toList().size());
        GalaDinnerSensing.begin(storage, start.plusSeconds(30));
        send(Event.TYPE_HAND_SIGN, hand("paper"), 60, 60);
        assertTrue(storage.containsKey(CONTINUATION_ID));
    }
}
