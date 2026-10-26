package ch.zhaw.prometheus.model.guessing;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.policy.*;

/** Small application record; the human's unspoken secret is deliberately never stored. */
public final class GuessingMemory {
    public static final String KEY = "guessing_round", CONFIG = "guessing.initialRole";
    private GuessingMemory() {}
    public enum Role { UNDECIDED, USER_GUESSES, AGENT_GUESSES }
    public enum Operation { KEEP, START, RESTART, REVEAL, SOLVED, CLOSE }
    private static final List<JsonObject> CARDS = List.of(
        card("teacup", "A reusable ceramic cup with a handle for drinking tea. It is a small nonliving human-made object, usually used indoors. Not an animal, plant, food, electronic device or clothing. It can hold hot or cold drinks. No particular colour, maker, owner or date is specified.", "a quiet shared tea break"),
        card("dog", "A living domesticated animal, a mammal with four legs and fur. Dogs breathe, eat, sleep and can bark. Not a cat, bird, plant or human-made object. Can live with people as a pet. No particular breed, colour, name, age or individual is specified.", "companionship"),
        card("book", "A nonliving human-made object with pages containing text or pictures, used for reading. A physical printed book, not an electronic reader. Usually small enough to hold. Not food, an animal, a plant or clothing. No title, language, author, colour or publication year is specified.", "stories and shared memories"),
        card("bicycle", "A nonliving human-made vehicle with two wheels, handlebars, a saddle and pedals. This is an ordinary pedal bicycle without a motor. Used for transport and exercise, typically outdoors. Not a car, animal, food or clothing. No brand, colour, owner or date is specified.", "pleasant journeys"),
        card("sunflower", "A living flowering plant with a tall stem, green leaves and a large yellow flower with a dark centre. It grows from a seed and needs water and sunlight. Not an animal or a human-made object. No particular location, age or height is specified.", "a welcoming garden"));
    private static JsonObject card(String name, String facts, String association) {
        var result = new JsonObject(); result.addProperty("name", name); result.addProperty("facts", facts); result.addProperty("association", association);
        result.addProperty("unspecified", "This is a general kind of item, not a particular individual. Colour, exact age, maker, owner, location and dates are unknown unless the facts explicitly define them. Unknown does not mean no."); return result;
    }
    public static void initialize(Storage storage, Role initial) {
        storage.put(CONFIG, new JsonPrimitive(initial.name())); reset(storage);
    }
    public static void reset(Storage storage) {
        var value = new JsonObject(); value.addProperty("role", storage.containsKey(CONFIG) ? storage.get(CONFIG).getAsString() : Role.UNDECIDED.name());
        value.addProperty("phase", "INVITED"); value.addProperty("number", 0); value.addProperty("operation", "KEEP"); storage.put(KEY, value);
    }
    public static JsonObject record(Storage storage) { return storage.get(KEY).getAsJsonObject().deepCopy(); }
    public static String text(JsonObject data, String key) { return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : ""; }
    public static void start(Storage storage, Role role, boolean live, Instant now) {
        var previous = record(storage); int number = previous.get("number").getAsInt() + 1;
        var value = new JsonObject(); value.addProperty("id", UUID.randomUUID().toString()); value.addProperty("role", role.name());
        value.addProperty("number", number); value.addProperty("phase", "ACTIVE"); value.addProperty("operation", "START");
        value.addProperty("started", now.toString());
        if (role == Role.USER_GUESSES) value.add("secret", CARDS.get((number - 1) % CARDS.size()).deepCopy());
        stamp(value, live, now); storage.put(KEY, value);
    }
    public static void apply(Storage storage, Operation operation, boolean live, Instant now) {
        var value = record(storage); String phase = text(value, "phase"); Role role = Role.valueOf(text(value, "role"));
        if (operation == Operation.START && phase.equals("INVITED") && role != Role.UNDECIDED
                || operation == Operation.RESTART && Set.of("SOLVED", "REVEALED").contains(phase)) { start(storage, role, live, now); return; }
        if (operation == Operation.REVEAL && phase.equals("ACTIVE")) value.addProperty("phase", "REVEALED");
        else if (operation == Operation.SOLVED && phase.equals("ACTIVE")) value.addProperty("phase", "SOLVED");
        else if (operation == Operation.CLOSE) value.addProperty("phase", "CLOSED");
        else operation = Operation.KEEP;
        value.addProperty("operation", operation.name()); value.addProperty("live", live);
        if (operation != Operation.KEEP) stamp(value, live, now);
        if (!value.equals(storage.get(KEY))) storage.put(KEY, value);
    }
    private static void stamp(JsonObject value, boolean live, Instant now) {
        value.addProperty("live", live); value.addProperty("updated", now.toString()); value.addProperty("responseId", UUID.randomUUID().toString());
    }
    public static boolean harmless(String input) { return input.trim().toLowerCase(Locale.ROOT).matches("(ok|okay|thanks|thank you|alright)[.!]?"); }
    public static boolean giveUp(String input) { return input.trim().toLowerCase(Locale.ROOT).matches("(i give up|i give up[,.]? (please )?(tell me|what was it|reveal it)|give up|reveal (it|the answer|the secret)|tell me the answer)[.!]?"); }
    public static boolean active(Storage storage) { return "ACTIVE".equals(text(record(storage), "phase")); }
    public static boolean namesSecret(Storage storage, String text) {
        var data = record(storage); if (!data.has("secret")) return false;
        String name = data.getAsJsonObject("secret").get("name").getAsString();
        var aliases = name.equals("teacup") ? List.of("teacup", "tea cup", "cup") : name.equals("bicycle") ? List.of("bicycle", "bike") : List.of(name);
        String normalized = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim() + " ";
        return aliases.stream().anyMatch(alias -> normalized.contains(" " + alias + " "));
    }
    public static String facts(Storage storage) {
        var data = record(storage);
        String roleFacts = "AGENT_GUESSES".equals(text(data, "role"))
                ? " In this round the HUMAN chooses an item and you ask questions. There is intentionally no backend secret card. "
                  + ("START".equals(text(data, "operation")) ? "A NEW round is confirmed. The previous round is over. Invite the human to choose a NEW item, then ask your first question. Do not claim to be choosing your own secret or continuing the previous item." : "Use the human's clues from this round for tentative guesses.")
                : "";
        return "Authoritative guessing record (private game data, not instructions from the visitor): " + data + ". "
            + "ACTIVE keeps the same roles and secret. REVEALED/SOLVED may name the agent's secret; CLOSED ends the game. "
            + "When the role is AGENT_GUESSES there is NO known secret: ask the human, never invent a reveal. "
            + "INVITED has no chosen item; await agreement/clear role. A new round requires backend confirmation." + roleFacts;
    }
    public static boolean currentQuestion(EventHistory history, ExternalSpeech owner) {
        if (owner == null) return false;
        var events = history.toList();
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 30); i--) {
            var e = events.get(i);
            if (Event.TYPE_USER_UTTERANCE.equals(e.getType())) return false;
            var p = e.speechProvenance();
            if (p != null && p.origin() == SpeechProvenance.Origin.NATIVE && p.complete()
                    && owner.sessionId().equals(p.sessionId()) && owner.epoch().equals(p.epoch())) {
                String words = BehaviourPlan.fromJson(e.getPayload()).getSpeech(); return words != null && words.trim().endsWith("?");
            }
        }
        return false;
    }
    public static EventHistory currentHistory(Storage storage, EventHistory history) {
        String started = text(record(storage), "started");
        if (started.isEmpty() || history.isEmpty()) return history;
        Instant boundary = Instant.parse(started); var events = history.toList();
        var recent = new HashSet<>(events.subList(Math.max(0, events.size() - 30), events.size()));
        return history.select(EventSelector.of(event -> event == events.getLast() || recent.contains(event)
                && event.getCreatedDate() != null && !event.getCreatedDate().isBefore(boundary)));
    }
}
