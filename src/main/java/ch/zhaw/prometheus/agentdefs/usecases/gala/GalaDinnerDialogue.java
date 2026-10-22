package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import java.time.Instant;
import java.util.Locale;
import ch.zhaw.prometheus.model.Storage;

/** A short-lived conversational hint, never an activity or a source of sensory facts. */
final class GalaDinnerDialogue {
    static final String FACE_TOPIC_AT = "gala.face_topic_at";
    private GalaDinnerDialogue() {}
    static void user(Storage storage, String utterance, Instant now) {
        if (GalaDinnerInput.contextualFollowUp(utterance) || GalaDinnerInput.acknowledgement(utterance)) return;
        String text = utterance.toLowerCase(Locale.ROOT);
        boolean question = text.matches("(?s).*(?:\\?|\\b(?:wie|was|kannst|siehst|wirke|sehe|how|what|can|do)\\b).*");
        boolean face = text.matches("(?s).*\\b(?:gesichtsausdruck\\p{L}*|mimik|gesicht|aussehe|wirke ich|face|facial|expression|do i look)\\b.*");
        if (question && face) put(storage, FACE_TOPIC_AT, now.toString());
        else remove(storage, FACE_TOPIC_AT);
    }
    static String facts(Storage storage, Instant now) {
        return refreshAt(storage, now) != null
                ? " Follow-up topic: facial expression. Reassess only from current reliable evidence."
                : " Follow-up topic: current user conversation, NOT facial expression unless explicitly asked again. Sensor updates never set the topic.";
    }
    static Instant refreshAt(Storage storage, Instant now) {
        if (!storage.containsKey(FACE_TOPIC_AT)) return null;
        Instant expires = time(storage, FACE_TOPIC_AT).plusSeconds(30);
        return now.isBefore(expires) ? expires : null;
    }
}
