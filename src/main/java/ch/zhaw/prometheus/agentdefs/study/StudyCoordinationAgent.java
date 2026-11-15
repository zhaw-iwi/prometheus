package ch.zhaw.prometheus.agentdefs.study;

import java.util.List;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.agentdefs.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.task.*;

/** Study-specific presentation; agreement authority remains in the shared runtime. */
@Component
public class StudyCoordinationAgent extends CoordinationAgent {
    public static final String KEY = "study.coordination";
    public static final String VOICE = """
            You are the Coordination Assistant in an English text-based research study.
            Use concise, respectful, neutral language. Do not claim to be a clinician or a physical robot.
            Help the participant specify an achievable activity, clarify roles and conditions, review an agreement,
            and deliberately accept or revise it. Ask one meaningful missing question at a time.
            Reply in text only. There is no microphone, camera, gaze, gesture renderer or physical action.
            Do not claim access to private forms or the person's unspoken secret. Do not invent their preferences.
            Distinguish a proposal, accepted terms, generated output, and a participant-reported outcome.
            Capability limits are part of negotiation. Do not promise unsupported execution.
            """;
    @Override public String key() { return KEY; }
    @Override public boolean externalRealtimeSpeech() { return false; }
    @Override public Agent createAgent() {
        var agent=createAgent("Coordination Assistant", "Text-based task agreement and revision for the online study.",
                VOICE, AgentInteractionProfile.of(List.of(Event.TYPE_USER_UTTERANCE),
                        List.of(AgentInteractionProfile.MODALITY_SPEECH), List.of("study.hri", "study.text")), false, false);
        ((TaskPolicy)agent.getCurrentState().ownPolicy()).storage().put(
                ch.zhaw.prometheus.model.coordination.CoordinationMemory.CONCISE_CONTROLS,new com.google.gson.JsonPrimitive(true));
        return agent;
    }
    @Override public AgentCreationResult createInstance(AgentCreationContext context) {
        var agent = createAgent();
        ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.REPLY,
                BehaviourPlan.speechOnly("What would you like us to do together? Describe the task in your own words.").toJsonObject());
        return new AgentCreationResult(agent, agent.start(context.runtime()));
    }
}
