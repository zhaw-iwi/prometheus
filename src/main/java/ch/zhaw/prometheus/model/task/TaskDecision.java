package ch.zhaw.prometheus.model.task;

import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import jakarta.persistence.Entity;

/** Pure, local task routing: unmatched sensor frames and ticks never request inference. */
@Entity
public class TaskDecision extends Decision {
    private String taskCondition;
    protected TaskDecision() {}
    public TaskDecision(Storage storage, String condition) {
        super(new TaskPolicy(storage, "")); this.taskCondition = condition;
    }
    @Override public boolean decide(EventHistory events, PolicyRuntime runtime) {
        Storage storage = ((TaskPolicy) getPolicy()).storage();
        return "CUE".equals(taskCondition) ? matching(storage, events, runtime, Instant.now()).isPresent()
                : taskCondition.equals(TaskMemory.phase(storage));
    }
    static Optional<TaskSpec.Rule> matching(Storage storage, EventHistory history, PolicyRuntime runtime, Instant now) {
        if (!"RUNNING".equals(TaskMemory.phase(storage)) || !storage.containsKey(TaskMemory.SPEC) || history.isEmpty()) return Optional.empty();
        var events = history.toList(); var latest = events.getLast();
        if (!Event.KIND_OBSERVATION.equals(latest.getKind()) || !TaskMemory.fresh(latest, now)) return Optional.empty();
        var spec = TaskSpec.parse(storage.get(TaskMemory.SPEC));
        Instant after = Instant.parse(TaskMemory.text(storage, TaskMemory.AFTER, Instant.EPOCH.toString()));
        if (now.isBefore(after) || !TaskMemory.observed(latest).isAfter(after)) return Optional.empty();
        int firstSample = 0;
        // A native completed segment after the most recent spoken intent is a conservative
        // backend completion signal, not proof of physical audibility. Incomplete segments do not arm a cue.
        if (runtime.externalSpeech() != null) {
            int intent = -1, completed = -1;
            for (int i = 0; i < events.size(); i++) {
                var provenance = events.get(i).speechProvenance();
                if (provenance == null || !provenance.sessionId().equals(runtime.externalSpeech().sessionId())
                        || !provenance.epoch().equals(runtime.externalSpeech().epoch())) continue;
                if (provenance.origin() == SpeechProvenance.Origin.BACKEND_INTENT
                        && ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(events.get(i).getPayload()).getSpeech() != null) intent = i;
                if (provenance.origin() == SpeechProvenance.Origin.NATIVE && provenance.complete()) completed = i;
            }
            if (intent >= 0 && completed <= intent) return Optional.empty();
            if (intent >= 0) firstSample = completed + 1;
        }
        final int from = firstSample;
        return spec.rules().stream().sorted(Comparator.comparing(TaskSpec.Rule::complete).reversed())
                .filter(rule -> rule.eventType().equals(latest.getType()))
                .filter(rule -> stable(rule, events.subList(from, events.size()), after, now)).findFirst();
    }
    private static boolean stable(TaskSpec.Rule rule, List<Event> events, Instant after, Instant now) {
        int count = 0; var seen = new HashSet<Instant>(); Instant newer = null;
        for (int i = events.size() - 1; i >= 0; i--) {
            var event = events.get(i);
            if (!rule.eventType().equals(event.getType())) continue;
            var time = TaskMemory.observed(event);
            if (time == null || !time.isAfter(after) || !TaskMemory.fresh(event, now) || !matches(rule, event)) return false;
            if (newer != null && time.isAfter(newer)) return false;
            newer = time;
            if (seen.add(time) && ++count >= rule.samples()) return true;
        }
        return false;
    }
    private static boolean matches(TaskSpec.Rule rule, Event event) {
        try {
            var object = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            if (object.has("facePresent") && !object.get("facePresent").getAsBoolean() && !rule.field().equals("facePresent")) return false;
            double confidence = object.has("confidence") ? object.get("confidence").getAsDouble()
                    : object.has("avgDetectionConfidence") ? object.get("avgDetectionConfidence").getAsDouble() : 0;
            if (!Double.isFinite(confidence) || confidence < rule.minConfidence()) return false;
            JsonElement value = object;
            for (String part : rule.field().split("\\.")) value = value.isJsonArray()
                    ? value.getAsJsonArray().get(Integer.parseInt(part)) : value.getAsJsonObject().get(part);
            if (value == null || !value.isJsonPrimitive()) return false;
            if (rule.operator().equals("eq")) return value.equals(rule.value());
            if (!value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) return false;
            return rule.operator().equals("lt") ? value.getAsDouble() < rule.value().getAsDouble() : value.getAsDouble() > rule.value().getAsDouble();
        } catch (RuntimeException invalid) { return false; }
    }
}
