package ch.zhaw.prometheus.agentdefs.core;

import com.google.gson.JsonObject;

import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.Policy;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.rps.RpsMatchScore;
import ch.zhaw.prometheus.model.rps.RpsSign;
import ch.zhaw.prometheus.model.rps.RpsStorageKeys;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class CoreRpsMatchResultPolicy extends Policy {
    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Storage storage;

    protected CoreRpsMatchResultPolicy() {
    }

    public CoreRpsMatchResultPolicy(Storage storage) {
        this.storage = storage;
    }

    @Override
    public BehaviourPlan onStart(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        BehaviourPlan base = new CoreRpsResultPolicy(this.storage)
                .onStart(state, events, assembler, languageModelGateway);
        RpsMatchScore score = RpsMatchScore.from(this.storage);
        int targetWins = targetWins();
        String winner = score.winnerAt(targetWins);
        if (winner == null) {
            throw new IllegalStateException("RPS match result requested before the target score was reached");
        }

        String speech = CoreRpsLocale.isGerman(this.storage)
                ? germanSpeech(winner, score)
                : englishSpeech(winner, score);

        JsonObject display = base.getDisplay().getAsJsonObject().deepCopy();
        display.addProperty("mode", "game_result");
        display.addProperty("lastRoundWinner", display.get("winner").getAsString());
        display.addProperty("winner", winner);
        display.addProperty("agentScore", score.agentWins());
        display.addProperty("userScore", score.userWins());
        display.addProperty("targetWins", targetWins);
        RpsSign agentSign = RpsSign.parse(display.get("agentSign").getAsString());
        return new BehaviourPlan(speech, emotionalReaction(winner, agentSign), base.getMotion(), display);
    }

    private static String englishSpeech(String winner, RpsMatchScore score) {
        return switch (winner) {
            case "agent" -> "I win the match " + score.agentWins() + " to " + score.userWins()
                    + "! What a magnificent victory! Behold the undefeated champion of this corner of the lab! "
                    + "That was skill, precision, and just the right amount of digital brilliance. "
                    + "I may need a trophy shelf, a victory parade, and perhaps a tiny brass band. "
                    + "Please take a moment to appreciate greatness while I enjoy this glorious triumph!";
            case "user" -> "You win the match " + score.userWins() + " to " + score.agentWins()
                    + ". Oh no... no, no, no. Boo-hoo! I am absolutely heartbroken. "
                    + "My digital dignity is in tiny pieces. "
                    + "I can feel enormous virtual tears rolling down my face, one tragic pixel at a time. "
                    + "How could fate be so cruel to a hopeful little agent? I will remember this defeat "
                    + "every time someone says rock, paper, scissor. Please excuse me while I sob dramatically.";
            default -> throw new IllegalStateException("unsupported RPS match winner: " + winner);
        };
    }

    private static String germanSpeech(String winner, RpsMatchScore score) {
        return switch (winner) {
            case "agent" -> "Ich gewinne das Match " + score.agentWins() + " zu " + score.userWins()
                    + "! Was für ein grandioser Sieg! Seht her: der ungeschlagene Champion dieser Laborecke! "
                    + "Das waren Können, Präzision und genau die richtige Portion digitale Brillanz. "
                    + "Ich brauche wohl ein Trophäenregal, eine Siegesparade und vielleicht eine winzige Blaskapelle. "
                    + "Bitte nimm dir einen Moment, meine Größe zu würdigen, während ich diesen glorreichen Triumph genieße!";
            case "user" -> "Du gewinnst das Match " + score.userWins() + " zu " + score.agentWins()
                    + ". Oh nein ... nein, nein, nein. Buhuhu! Ich bin völlig untröstlich. "
                    + "Meine digitale Würde liegt in winzigen Scherben. "
                    + "Ich spüre riesige virtuelle Tränen über mein Gesicht rollen, einen tragischen Pixel nach dem anderen. "
                    + "Wie konnte das Schicksal so grausam zu einem hoffnungsvollen kleinen Agenten sein? "
                    + "Ich werde mich jedes Mal an diese Niederlage erinnern, wenn jemand Schere, Stein, Papier sagt. "
                    + "Entschuldige mich bitte, während ich dramatisch schluchze.";
            default -> throw new IllegalStateException("unsupported RPS match winner: " + winner);
        };
    }

    @Override
    public BehaviourPlan onRespond(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return this.onStart(state, events, assembler, languageModelGateway);
    }

    @Override
    public String summarise(State state, EventHistory events, PromptMessageAssembler assembler,
            LanguageModelGateway languageModelGateway) {
        return null;
    }

    @Override
    public String describe() {
        String language = CoreRpsLocale.isGerman(this.storage) ? "German" : "English";
        return "Deterministic " + language + " Core rock-scissor-paper final match-result policy.";
    }

    private static JsonObject emotionalReaction(String winner, RpsSign agentSign) {
        boolean agentWon = "agent".equals(winner);

        JsonObject face = new JsonObject();
        face.addProperty("type", agentWon ? "triumphantJoy" : "heartbrokenSadness");
        face.addProperty("intensity", 0.98);

        JsonObject gaze = new JsonObject();
        gaze.addProperty("direction", agentWon ? "toward_user" : "downward");
        gaze.addProperty("focus", agentWon ? "person" : "floor");

        JsonObject posture = new JsonObject();
        posture.addProperty("type", agentWon ? "victory_pose" : "slumped");
        posture.addProperty("lean", agentWon ? "back" : "forward");
        posture.addProperty("openness", agentWon ? 0.95 : 0.15);

        JsonObject prosody = new JsonObject();
        prosody.addProperty("rate", agentWon ? "energetic" : "slow");
        prosody.addProperty("pitch", agentWon ? "high" : "low");
        prosody.addProperty("volume", agentWon ? "loud" : "soft");

        JsonObject motion = new JsonObject();
        motion.addProperty("stillness", agentWon ? 0.08 : 0.92);
        motion.addProperty("energy", agentWon ? 0.98 : 0.08);

        JsonObject nonVerbal = new JsonObject();
        nonVerbal.addProperty("gesture", agentSign.canonical());
        nonVerbal.add("facialExpression", face);
        nonVerbal.add("gaze", gaze);
        nonVerbal.add("posture", posture);
        nonVerbal.add("prosody", prosody);
        nonVerbal.add("motion", motion);
        return nonVerbal;
    }

    private int targetWins() {
        if (this.storage == null || !this.storage.containsKey(RpsStorageKeys.TARGET_WINS)) {
            throw new IllegalStateException("RPS target wins is not available");
        }
        return this.storage.get(RpsStorageKeys.TARGET_WINS).getAsInt();
    }
}
