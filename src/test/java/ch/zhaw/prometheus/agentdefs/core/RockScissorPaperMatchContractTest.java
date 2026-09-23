package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessage;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.rps.RpsSign;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class RockScissorPaperMatchContractTest {
    @Test
    void countdownHidesEveryCommittedSignAndResultRevealsIt() {
        for (RpsSign sign : RpsSign.values()) {
            Storage storage = new Storage();
            storage.put(RpsStorageKeys.CURRENT_AGENT_SIGN, new JsonPrimitive(sign.canonical()));
            storage.put(RpsStorageKeys.CURRENT_ROUND_NUMBER, new JsonPrimitive(1));

            BehaviourPlan countdown = new CoreRpsRevealPolicy(storage).onStart(null, null, null, null);
            assertCountdownHidesCommittedSign(countdown);

            JsonObject round = new JsonObject();
            round.addProperty("round", 1);
            round.addProperty("agentSign", sign.canonical());
            round.addProperty("userSign", sign.canonical());
            round.addProperty("winner", "draw");
            storage.put(RpsStorageKeys.LAST_ROUND, round);

            BehaviourPlan result = new CoreRpsResultPolicy(storage).onStart(null, null, null, null);
            assertDisplayedSignMatchesPlayedSign(result, sign.canonical());
        }
    }

    @Test
    void playsUntilEitherSideReachesTheConfiguredWinCount() {
        Agent agent = new RockScissorPaperMatch().createAgent();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new MatchGateway());

        BehaviourPlan setup = BehaviourPlan.fromJson(agent.start(runtime).getPayload());
        assertTrue(setup.getSpeech().contains("How many"));
        assertTrue(setup.getSpeech().contains("rock, paper, scissor"));
        assertTrue(RockScissorPaperMatch.PROMPT_STARTER.contains("rock, paper, scissor"));

        assertEquals(null, agent.acknowledge(userUtterance("However many"), runtime));
        BehaviourPlan retry = BehaviourPlan.fromJson(agent.generate(runtime).getPayload());
        assertTrue(retry.getSpeech().contains("did not catch a number"));
        assertFalse(retry.getSpeech().contains("Hello, I am Valerian"));
        assertEquals("Valerian Core RPS Match Setup", innerState(agent).getName());
        assertFalse(agent.getStorage().containsKey(RpsStorageKeys.TARGET_WINS));

        BehaviourPlan firstCountdown = BehaviourPlan.fromJson(
                agent.acknowledge(userUtterance("Two, yes, two wins"), runtime).getPayload());
        assertCountdownHidesCommittedSign(firstCountdown);
        assertEquals("rock", agent.getStorage().get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());
        assertEquals(2, agent.getStorage().get(RpsStorageKeys.TARGET_WINS).getAsInt());

        BehaviourPlan firstResult = BehaviourPlan.fromJson(agent.acknowledge(handSign("paper"), runtime).getPayload());
        assertDisplayedSignMatchesPlayedSign(firstResult, "rock");
        assertTrue(firstResult.getSpeech().contains("You win this round"));
        assertTrue(firstResult.getSpeech().contains("you 1, me 0"));
        assertEquals(1, firstResult.getDisplay().getAsJsonObject().get("userScore").getAsInt());
        assertTrue(agent.isActive());

        BehaviourPlan secondCountdown = BehaviourPlan.fromJson(
                agent.acknowledge(userUtterance("Ready for the next round"), runtime).getPayload());
        assertCountdownHidesCommittedSign(secondCountdown);
        assertEquals("scissor", agent.getStorage().get(RpsStorageKeys.CURRENT_AGENT_SIGN).getAsString());

        BehaviourPlan matchResult = BehaviourPlan.fromJson(agent.acknowledge(handSign("rock"), runtime).getPayload());
        assertDisplayedSignMatchesPlayedSign(matchResult, "scissor");
        assertTrue(matchResult.getSpeech().contains("You win the match 2 to 0"));
        assertTrue(matchResult.getSpeech().contains("Boo-hoo"));
        assertTrue(matchResult.getSpeech().contains("absolutely heartbroken"));
        assertTrue(matchResult.getSpeech().contains("virtual tears"));
        assertTrue(matchResult.getSpeech().contains("sob dramatically"));
        assertEquals("heartbrokenSadness", matchResult.getNonVerbal().getAsJsonObject()
                .getAsJsonObject("facialExpression").get("type").getAsString());
        assertEquals(0.92, matchResult.getNonVerbal().getAsJsonObject()
                .getAsJsonObject("motion").get("stillness").getAsDouble());
        assertEquals("game_result", matchResult.getDisplay().getAsJsonObject().get("mode").getAsString());
        assertEquals("user", matchResult.getDisplay().getAsJsonObject().get("winner").getAsString());
        assertEquals(2, matchResult.getDisplay().getAsJsonObject().get("targetWins").getAsInt());
        assertEquals("Valerian Core RPS Match Result", innerState(agent).getName());
        assertFalse(agent.isActive());
    }

    @Test
    void boastsAndCelebratesWhenValerianWinsTheMatch() {
        Agent agent = new RockScissorPaperMatch().createAgent();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new MatchGateway());

        agent.start(runtime);
        agent.acknowledge(userUtterance("one"), runtime);
        BehaviourPlan matchResult = BehaviourPlan.fromJson(
                agent.acknowledge(handSign("scissor"), runtime).getPayload());

        assertDisplayedSignMatchesPlayedSign(matchResult, "rock");
        assertTrue(matchResult.getSpeech().contains("I win the match 1 to 0"));
        assertTrue(matchResult.getSpeech().contains("magnificent victory"));
        assertTrue(matchResult.getSpeech().contains("undefeated champion"));
        assertTrue(matchResult.getSpeech().contains("victory parade"));
        assertTrue(matchResult.getSpeech().contains("glorious triumph"));
        assertEquals("triumphantJoy", matchResult.getNonVerbal().getAsJsonObject()
                .getAsJsonObject("facialExpression").get("type").getAsString());
        assertEquals(0.98, matchResult.getNonVerbal().getAsJsonObject()
                .getAsJsonObject("motion").get("energy").getAsDouble());
        assertFalse(agent.isActive());
    }

    @Test
    void germanCloneAcceptsGermanTargetAndKeepsTheWholeMatchInGerman() {
        Agent agent = new GermanRockScissorPaperMatch().createAgent();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), new GermanMatchGateway());

        BehaviourPlan setup = BehaviourPlan.fromJson(agent.start(runtime).getPayload());
        assertTrue(setup.getSpeech().contains("Wie viele"));
        assertTrue(setup.getSpeech().contains("Schere, Stein, Papier"));

        BehaviourPlan firstCountdown = BehaviourPlan.fromJson(
                agent.acknowledge(userUtterance("Zwei Siege"), runtime).getPayload());
        assertEquals("Schere, Stein, Papier – zeig!", firstCountdown.getSpeech());
        assertFalse(firstCountdown.getDisplay().getAsJsonObject().has("agentSign"));
        assertEquals(2, agent.getStorage().get(RpsStorageKeys.TARGET_WINS).getAsInt());
        assertEquals("de", agent.getStorage().get(RpsStorageKeys.LANGUAGE_CODE).getAsString());

        BehaviourPlan firstResult = BehaviourPlan.fromJson(
                agent.acknowledge(handSign("paper"), runtime).getPayload());
        assertTrue(firstResult.getSpeech().contains("Du gewinnst diese Runde"));
        assertTrue(firstResult.getSpeech().contains("Der Spielstand ist: du 1, ich 0"));
        assertTrue(firstResult.getSpeech().contains("Sag „bereit“"));

        BehaviourPlan secondCountdown = BehaviourPlan.fromJson(
                agent.acknowledge(userUtterance("Bereit für die nächste Runde"), runtime).getPayload());
        assertEquals("Schere, Stein, Papier – zeig!", secondCountdown.getSpeech());

        BehaviourPlan matchResult = BehaviourPlan.fromJson(
                agent.acknowledge(handSign("rock"), runtime).getPayload());
        assertTrue(matchResult.getSpeech().contains("Du gewinnst das Match 2 zu 0"));
        assertTrue(matchResult.getSpeech().contains("Buhuhu"));
        assertTrue(matchResult.getSpeech().contains("dramatisch schluchze"));
        assertEquals("Schere, Stein, Papier",
                matchResult.getDisplay().getAsJsonObject().get("title").getAsString());
        assertFalse(agent.isActive());
    }

    private static Event userUtterance(String text) {
        return Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, text);
    }

    private static Event handSign(String sign) {
        return Event.observation(Event.TYPE_HAND_SIGN, Event.ACTOR_USER, "{\"sign\":\"" + sign + "\"}");
    }

    private static void assertDisplayedSignMatchesPlayedSign(BehaviourPlan plan, String expectedSign) {
        String displayedSign = plan.getDisplay().getAsJsonObject().get("agentSign").getAsString();
        String semanticGesture = plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString();
        assertNull(plan.getMotion());
        assertEquals(expectedSign, displayedSign);
        assertEquals(displayedSign, semanticGesture);
    }

    private static void assertCountdownHidesCommittedSign(BehaviourPlan plan) {
        assertEquals("Rock, scissor, paper—show!", plan.getSpeech());
        assertEquals("NONE", plan.getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        assertNull(plan.getMotion());
        JsonObject display = plan.getDisplay().getAsJsonObject();
        assertEquals("game_countdown", display.get("mode").getAsString());
        assertEquals("capture_user_sign", display.get("phase").getAsString());
        assertTrue(display.get("awaitingUserSign").getAsBoolean());
        assertFalse(display.has("agentSign"));
        assertFalse(display.has("userSign"));
    }

    private static ch.zhaw.prometheus.model.State innerState(Agent agent) {
        return ((OuterState) agent.getCurrentState()).getInnerCurrent();
    }

    private static final class MatchGateway implements LanguageModelGateway {
        @Override
        public String complete(List<PromptMessage> messages) {
            if (join(messages).contains("However many")) {
                return """
                        {
                          "speech": "I did not catch a number there. How many round wins should be required?",
                          "nonVerbal": {"gesture": "OPEN_QUESTION"},
                          "motion": null,
                          "display": null
                        }
                        """;
            }
            return """
                    {
                      "speech": "Hello, I am Valerian. We are playing rock, paper, scissor. How many round wins should be required? For example, three.",
                      "nonVerbal": {"gesture": "OPEN_QUESTION"},
                      "motion": null,
                      "display": null
                    }
                    """;
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return join(messages).contains("ready to start the next round of rock-scissor-paper");
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }

        private static String join(List<PromptMessage> messages) {
            return messages.stream().map(PromptMessage::getContent).reduce("", (left, right) -> left + "\n" + right);
        }
    }

    private static final class GermanMatchGateway implements LanguageModelGateway {
        @Override
        public String complete(List<PromptMessage> messages) {
            return """
                    {
                      "speech": "Hallo, ich bin Valerian. Wir spielen Schere, Stein, Papier. Wie viele Rundensiege sollen nötig sein? Zum Beispiel drei.",
                      "nonVerbal": {"gesture": "OPEN_QUESTION"},
                      "motion": null,
                      "display": null
                    }
                    """;
        }

        @Override
        public boolean decide(List<PromptMessage> messages) {
            return join(messages).contains("eindeutig bereit ist");
        }

        @Override
        public JsonElement extract(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public JsonElement summarise(List<PromptMessage> messages) {
            return JsonNull.INSTANCE;
        }

        @Override
        public String summariseOffline(List<PromptMessage> messages) {
            return "";
        }

        private static String join(List<PromptMessage> messages) {
            return messages.stream().map(PromptMessage::getContent).reduce("", (left, right) -> left + "\n" + right);
        }
    }

}
