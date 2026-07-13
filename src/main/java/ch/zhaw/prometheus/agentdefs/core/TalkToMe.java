package ch.zhaw.prometheus.agentdefs.core;

import java.util.List;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;

@Component
public class TalkToMe implements AgentDefinition {
    public static final String KEY = "core.talk_to_me";
    public static final String PROFILE_TAG = "utility.talk_to_me";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_ENGLISH;
    }

    @Override
    public Agent createAgent() {
        State state = new State("Talk to Me", new TalkToMePolicy(), List.of());
        state.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE), state));

        Agent agent = new Agent(
                "Talk to Me",
                "Speaks submitted text exactly through a PROMETHEUS Realtime session.",
                state);
        agent.setInteractionProfile(AgentInteractionProfile.of(
                List.of(AgentInteractionProfile.OBS_USER_UTTERANCE),
                List.of(AgentInteractionProfile.MODALITY_SPEECH),
                List.of(PROFILE_TAG)));
        return this.applyDefinitionMetadata(agent);
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
