package ch.zhaw.prometheus.model.coordination;

import java.time.Instant;
import java.util.Locale;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.task.TaskMemory;
import ch.zhaw.prometheus.model.policy.*;
import com.google.gson.JsonObject;

/** Authoritative explanation, independent of task permission or model availability. */
public final class CoordinationSupport {
    public static final String LIVE_HELP = "coordination.pendingHelp";
    public static final String GUIDANCE = "\nWhen the user is struggling, confused or reports unexpected behaviour, use operation SUPPORT with agreement:null. "
            + "The backend supplies the truthful explanation. Do not infer start/restart consent from a complaint. "
            + "Explicit pause/stop/resume/restart requests still use their guarded operations.";
    private CoordinationSupport() {}
    public static boolean requested(String text) {
        String value = text.toLowerCase(Locale.ROOT).replace('’', '\'');
        // A targeted direct-request fallback, not a sentiment classifier. Other wording uses normal inference.
        if (value.matches(".*\\b(don't|do not|not|kein|keine|nicht) (need |want |brauche )?help\\b.*")) return false;
        return value.matches(".*\\b(help me|i need help|i'm stuck|i am stuck|i don't understand|i do not understand|"
                + "this isn't working|this is not working|something went wrong|why did that fail|what went wrong|"
                + "ich verstehe (das )?nicht|ich brauche hilfe|das funktioniert nicht|hilf mir)\\b.*")
                || value.strip().matches("(please )?help[.!?]?");
    }

    public static String explain(Storage storage, Instant now) {
        String stage = CoordinationMemory.stage(storage);
        String next = switch (stage) {
            case "CONVERSATION" -> "No task is active. Tell me your goal; we can agree a small supported task.";
            case "COORDINATION" -> {
                var draft = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT);
                yield "The proposal still has unresolved terms. " + (draft.questions().isEmpty()
                        ? "We need the goal, roles and success criterion before starting." : draft.questions().getFirst());
            }
            case "PREPARATION" -> {
                var draft = CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT);
                var evidence = CoordinationMemory.evidence(storage);
                var missing = java.util.stream.IntStream.range(0, draft.checks().size())
                        .filter(i -> !evidence.has(Integer.toString(i)))
                        .mapToObj(i -> draft.checks().get(i).value().toString()).toList();
                yield "The proposal is not active. Remaining recognition checks: " + missing
                        + ". Demonstrate those observations; recognition alone will not start the task.";
            }
            case "READY" -> "The proposal and preparation are ready; no further demonstration is needed. Say Start to accept the saved proposal."
                    + (CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT).reactions() != null
                    && CoordinationMemory.agreement(storage, CoordinationMemory.DRAFT).reactions().firstAction()
                        == ch.zhaw.prometheus.model.task.TaskSpec.FirstAction.ON_CUE
                    ? " After acceptance, show a fresh matching cue." : "");
            case "EXECUTION" -> "The accepted task is active. We can review its terms; say Pause or Stop if you want to suspend it."
                    + " Asking for help does not pause independent sensor reactions.";
            case "PAUSED" -> "The task is paused and its progress is preserved. Say Resume to continue, or Stop to end it.";
            case "FINISHING" -> "The final contribution is awaiting speech association or playback confirmation. No further task action is authorized."
                    + " Wait for delivery to settle; do not repeat it automatically.";
            case "UNCERTAIN" -> "Final response delivery is uncertain. The spent contribution is preserved and will not be repeated automatically."
                    + " We can reflect, return to conversation or explicitly restart with a fresh budget.";
            default -> "The task has ended. Its recorded outcome is " + TaskMemory.text(storage, CoordinationMemory.OUTCOME, "not established")
                    + ". We can reflect, revise, return to conversation or explicitly restart; output is not proof of success.";
        };
        var failure = CoordinationRecovery.current(storage, now);
        String reason = failure == null ? "" : switch (failure.get("category").getAsString()) {
            case "protected_terms" -> "it changed protected agreement terms";
            case "operation_constraints" -> "the requested operation was not permitted in the current state";
            case "update_shape", "agreement_shape", "reply_shape", "speech_intent_length" -> "the generated update did not meet the response requirements";
            default -> "I could not obtain a usable interpretation; the underlying cause is not established";
        };
        String diagnostic = failure == null ? "" : " The last update was not applied because " + reason
                + ". The task agreement and preparation were preserved.";
        return "Current coordination state: " + stage + ". " + next + diagnostic;
    }

    public static void pending(Storage storage, ExternalSpeech owner, Instant now, String explanation) {
        var value = new JsonObject(); value.addProperty("session", owner.sessionId().toString());
        value.addProperty("epoch", owner.epoch().toString()); value.addProperty("at", now.toString());
        value.addProperty("revision", TaskMemory.text(storage, TaskMemory.REVISION, "0"));
        // Timestamped request-time evidence stays historical if task delivery delays the answer.
        if ((explanation.startsWith("When you asked,") || explanation.startsWith("No ") || explanation.startsWith("The supplied "))
                && explanation.length() <= 1000) value.addProperty("requestEvidence", explanation);
        storage.put(LIVE_HELP, value);
    }

    /** Quiet facts only: never replace the pending response identity, occasion or playback cue. */
    public static LiveActivityContext withHelp(Storage storage, LiveActivityContext context, Instant now) {
        if (!storage.containsKey(LIVE_HELP)) return context;
        try {
            var value = storage.get(LIVE_HELP).getAsJsonObject();
            if (!now.isBefore(Instant.parse(value.get("at").getAsString()).plusSeconds(30))
                    || !value.get("revision").getAsString().equals(TaskMemory.text(storage, TaskMemory.REVISION, "0"))) return context;
            String help = "\nThe user requested help. " + explain(storage, now)
                    + (value.has("requestEvidence") ? " Request-time perception facts (not proof of current input): " + value.get("requestEvidence").getAsString() : "")
                    + " This explanation is not the pending task contribution; do not report it as task delivery or repeat the task.";
            String facts = context.facts() + help;
            if (facts.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4000) return context;
            return new LiveActivityContext(facts, context.continuation(), context.refreshAt(), context.playbackCue());
        } catch (RuntimeException invalid) { return context; }
    }
}
