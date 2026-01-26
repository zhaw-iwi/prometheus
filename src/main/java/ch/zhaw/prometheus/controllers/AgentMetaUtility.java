package ch.zhaw.prometheus.controllers;

import java.util.List;

import ch.zhaw.prometheus.controllers.dto.SingleStateAgentCreateDTO;
import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.Policy;
import ch.zhaw.prometheus.model.PromptPolicy;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;

public class AgentMetaUtility {

        public static Agent createSingleStateAgent(SingleStateAgentCreateDTO data) {
                var storage = new Storage();

                Decision trigger = new StaticDecision(data.getTriggerToFinalPrompt());
                Decision guard = new StaticDecision(data.getGuardToFinalPrompt());
                Action action = new StaticExtractionAction(data.getActionToFinalPrompt(), storage, "summary");
                Transition transition = new Transition(List.of(trigger, guard), List.of(action),
                                new Final("User Exit Final"));

                Policy policy = new PromptPolicy(data.getStatePrompt(),
                                data.getStateStarterPrompt(), PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
                State state = new State(data.getStateName(), policy, List.of(transition));

                Agent result = new Agent(data.getAgentName(), data.getAgentDescription(), state, storage);
                result.start();

                return result;
        }
}
