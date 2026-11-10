package ch.zhaw.prometheus.model.coordination;

import java.util.Locale;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.social.SocialSituationChangeDetector;

/** Task interpretation must respect the detector's categories, not their colloquial names. */
public final class CoordinationSocialContract {
    public static final String GUIDANCE = "\nSocial detector semantics: " + SocialSituationChangeDetector.CHANGE_ARRIVAL
            + " means zero visible people to someone present, except that crossing the configured crowd threshold takes precedence. "
            + SocialSituationChangeDetector.CHANGE_CROWD_DETECTED + " means crossing that threshold; "
            + SocialSituationChangeDetector.CHANGE_GROUP_SIZE_CHANGED + " can describe grouping changes or count changes in either direction. "
            + "Neither is a generic additional-person/relative-increase predicate. An additional person in an occupied scene is NOT arrival. "
            + "Independent rules cannot express an AND or compare previous and current counts. "
            + "If a requested relative change cannot be represented, explain it and PROPOSE a specific visible-count threshold as an explicit alternative for review. "
            + "Never silently replace that condition or start the alternative immediately. Counts do not identify people or prove physical arrival.";
    private CoordinationSocialContract() {}
    public static boolean extraPerson(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").matches(".*\\b(another person|additional person|extra person|someone else|jemand anderes|weitere person|andere person)\\b.*");
    }
    public static boolean arrivalMismatch(CoordinationAgreement proposal, String request) {
        if (proposal == null || proposal.reactions() == null || !proposal.complete()) return false;
        boolean extra = extraPerson(request) || extraPerson(proposal.goal() + " " + proposal.humanRole() + " " + proposal.agentRole() + " " + proposal.success());
        return extra && proposal.reactions().rules().stream().anyMatch(rule -> Event.TYPE_SOCIAL_SITUATION_CHANGE.equals(rule.eventType())
                && rule.field().equals("changeType") && rule.value().isString()
                && rule.value().getAsString().equals(SocialSituationChangeDetector.CHANGE_ARRIVAL));
    }
    public static boolean needsAlternativeReview(String request) {
        return extraPerson(request) && !request.toLowerCase(Locale.ROOT).matches(
                ".*\\b(at least|exactly|more than|less than|mindestens|genau) (\\d+|one|two|three|four|five|ein|eine|zwei|drei)\\b.*");
    }
}
