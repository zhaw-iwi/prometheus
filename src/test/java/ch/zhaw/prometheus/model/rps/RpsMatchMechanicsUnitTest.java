package ch.zhaw.prometheus.model.rps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;

class RpsMatchMechanicsUnitTest {
    @Test
    void parsesOnePositiveUnambiguousTarget() {
        assertEquals(3, RpsSetTargetWinsAction.parseTargetWins("First to 3, please").orElseThrow());
        assertEquals(7, RpsSetTargetWinsAction.parseTargetWins("seven wins").orElseThrow());
        assertEquals(21, RpsSetTargetWinsAction.parseTargetWins("twenty-one wins").orElseThrow());
        assertEquals(3, RpsSetTargetWinsAction.parseTargetWins("Three, yes, three wins").orElseThrow());
        assertEquals(5, RpsSetTargetWinsAction.parseTargetWins("5 games, first to 5").orElseThrow());
        assertEquals(2, RpsSetTargetWinsAction.parseTargetWins("Zwei Siege").orElseThrow());
        assertEquals(1, RpsSetTargetWinsAction.parseTargetWins("einen Sieg").orElseThrow());
        assertEquals(3, RpsSetTargetWinsAction.parseTargetWins("Drei, ja, drei Siege").orElseThrow());
        assertEquals(5, RpsSetTargetWinsAction.parseTargetWins("fünf Rundensiege").orElseThrow());
        assertEquals(21, RpsSetTargetWinsAction.parseTargetWins("einundzwanzig Siege").orElseThrow());
        assertEquals(2, RpsSetTargetWinsAction.parseTargetWins("to").orElseThrow());
        assertEquals(4, RpsSetTargetWinsAction.parseTargetWins("fore").orElseThrow());

        assertTrue(RpsSetTargetWinsAction.parseTargetWins("zero").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("-3").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("one hundred").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("two or three").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("zwei oder drei").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("I want to play").isEmpty());
        assertTrue(RpsSetTargetWinsAction.parseTargetWins("whenever").isEmpty());
    }

    @Test
    void targetActionStartsWithFreshRoundHistory() {
        Storage storage = new Storage();
        storage.put(RpsStorageKeys.ROUNDS, new JsonPrimitive("stale"));
        EventHistory events = new EventHistory();
        events.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "First to five wins"));

        new RpsSetTargetWinsAction(storage).execute(events, null);

        assertEquals(5, storage.get(RpsStorageKeys.TARGET_WINS).getAsInt());
        assertEquals(0, storage.get(RpsStorageKeys.ROUNDS).getAsJsonArray().size());
    }

    @Test
    void completionDecisionCountsWinsAndIgnoresDraws() {
        Storage storage = new Storage();
        storage.put(RpsStorageKeys.TARGET_WINS, new JsonPrimitive(2));
        storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive("rock"));
        JsonArray rounds = new JsonArray();
        rounds.add(round("agent"));
        rounds.add(round("draw"));
        storage.put(RpsStorageKeys.ROUNDS, rounds);
        RpsRoundCompletesMatchDecision decision = new RpsRoundCompletesMatchDecision(storage);

        EventHistory winningEvents = new EventHistory();
        winningEvents.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER,
                "{\"sign\":\"scissor\"}"));
        assertTrue(decision.decide(winningEvents, null));

        EventHistory drawEvents = new EventHistory();
        drawEvents.appendEvent(Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER,
                "{\"sign\":\"rock\"}"));
        assertFalse(decision.decide(drawEvents, null));
        assertEquals(new RpsMatchScore(1, 0, 1), RpsMatchScore.from(storage));
    }

    private static JsonObject round(String winner) {
        JsonObject round = new JsonObject();
        round.addProperty("winner", winner);
        return round;
    }
}
