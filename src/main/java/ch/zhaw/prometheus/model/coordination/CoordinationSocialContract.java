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
    public static String interpretationGuidance(String request) {
        if (!needsAlternativeReview(request)) return "";
        return "\nFor a requested new or replacement sensor task about an additional person in an occupied scene, do NOT compile arrival. "
                + "This guidance does not authorize a proposal for an ordinary question or replace an already-reviewed draft on acceptance. "
                + "When negotiating that new task, PROPOSE an alternative for review: at least two people visible. Use "
                + Event.TYPE_HUMAN_PRESENCE + ", field humanCount, "
                + "operator gt, numeric value 1, minConfidence 0, samples 1, cooldownSeconds 3. "
                + "Keep the requested response and action limit. The goal must explicitly call this a visible-count alternative, "
                + "not promise to detect a new person joining. This snapshot condition also matches an already occupied scene; "
                + "it does not detect a relative increase. Do not start until the person accepts the alternative.";
    }
    public static String proposalCondition(CoordinationAgreement proposal) {
        if (proposal == null || proposal.reactions() == null) return "";
        var conditions = proposal.reactions().rules().stream()
                .filter(rule -> "humanCount".equals(rule.field()) || "currentHumanCount".equals(rule.field()))
                .map(rule -> (switch (rule.operator()) { case "gt" -> "more than "; case "lt" -> "fewer than "; default -> "exactly "; })
                        + rule.value().getAsString() + (rule.value().getAsDouble() == 1 ? " person visible" : " people visible")).distinct().toList();
        return conditions.isEmpty() ? "" : " Visible-person conditions: " + String.join("; ", conditions)
                + ". These check the supplied count, not a new arrival or a person's identity.";
    }
    /** Essential executable terms travel with the invitation, not only as background JSON. */
    public static String liveProposalReview(CoordinationAgreement proposal) {
        String condition = proposalCondition(proposal);
        if (condition.isEmpty()) return "";
        return "Proposed count alternative." + condition
                + " Limit: " + proposal.reactions().maxActions() + " action(s) in total. Awaiting acceptance, not active. "
                + (proposal.reactions().firstAction() == ch.zhaw.prometheus.model.task.TaskSpec.FirstAction.ON_CUE
                    ? "After acceptance, a fresh matching cue is required. " : "Acceptance starts the agreed immediate first action. ")
                + "Do you accept these terms?";
    }
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
