package ch.zhaw.prometheus.model.hand;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

/** Bound new hand ingress before persistence; legacy event contracts are unchanged. */
public final class HandObservationAdmission {
    private HandObservationAdmission() {}
    public static void validate(String payload, List<Event> history, Instant receivedAt) {
        rate(HandSignObservation.parse(payload).identity(),history,receivedAt);
    }
    public static void validate(Event input, AgentInteractionProfile profile, List<Event> history, Instant receivedAt) {
        if (!Event.KIND_OBSERVATION.equals(input.getKind()) || !profile.supportsObservation(input.getType()))
            throw new IllegalArgumentException("This instance does not declare this hand input");
        if(Event.TYPE_HAND_POINTING.equals(input.getType())) {
            if(!HandFeatures.pointing(profile))throw new IllegalArgumentException("This instance does not declare pointing");
            var point=HandPointObservation.parse(input.getPayload());
            if(!HandFeatures.dynamicTargets(profile)&&(point.handOrigin()!=null||point.sourceAssociation()!=null))throw new IllegalArgumentException("Dynamic source geometry not declared");
            if(!HandFeatures.targets(profile)&&!"unresolved".equals(point.resolution().get("status").getAsString()))throw new IllegalArgumentException("Targets not declared");
            rate(point.identity(),history,receivedAt);
        } else if(Event.TYPE_HAND_SIGNS.equals(input.getType())) {
            var parsed=HandSignObservation.parse(input.getPayload());
            if(parsed.hands().stream().anyMatch(h -> h.sign()!=null&&!HandFeatures.signAllowed(h.sign(),HandFeatures.poses(profile),HandFeatures.geometry(profile))))
                throw new IllegalArgumentException("This instance does not declare derived hand signs");
            rate(parsed.identity(),history,receivedAt);
        } else if(HandFeatures.poses(profile)||HandEvidence.modernPose(input.getPayload())) {
            if(!HandFeatures.poses(profile))throw new IllegalArgumentException("This instance does not declare v2 poses");
            var pose=HandPoseObservation.parse(input.getPayload());
            if(!HandFeatures.geometry(profile)&&pose.frame().hands().asList().stream().anyMatch(h->h.getAsJsonObject().has("geometry")))
                throw new IllegalArgumentException("This instance does not declare hand geometry");
            rate(pose.frame().identity(),history,receivedAt);
        }
    }
    private static void rate(String incoming,List<Event> history,Instant receivedAt) {
        var ids=new HashSet<String>(); Instant cutoff = receivedAt.minusSeconds(1);
        for (int i = history.size() - 1; i >= 0; i--) {
            var event = history.get(i);
            if (!Event.TYPE_HAND_SIGNS.equals(event.getType()) && !Event.TYPE_HAND_POSE.equals(event.getType()) && !Event.TYPE_HAND_POINTING.equals(event.getType())) continue;
            if (event.getCreatedDate() == null) continue;
            if (!event.getCreatedDate().isAfter(cutoff)) break;
            try {String id=HandEvidence.identity(event);if(id!=null)ids.add(id);}
            catch(RuntimeException invalid) { /* Old unvalidated history is not new v2 evidence. */ }
        }
        if(ids.size()>=5&&!ids.contains(incoming))throw new IllegalArgumentException("Hand observation rate exceeds five frames per second");
    }
}
