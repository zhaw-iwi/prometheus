package ch.zhaw.prometheus.model.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;

/** One rule for prompt composition and projected client history. Raw history remains the audit. */
public final class ConversationProjection {
    private ConversationProjection() {}
    public static boolean isIntent(Event event) {
        return event != null && event.speechProvenance() != null
                && event.speechProvenance().origin() == SpeechProvenance.Origin.BACKEND_INTENT;
    }
    public static String speech(Event event) {
        if (event == null || isIntent(event) || !Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(event.getType())) return null;
        try { var plan = BehaviourPlan.fromJson(event.getPayload()); return plan == null ? null : plan.getSpeech(); }
        catch (RuntimeException invalid) { return null; }
    }
    public record View(UUID id, String type, String actor, String kind, String payload, Instant createdDate,
            List<String> statePath, SpeechProvenance provenance, String plannedSpeech) {}
    public static View view(Event event) {
        String payload = event.getPayload(), planned = null;
        if (isIntent(event)) {
            var plan = BehaviourPlan.fromJson(payload);
            if (plan != null) { planned = plan.getSpeech(); plan.setSpeech(null); payload = plan.toJson(); }
        }
        return new View(event.getId(), event.getType(), event.getActor(), event.getKind(), payload,
                event.getCreatedDate(), event.getStatePath(), event.speechProvenance(), planned);
    }
}
