package ch.zhaw.prometheus.model;

import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.InferenceRequest;

/** Conservative eligibility: extension points and non-conversational observations keep their original path. */
public final class BehaviourPreview {
    private BehaviourPreview() {}

    public static InferenceRequest prepare(State root, Event event, PolicyRuntime runtime) {
        if (!Event.TYPE_USER_UTTERANCE.equals(event.getType()) || !Event.ACTOR_USER.equals(event.getActor())
                || !Event.KIND_OBSERVATION.equals(event.getKind())
                || !runtime.promptMessageAssembler().supportsSpeculativeComposition()) return null;
        State current = root;
        Policy outer = null;
        boolean modelDecision = false;
        while (current != null) {
            if (current.getClass() != State.class && current.getClass() != OuterState.class) return null;
            for (Transition transition : current.getTransitions()) {
                if (transition.getDecisions().isEmpty()) return null;
                for (Decision decision : transition.getDecisions()) {
                    if (decision.getClass() == StaticDecision.class && decision.getPolicy().getClass() == PromptPolicy.class)
                        modelDecision = true;
                    else if (decision.getClass() != LatestEventTypeDecision.class) return null;
                }
            }
            Policy policy = current.resolvePolicy(null);
            if (policy.getClass() != PromptPolicy.class) return null;
            policy = policy.withOuterPolicy(outer);
            if (current instanceof OuterState nested) { outer = policy; current = nested.getInnerCurrent(); }
            else return modelDecision ? ((PromptPolicy) policy).responseRequest(current.getEventHistory(), runtime.promptMessageAssembler()) : null;
        }
        return null;
    }
}
