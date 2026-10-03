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
                : "RUNNING".equals(taskCondition) ? TaskMemory.active(storage) : taskCondition.equals(TaskMemory.phase(storage));
    }
    static Optional<TaskSpec.Rule> matching(Storage storage, EventHistory history, PolicyRuntime runtime, Instant now) {
        TaskMemory.trace(storage);
        if (!TaskMemory.active(storage) || !storage.containsKey(TaskMemory.SPEC) || history.isEmpty()) return waitFor("task_inactive", null);
        var events = history.toList(); var latest = events.getLast();
        if (!Event.KIND_OBSERVATION.equals(latest.getKind()) || !TaskMemory.fresh(latest, now)) return waitFor("stale_or_missing_observation", latest.getId());
        var spec = TaskSpec.parse(storage.get(TaskMemory.SPEC));
        Instant after = Instant.parse(TaskMemory.text(storage, TaskMemory.AFTER, Instant.EPOCH.toString()));
        if (now.isBefore(after) || !TaskMemory.observed(latest).isAfter(after)) return waitFor("cooldown", latest.getId());
        int firstSample = firstSample(events, runtime.externalSpeech());
        if (firstSample < 0) return waitFor("awaiting_response_capture", latest.getId());
        final int from = firstSample;
        var reason = new String[]{"waiting_for_cue"};
        var result = spec.rules().stream().sorted(Comparator.comparing(TaskSpec.Rule::effect).reversed())
                .filter(rule -> rule.effect() != TaskSpec.Effect.WAIT || !"WAITING".equals(TaskMemory.phase(storage)))
                .filter(rule -> rule.eventType().equals(latest.getType()))
                .filter(rule -> stable(rule, events.subList(from, events.size()), after, now, reason)).findFirst();
        ch.zhaw.prometheus.logging.ActivityTrace.cue(result.isPresent() ? "cue_matched" : reason[0], latest.getId(), latest.getType());
        return result;
    }
    static int firstSample(List<Event> events, ch.zhaw.prometheus.model.policy.ExternalSpeech owner) {
        // Captured content is not physical playback completion. A short acknowledgment or an
        // unrelated completed segment must not release the response boundary.
        if (owner != null) {
            int intent = -1, boundary = -1;
            String expected = ""; var captured = new StringBuilder();
            for (int i = 0; i < events.size(); i++) {
                var provenance = events.get(i).speechProvenance();
                if (provenance == null || !provenance.sessionId().equals(owner.sessionId())
                        || !provenance.epoch().equals(owner.epoch())) continue;
                String speech = ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(events.get(i).getPayload()).getSpeech();
                if (provenance.origin() == SpeechProvenance.Origin.BACKEND_INTENT && speech != null && !speech.isBlank()) {
                    intent = i; expected = speech; captured.setLength(0); boundary = -1;
                }
                if (intent >= 0 && boundary < 0 && provenance.origin() == SpeechProvenance.Origin.NATIVE && provenance.complete() && speech != null) {
                    // Selector projections intentionally have no persistence IDs; content/session association still applies.
                    var intentId = events.get(intent).getId();
                    if (intentId != null && !provenance.intentIds().isEmpty() && !provenance.intentIds().contains(intentId)) continue;
                    captured.append(' ').append(normalize(speech));
                    // Associate ordered response content across ASR segments, allowing inserted acknowledgments.
                    if (containsResponse(expected, captured.toString())) boundary = i + 1;
                }
            }
            if (intent >= 0) return boundary;
        }
        return 0;
    }
    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static boolean containsResponse(String expected, String captured) {
        if (ordered(normalize(expected), captured)) return true;
        // Live may shorten trailing explanations of waiting rules. Match a complete substantive
        // opening (two sentences, never a question alone), tolerating function-word ASR variation.
        // This is content association, not a semantic proof or a provider playback-complete event.
        var sentences = java.text.BreakIterator.getSentenceInstance(Locale.ENGLISH);
        sentences.setText(expected); sentences.first(); sentences.next(); int end = sentences.next();
        if (end == java.text.BreakIterator.DONE || end >= expected.length()) return false;
        String opening = expected.substring(0, end).trim();
        if (opening.endsWith("?")) return false;
        String anchor = contentWords(normalize(opening));
        return anchor.split(" +").length >= 5 && ordered(anchor, contentWords(captured));
    }
    private static String contentWords(String text) {
        var functionWords = Set.of("a", "an", "the", "i", "ll", "will", "he", "she", "it", "his", "her", "its",
                "is", "was", "are", "were", "be", "been", "in", "on", "at", "to", "for", "of", "and", "or", "why", "did", "because");
        return String.join(" ", Arrays.stream(text.trim().split(" +")).filter(word -> !functionWords.contains(word)).toList());
    }
    private static boolean ordered(String expected, String captured) {
        if (expected.isEmpty()) return false;
        String[] words = expected.split(" +"); int next = 0;
        for (String word : captured.trim().split(" +")) if (word.equals(words[next]) && ++next == words.length) return true;
        return false;
    }
    private static Optional<TaskSpec.Rule> waitFor(String reason, UUID source) {
        ch.zhaw.prometheus.logging.ActivityTrace.cue(reason, source); return Optional.empty();
    }
    private static boolean stable(TaskSpec.Rule rule, List<Event> events, Instant after, Instant now, String[] reason) {
        int count = 0; var seen = new HashSet<Instant>(); Instant newer = null;
        for (int i = events.size() - 1; i >= 0; i--) {
            var event = events.get(i);
            if (!rule.eventType().equals(event.getType())) continue;
            var time = TaskMemory.observed(event);
            if (time == null || !time.isAfter(after) || !TaskMemory.fresh(event, now)) {
                reason[0] = "stale_or_missing_observation"; return false;
            }
            if (!matches(rule, event, reason)) return false;
            if (newer != null && time.isAfter(newer)) return false;
            newer = time;
            if (seen.add(time) && ++count >= rule.samples()) return true;
        }
        reason[0] = "insufficient_samples";
        return false;
    }
    private static boolean matches(TaskSpec.Rule rule, Event event, String[] reason) {
        try {
            var object = JsonParser.parseString(event.getPayload()).getAsJsonObject();
            if (object.has("facePresent") && !object.get("facePresent").getAsBoolean() && !rule.field().equals("facePresent")) { reason[0] = "face_missing"; return false; }
            double confidence = object.has("confidence") ? object.get("confidence").getAsDouble()
                    : object.has("avgDetectionConfidence") ? object.get("avgDetectionConfidence").getAsDouble() : 0;
            if (!Double.isFinite(confidence) || confidence < rule.minConfidence()) { reason[0] = "low_confidence"; return false; }
            JsonElement value = object;
            for (String part : rule.field().split("\\.")) value = value.isJsonArray()
                    ? value.getAsJsonArray().get(Integer.parseInt(part)) : value.getAsJsonObject().get(part);
            if (value == null || !value.isJsonPrimitive()) { reason[0] = "missing_field"; return false; }
            if (rule.operator().equals("eq")) {
                boolean match = value.equals(rule.value()); if (!match) reason[0] = "condition_not_met"; return match;
            }
            if (!value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble())) return false;
            boolean match = rule.operator().equals("lt") ? value.getAsDouble() < rule.value().getAsDouble() : value.getAsDouble() > rule.value().getAsDouble();
            if (!match) reason[0] = "condition_not_met"; return match;
        } catch (RuntimeException invalid) { reason[0] = "invalid_observation"; return false; }
    }
}
