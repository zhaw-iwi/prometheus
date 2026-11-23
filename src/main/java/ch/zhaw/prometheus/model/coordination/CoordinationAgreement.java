package ch.zhaw.prometheus.model.coordination;

import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.task.TaskSpec;
import ch.zhaw.prometheus.model.task.HandSignCondition;
import ch.zhaw.prometheus.model.task.HandPoseCondition;
import ch.zhaw.prometheus.model.event.Event;

/** A bounded agreement; dialogue needs no invented sensor rule. Preparation is evidence, not model output. */
public record CoordinationAgreement(String goal, String humanRole, String agentRole, String success,
        List<String> questions, List<Check> checks, int dialogueLimit, TaskSpec reactions) {
    private static final Gson JSON = new Gson();
    public record Check(String eventType, String field, String operator, JsonPrimitive value, double minConfidence, HandSignCondition handSign, HandPoseCondition handPose, ch.zhaw.prometheus.model.task.HandPointCondition handPoint, ch.zhaw.prometheus.model.task.HandSequence handSequence) {
        public Check(String eventType,String field,String operator,JsonPrimitive value,double minConfidence,HandSignCondition handSign,HandPoseCondition handPose,ch.zhaw.prometheus.model.task.HandPointCondition handPoint){this(eventType,field,operator,value,minConfidence,handSign,handPose,handPoint,null);}
        public Check(String eventType,String field,String operator,JsonPrimitive value,double minConfidence,HandSignCondition handSign,HandPoseCondition handPose) {
            this(eventType,field,operator,value,minConfidence,handSign,handPose,null);
        }
        public Check(String eventType, String field, String operator, JsonPrimitive value, double minConfidence, HandSignCondition handSign) {
            this(eventType,field,operator,value,minConfidence,handSign,null);
        }
        public Check(String eventType, String field, String operator, JsonPrimitive value, double minConfidence) {
            this(eventType, field, operator, value, minConfidence, null);
        }
        public TaskSpec.Rule rule() {
            return new TaskSpec.Rule(eventType, field, operator, value, "Recognise the agreed observation",
                    TaskSpec.Effect.ACT, minConfidence, 1, 3, handPose, handSign, handPoint,handSequence);
        }
        public String description() { return handSequence!=null?handSequence.description():handPoint!=null ? handPoint.description() : handPose != null ? handPose.description() : handSign == null ? value.getAsString() : handSign.description(); }
    }
    public static boolean supportedObservation(String type) {
        return TaskSpec.FIELDS.containsKey(type) || Event.TYPE_HAND_SIGNS.equals(type) || Event.TYPE_HAND_POSE.equals(type) || Event.TYPE_HAND_POINTING.equals(type)||ch.zhaw.prometheus.model.task.HandSequence.RULE_TYPE.equals(type);
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
                String type = text(check, "eventType", 80);
                boolean named = Event.TYPE_HAND_SIGNS.equals(type), pose = Event.TYPE_HAND_POSE.equals(type), pointing=Event.TYPE_HAND_POINTING.equals(type),sequence=ch.zhaw.prometheus.model.task.HandSequence.RULE_TYPE.equals(type);
                exact(check, named || pose || pointing || sequence ? Set.of("eventType", sequence?"handSequence":named ? "handSign" : pose ? "handPose" : "handPoint", "minConfidence")
                        : Set.of("eventType", "field", "operator", "value", "minConfidence"));
                if (!check.get("minConfidence").getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
                var parsed = sequence?new Check(type,null,null,null,check.get("minConfidence").getAsDouble(),null,null,null,ch.zhaw.prometheus.model.task.HandSequence.parse(check.get("handSequence")))
                        : pointing ? new Check(type,null,null,null,check.get("minConfidence").getAsDouble(),null,null,ch.zhaw.prometheus.model.task.HandPointCondition.parse(check.get("handPoint")))
                        : named ? new Check(type, null, null, null, check.get("minConfidence").getAsDouble(), HandSignCondition.parse(check.get("handSign")))
                        : pose ? new Check(type,null,null,null,check.get("minConfidence").getAsDouble(),null,HandPoseCondition.parse(check.get("handPose")))
                        : new Check(type, text(check, "field", 80), text(check, "operator", 8),
                            check.getAsJsonPrimitive("value").deepCopy(), check.get("minConfidence").getAsDouble());
                if (pose && !parsed.handPose().versioned()) throw new IllegalArgumentException("Coordination requires versioned pose conditions");
                // Reuse the executable field/type/confidence contract, including observed-absence semantics.
                TaskSpec.parse(new TaskSpec("Preparation", 1, List.of(parsed.rule()),true,sequence?TaskSpec.FirstAction.ON_CUE:TaskSpec.FirstAction.IMMEDIATE).json()).executable();
                if (!seen.add(parsed.rule().conditionKey()))
                    throw new IllegalArgumentException();
                checks.add(parsed);
            }
            if (checks.size() > 3) throw new IllegalArgumentException();
            var limit = object.getAsJsonPrimitive("dialogueLimit");
            if (!limit.isNumber()) throw new IllegalArgumentException();
            int turns = limit.getAsBigDecimal().intValueExact();
            if (turns < 1 || turns > 50) throw new IllegalArgumentException();
            var task = object.get("reactions").isJsonNull() ? null : TaskSpec.parse(object.get("reactions")).executable();
            if (task != null && (!task.goal().equals(goal)
                    || task.rules().stream().anyMatch(rule -> !supportedObservation(rule.eventType()) || rule.handPose() != null && !rule.handPose().versioned())))
                throw new IllegalArgumentException();
            var agreement = new CoordinationAgreement(goal, human, agent, success, List.copyOf(questions), List.copyOf(checks), turns, task);
            boolean sequenced=checks.stream().anyMatch(c->c.handSequence()!=null)||task!=null&&task.rules().stream().anyMatch(r->r.handSequence()!=null);
            if (agreement.json().toString().getBytes(StandardCharsets.UTF_8).length > (sequenced?32768:3000)) throw new IllegalArgumentException();
            return agreement;
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid coordination agreement"); }
    }
    public boolean complete() { return questions.isEmpty() && !goal.isBlank() && !humanRole.isBlank() && !agentRole.isBlank() && !success.isBlank(); }
    /** Editing response content cannot silently change the executable contract or the human's commitments. */
    public boolean sameTerms(CoordinationAgreement other) {
        return protectedTerms().equals(other.protectedTerms());
    }
    public JsonObject protectedTerms() {
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
        for(int i=0;i<checks.size();i++)if(checks.get(i).handPoint()!=null)value.getAsJsonArray("checks").get(i).getAsJsonObject().add("handPoint",checks.get(i).handPoint().json());
        for(int i=0;i<checks.size();i++)if(checks.get(i).handSequence()!=null)value.getAsJsonArray("checks").get(i).getAsJsonObject().add("handSequence",checks.get(i).handSequence().json());
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
