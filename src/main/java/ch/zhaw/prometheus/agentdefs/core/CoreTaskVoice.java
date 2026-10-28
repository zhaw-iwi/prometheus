package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.task.*;

/** Persisted confirmed facts, never provider-authored speech or authority to execute an action. */
final class CoreTaskVoice {
    static final String ENABLED = "core.task.liveWording", RESPONSE = "core.task.response", INVITATION = "core.task.invitation", SPOKEN_PROMPT = "core.task.spokenPrompt";
    static boolean enabled(Storage storage) { return storage.containsKey(ENABLED) && storage.get(ENABLED).getAsBoolean(); }
    private CoreTaskVoice() {}
    static void clear(Storage storage) { for (var key : List.of(RESPONSE, INVITATION, SPOKEN_PROMPT)) if (storage.containsKey(key)) storage.remove(key); }
    static boolean capturedPrompt(Storage storage, Event event, ExternalSpeech owner, Instant started) {
        return capturedPrompt(storage, event, owner, started, List.of());
    }
    static boolean capturedPrompt(Storage storage, Event event, ExternalSpeech owner, Instant started, List<LiveSpeechPart> parts) {
        return capturedPrompt(storage, event, owner, started, parts, TaskMemory.DRAFT);
    }
    static boolean capturedPrompt(Storage storage, Event event, ExternalSpeech owner, Instant started, List<LiveSpeechPart> parts, String draftKey) {
        if (!storage.containsKey(INVITATION) || !storage.containsKey(draftKey) || started == null
                || owner == null) return false;
        Instant boundary = Instant.parse(storage.get(INVITATION).getAsString());
        var p = event.speechProvenance();
        if (p == null || !p.complete() || p.origin() != SpeechProvenance.Origin.NATIVE || p.segmentId() == null
                || !owner.sessionId().equals(p.sessionId()) || !owner.epoch().equals(p.epoch())) return false;
        String speech = BehaviourPlan.fromJson(event.getPayload()).getSpeech();
        if (!parts.isEmpty()) {
            String raw = parts.stream().map(LiveSpeechPart::text).collect(java.util.stream.Collectors.joining());
            int question = finalSentenceStart(raw);
            int offset = 0; Instant questionAt = null;
            for (var part : parts) {
                if (offset + part.text().length() > question) { questionAt = part.receivedAt(); break; }
                offset += part.text().length();
            }
            if (questionAt == null || !questionAt.isAfter(boundary)) return false;
            speech = raw.substring(question);
        } else if (!started.isAfter(boundary)) return false;
        if (promptText(speech) == null) return false;
        storage.put(SPOKEN_PROMPT, new JsonPrimitive(p.segmentId())); return true;
    }
    private static int finalSentenceStart(String speech) {
        int end = speech.length() - 1;
        while (end >= 0 && (Character.isWhitespace(speech.charAt(end)) || ".!?".indexOf(speech.charAt(end)) >= 0)) end--;
        int start = Math.max(Math.max(speech.lastIndexOf('.', end), speech.lastIndexOf('!', end)), speech.lastIndexOf('?', end)) + 1;
        while (start < speech.length() && Character.isWhitespace(speech.charAt(start))) start++;
        return start;
    }
    private static String promptText(String speech) {
        if (speech == null || speech.isBlank()) return null;
        String prompt = speech.substring(finalSentenceStart(speech)).trim();
        return prompt.length() > 1 && prompt.length() <= 800 ? prompt : null;
    }
    static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", "").trim().replaceAll(" +", " "); }
    static boolean acknowledgement(String text) { return normalize(text).matches("please|thanks|thank you|great|nice|cool|got it|understood|all right|alright|bitte|danke|verstanden|alles klar"); }
    static void clearAnswered(Storage storage) {
        if (!Set.of("PENDING", "CHECKING").contains(TaskResponse.status(storage)) && storage.containsKey(RESPONSE)) storage.remove(RESPONSE);
    }
    static boolean assent(String text) { return normalize(text).matches("yes|yeah|yep|sure|ok|okay|yes please|ja|ja gerne|gern|gerne"); }
    static boolean deferredQuestion(String question) {
        String n = normalize(question);
        return !n.matches(".*\\b(now|jetzt)\\b.*")
                && n.matches(".*\\b(when|after|if|until|later|tomorrow|next|wenn|sobald|später|morgen)\\b.*");
    }
    /** Current completed utterance context only. Ordinary task control must interpret acceptance. */
    static String prompt(Storage storage, EventHistory history, ExternalSpeech owner) {
        return prompt(storage, history, owner, TaskMemory.DRAFT);
    }
    static String prompt(Storage storage, EventHistory history, ExternalSpeech owner, String draftKey) {
        if (!storage.containsKey(draftKey) || !storage.containsKey(INVITATION)) return null;
        Instant after = Instant.parse(storage.get(INVITATION).getAsString());
        var events = history.toList();
        for (int i = events.size() - 1; i >= 0; i--) {
            var event = events.get(i);
            if (Event.TYPE_USER_UTTERANCE.equals(event.getType())) {
                if (i == events.size() - 1) continue;
                return null;
            }
            if (!Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) continue;
            if (event.getCreatedDate() == null || event.getCreatedDate().isBefore(after)) return null;
            var p = event.speechProvenance();
            if (owner != null && (p == null || p.origin() != SpeechProvenance.Origin.NATIVE || !p.complete()
                    || !owner.sessionId().equals(p.sessionId()) || !owner.epoch().equals(p.epoch())
                    || !storage.containsKey(SPOKEN_PROMPT) || !storage.get(SPOKEN_PROMPT).getAsString().equals(p.segmentId()))) return null;
            String speech = BehaviourPlan.fromJson(event.getPayload()).getSpeech();
            if (speech == null) continue;
            return promptText(speech);
        }
        return null;
    }
    static String terms(TaskSpec task, boolean goal) {
        StringBuilder text = new StringBuilder(goal ? "Goal: " + task.goal() + ". " : "");
        text.append("Limit: ").append(task.maxActions()).append(" actions; first: ").append(task.firstAction())
            .append("; sessionBound: ").append(task.sessionBound()).append(". ");
        for (var r : task.rules()) text.append("On ").append(r.eventType()).append('.').append(r.field()).append(' ')
            .append(r.operator()).append(' ').append(r.value()).append("; confidence>=").append(r.minConfidence())
            .append("; samples=").append(r.samples()).append("; cooldown=").append(r.cooldownSeconds()).append("s: ")
            .append(r.effect()).append(' ').append(r.action()).append(". ");
        return text.toString();
    }
    static String facts(Storage storage, String response) {
        String prefix = "Confirmed task phase: " + TaskMemory.phase(storage) + "; actions: " + TaskMemory.text(storage, TaskMemory.ACTIONS, "0")
                + ". " + response + "\n";
        if ("PAUSED".equals(TaskMemory.phase(storage)) && storage.containsKey(TaskMemory.PAUSE_REASON))
            prefix += "Pause reason: " + TaskMemory.text(storage, TaskMemory.PAUSE_REASON, "") + ". Explicit resume required. ";
        String key = storage.containsKey(TaskMemory.DRAFT) ? TaskMemory.DRAFT : TaskMemory.SPEC;
        if (!storage.containsKey(key)) return prefix + "No agreement or proposal.";
        var task = TaskSpec.parse(storage.get(key));
        String label = key.equals(TaskMemory.DRAFT) ? "Proposed, NOT ACTIVE: " : "Agreed task: ";
        String full = prefix + label + terms(task, true);
        // Preserve every executable rule. Only the redundant purpose can be omitted at the byte boundary.
        if (bytes(full) > 4000) full = prefix + label + terms(task, false);
        if (bytes(full) > 4000) throw new IllegalArgumentException("Task terms exceed Live context budget");
        return full;
    }
    static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    static void response(Storage storage, String facts, boolean proactive, boolean gateNextCue, PolicyRuntime runtime) {
        response(storage, facts, proactive, gateNextCue, runtime, null);
    }
    static void response(Storage storage, String facts, boolean proactive, boolean gateNextCue, PolicyRuntime runtime, String confirmedContext) {
        if (runtime.externalSpeech() == null) return;
        var value = new JsonObject(); var id = UUID.randomUUID(); Instant now = Instant.now();
        value.addProperty("id", id.toString()); value.addProperty("facts", facts);
        value.addProperty("proactive", proactive); value.addProperty("expires", now.plusSeconds(45).toString());
        value.addProperty("revision", TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        storage.put(RESPONSE, value);
        if (TaskMemory.active(storage) && gateNextCue) TaskResponse.expect(storage, runtime.externalSpeech(), id,
                confirmedContext == null ? facts(storage, facts) : confirmedContext, now);
        else if (!TaskMemory.active(storage) && storage.containsKey(TaskResponse.KEY)) storage.remove(TaskResponse.KEY);
    }
    static LiveActivityContext context(Storage storage) {
        var now = Instant.now(); var value = storage.containsKey(RESPONSE) ? storage.get(RESPONSE).getAsJsonObject() : null;
        boolean current = value != null && value.get("revision").getAsString().equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        String detail = current ? value.get("facts").getAsString() : "No new task action is authorized. Answer ordinary questions directly.";
        if (current && !now.isBefore(Instant.parse(value.get("expires").getAsString())))
            detail = "Historical update, do not repeat its action: " + detail;
        var context = facts(storage, detail);
        if (!current || !now.isBefore(Instant.parse(value.get("expires").getAsString()))) return new LiveActivityContext(context, null);
        Instant expiry = Instant.parse(value.get("expires").getAsString());
        return new LiveActivityContext(context, new LiveActivityContext.Continuation(UUID.fromString(value.get("id").getAsString()),
                value.get("proactive").getAsBoolean() ? LiveActivityContext.Occasion.PROACTIVE : LiveActivityContext.Occasion.USER_REQUEST,
                "A confirmed task update is ready. Respond once from the current task facts in your own words.", expiry), expiry);
    }
}
