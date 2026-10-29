package ch.zhaw.prometheus.model.coordination;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.task.TaskSpec;

/** A bounded agreement; dialogue needs no invented sensor rule. Preparation is evidence, not model output. */
public record CoordinationAgreement(String goal, String humanRole, String agentRole, String success,
        List<String> questions, List<Check> checks, int dialogueLimit, TaskSpec reactions) {
    private static final Gson JSON = new Gson();
    public record Check(String eventType, String field, String operator, JsonPrimitive value, double minConfidence) {
        public TaskSpec.Rule rule() {
            return new TaskSpec.Rule(eventType, field, operator, value, "Recognise the agreed observation",
                    TaskSpec.Effect.ACT, minConfidence, 1, 3);
        }
    }
    public static CoordinationAgreement parse(JsonElement raw) {
        try {
            var object = raw.getAsJsonObject();
            exact(object, Set.of("goal", "humanRole", "agentRole", "success", "questions", "checks", "dialogueLimit", "reactions"));
            String goal = text(object, "goal", 300), human = text(object, "humanRole", 300), agent = text(object, "agentRole", 300), success = text(object, "success", 300);
            var questions = new ArrayList<String>();
            for (var question : object.getAsJsonArray("questions")) {
                if (!question.isJsonPrimitive() || !question.getAsJsonPrimitive().isString()
                        || question.getAsString().isBlank() || question.getAsString().length() > 160) throw new IllegalArgumentException();
                questions.add(question.getAsString());
            }
            if (questions.size() > 4) throw new IllegalArgumentException();
            var checks = new ArrayList<Check>(); var seen = new HashSet<String>();
            for (var item : object.getAsJsonArray("checks")) {
                var check = item.getAsJsonObject();
                exact(check, Set.of("eventType", "field", "operator", "value", "minConfidence"));
                if (!check.get("minConfidence").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                var parsed = new Check(text(check, "eventType", 80), text(check, "field", 80), text(check, "operator", 8),
                        check.getAsJsonPrimitive("value").deepCopy(), check.get("minConfidence").getAsDouble());
                // Reuse the executable field/type/confidence contract, including observed-absence semantics.
                TaskSpec.parse(new TaskSpec("Preparation", 1, List.of(parsed.rule())).json()).executable();
                if (!seen.add(parsed.eventType() + "/" + parsed.field() + "/" + parsed.operator() + "/" + parsed.value()))
                    throw new IllegalArgumentException();
                checks.add(parsed);
            }
            if (checks.size() > 3) throw new IllegalArgumentException();
            var limit = object.getAsJsonPrimitive("dialogueLimit");
            if (!limit.isNumber()) throw new IllegalArgumentException();
            int turns = limit.getAsBigDecimal().intValueExact();
            if (turns < 1 || turns > 50) throw new IllegalArgumentException();
            var task = object.get("reactions").isJsonNull() ? null : TaskSpec.parse(object.get("reactions")).executable();
            if (task != null && !task.goal().equals(goal)) throw new IllegalArgumentException();
            var agreement = new CoordinationAgreement(goal, human, agent, success, List.copyOf(questions), List.copyOf(checks), turns, task);
            if (agreement.json().toString().getBytes(StandardCharsets.UTF_8).length > 3000) throw new IllegalArgumentException();
            return agreement;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid coordination agreement"); }
    }
    public boolean complete() { return questions.isEmpty() && !goal.isBlank() && !humanRole.isBlank() && !agentRole.isBlank() && !success.isBlank(); }
    /** Editing response content cannot silently change the executable contract or the human's commitments. */
    public boolean sameTerms(CoordinationAgreement other) {
        return protectedTerms().equals(other.protectedTerms());
    }
    private JsonObject protectedTerms() {
        var value = json();
        for (String key : List.of("goal", "agentRole", "questions")) value.remove(key);
        if (!value.get("reactions").isJsonNull()) {
            var task = value.getAsJsonObject("reactions"); task.remove("goal");
            for (var rule : task.getAsJsonArray("rules")) rule.getAsJsonObject().remove("action");
        }
        return value;
    }
    public JsonObject json() {
        var value = JSON.toJsonTree(this).getAsJsonObject();
        value.add("reactions", reactions == null ? JsonNull.INSTANCE : reactions.json());
        return value;
    }
    public static void exact(JsonObject object, Set<String> fields) {
        if (!object.keySet().equals(fields)) throw new IllegalArgumentException("Unexpected coordination fields");
    }
    private static String text(JsonObject object, String key, int max) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > max) throw new IllegalArgumentException();
        return value.getAsString().strip();
    }
}
