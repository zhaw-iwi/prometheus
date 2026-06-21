package ch.zhaw.prometheus.agentdefs.tdsr.shhd;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.tdsr.core.TdsrCoreAgentFactory;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

public final class TdsrShhdAgentFactory {
    private TdsrShhdAgentFactory() {
    }

    public record ShhdPrompts(String state, String starter, String toFinal, String outcomeExtraction,
            String socialInterjectionOpportunity, String finalPrompt) {
    }

    public static Agent socialTourAgent(ShhdPrompts prompts, String agentName, String agentDescription,
            String stateName, String finalStateName, String outcomeStorageKey) {
        Storage storage = new Storage();
        State sessionFinal = new Final(finalStateName, prompts.finalPrompt());

        PromptPolicy interactionPolicy = new PromptPolicy(
                prompts.state(),
                prompts.starter(),
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        interactionPolicy.setNonVerbalPlanPrompt(TdsrCoreAgentFactory.TOUR_NONVERBAL_PLAN);
        interactionPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

        State interactionState = new State(stateName, interactionPolicy, List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(prompts.toFinal())),
                List.of(
                        new StaticExtractionAction(
                                prompts.outcomeExtraction(),
                                storage,
                                outcomeStorageKey)),
                sessionFinal);
        Transition reactToSocialContextChange = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                        new StaticDecision(prompts.socialInterjectionOpportunity())),
                List.of(),
                interactionState);
        interactionState.addTransition(toFinal);
        interactionState.addTransition(reactToSocialContextChange);

        Agent agent = new Agent(agentName, agentDescription, interactionState, storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrTourConversationSocialContextSensitivity());
        return agent;
    }
}
