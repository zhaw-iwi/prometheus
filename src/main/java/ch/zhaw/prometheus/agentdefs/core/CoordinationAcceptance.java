package ch.zhaw.prometheus.agentdefs.core;

import java.time.Instant;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;

/** Coordination-only natural commands; short assent still needs a recent completed invitation. */
final class CoordinationAcceptance {
    static final long INVITATION_SECONDS = 120;
    private CoordinationAcceptance() {}
    static boolean assent(String text) {
        if (text.contains("?") || text.stripLeading().matches("[\"'“‘].*")) return false;
        return CoreTaskVoice.assent(text) || CoreTaskVoice.normalize(text).matches("ready|bereit|ich bin bereit");
    }
    static String invitation(Storage storage, EventHistory events, ExternalSpeech owner, Instant now) {
        if (!storage.containsKey(CoreTaskVoice.INVITATION)) return null;
        try {
            var at = Instant.parse(storage.get(CoreTaskVoice.INVITATION).getAsString());
            if (at.isAfter(now) || !now.isBefore(at.plusSeconds(INVITATION_SECONDS))) return null;
            return CoreTaskVoice.prompt(storage, events, owner, CoordinationMemory.DRAFT);
        } catch (RuntimeException invalid) { return null; }
    }
    static boolean explicit(String text) {
        if (text.contains("?") || text.stripLeading().matches("[\"'“‘].*")) return false;
        return CoreTaskVoice.normalize(text).matches("(yes |okay |ok )?(please )?"
                + "((lets|let) start( it| the task| the saved proposal)?( now)?|start( it| the task| the saved proposal)?( now)?|"
                + "run (it|the task|the saved proposal)|i accept( the (current|saved) ?proposal)?|accept the (current|saved) ?proposal)"
                + "|(ja )?(bitte )?(jetzt )?(starten|starten wir|lass uns starten|los gehts)( jetzt)?");
    }
    static boolean incomplete(String text) {
        return CoreTaskVoice.normalize(text).matches("(yes |okay |ok )?(i accept|accept) the( current| saved)?");
    }
    static final String REPAIR = "The proposal is still inactive. If you accept it, please say 'Start now'.";
    static void repair(Storage storage, ExternalSpeech owner) {
        var prior = storage.get(CoreTaskVoice.RESPONSE);
        if (prior != null && prior.getAsJsonObject().has("consentRepair")) return;
        if (prior != null && prior.getAsJsonObject().has("proposalReview") && !prior.getAsJsonObject().has("consumed")) {
            var review = prior.getAsJsonObject().deepCopy();
            review.addProperty("repairAfterReview", true);
            storage.put(CoreTaskVoice.RESPONSE, review);
            return;
        }
        CoreTaskVoice.response(storage, REPAIR, false, false, owner, null);
        var response = storage.get(CoreTaskVoice.RESPONSE).getAsJsonObject().deepCopy();
        response.addProperty("supportOnly", true);
        response.addProperty("consentRepair", true);
        storage.put(CoreTaskVoice.RESPONSE, response);
    }
    static boolean uncertainStart(String text) {
        return text.contains("?") || text.stripLeading().matches("[\"'“‘].*")
                || CoreTaskVoice.normalize(text).matches(".*\\b(not|dont|never|tomorrow|later|after|until|if|when|nicht|morgen|später|wenn|sobald)\\b.*");
    }
    static boolean withholdsStart(String text) {
        return CoreTaskVoice.normalize(text).matches(".*\\b(dont|do not|never)(?: \\w+){0,3} (start|begin|activate|run|restart)\\b.*"
                + "|.*\\bnicht (starten|beginnen|anfangen)\\b.*");
    }
}
