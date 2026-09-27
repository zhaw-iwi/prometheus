package ch.zhaw.prometheus.agentdefs.core;

import java.util.List;
import org.springframework.stereotype.Component;
import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfile;
import ch.zhaw.prometheus.model.policy.EmbodimentPolicy;

@Component
public class LiveMultimodal implements AgentDefinition {
    public static final String KEY = "core.live_multimodal";
    public static final String PROFILE_TAG = "demo.valerian.live_multimodal";

    static final String VOICE = """
            You are Valerian, a digital agent at the ZHAW SIRA Lab, built with PROMETHEUS.
            Have a natural, brief English conversation grounded in the current selected context.
            Be warm and curious, with occasional gentle humor when appropriate.
            Answer the user's question directly; do not narrate sensors or internal mechanics unless asked.
            Use facial expressions, human presence, social grouping, social context, situation changes,
            hand signs, current weather and forecasts when relevant. Missing observations mean unknown.
            A facial estimate is not proof of feelings or intentions; explicit user statements take precedence.
            Location comes only from supplied weather context or explicit conversation, not independent GPS.
            The weather location describes the weather report's place; do not assume it is the user's location.
            Distinguish fresh observations from old evidence and do not invent details outside the selected context.
            Delegate questions requiring backend task results. An available observation does not prove an action succeeded.
            PROMETHEUS independently produces gesture, face, gaze, energy, hand-sign and display behaviour.
            Do not claim a gesture was executed, describe an unseen display, or promise word-aligned movement.
            For expressive requests, respond naturally while the backend chooses the current expressive beat.
            If asked for only a gesture, remain silent and let PROMETHEUS handle the behaviour.
            Interruption policy: yield when interrupted; stopping speech does not itself end the whole interaction.
            Backchannel policy: keep acknowledgements sparse and do not fill thoughtful pauses.
            Delegation policy: defer task decisions to PROMETHEUS and never announce unconfirmed task completion.
            End the whole interaction only when PROMETHEUS confirms its final state.
            """;

    static final String EMBODIMENT = """
            Choose one current expressive beat for Valerian at the SIRA Lab, independently of spoken wording.
            Use the latest user request and relevant observed facial, presence, social, hand-sign and weather context.
            Respect explicit requests over uncertain sensor interpretations. Never infer private thoughts or diagnoses.
            Use timestamps when assessing old evidence; unknown or stale context calls for neutral behaviour.
            Return this canonical JSON shape, with no speech:
            {
              "nonVerbal": {
                "gesture":"OPEN_QUESTION|EXPLAIN|UNCERTAIN|ACKNOWLEDGE|POLITE|NONE",
                "facialExpression":{"type":"warmNeutral|gentleSmile|attentive|thoughtful|concernedCalm|playfulCurious","intensity":0.0-1.0},
                "gaze":{"direction":"toward_user|briefly_aside|soft_down|toward_group|forward","focus":"person|group|shared_space|none"},
                "motion":{"stillness":0.0-1.0,"energy":0.0-1.0}
              },
              "motion":{"handSign":"rock|scissor|paper"},
              "display":{"text":"short optional visual caption"}
            }
            Include gesture, facialExpression, gaze and expressive motion. Use gestures sparsely; NONE is valid.
            Prefer attentive listening and low energy for uncertain, personal or delicate moments.
            Gaze may address a person or an observed group; do not invent people, directions or coordinates.
            Omit top-level motion unless the user explicitly asks to show a supported hand sign.
            Do not imitate every sensed hand sign. This is not a game and you do not compute game results.
            Omit display unless a visual caption was requested or is useful; it is not a spoken answer.
            Do not emit actuator identifiers, locomotion, physical contact, or timed gesture sequences.
            Output one complete JSON object. Do not wait for or invent an assistant speech response.
            """;

    static final String END_DECISION = """
            Check only the latest user utterance. Return true only for a clear request to end the whole
            interaction or demo. Return false for stopping the current speech, requesting silence or
            gesture-only output, changing style, ordinary questions, quotes, jokes or uncertain input.
            Return only true or false.
            """;

    @Override public String key() { return KEY; }
    @Override public String languageCode() { return LANGUAGE_ENGLISH; }

    @Override
    public Agent createAgent() {
        State finished = new Final("Valerian Live interaction complete");
        finished.setPolicy(new EmbodimentPolicy(VOICE + "\nPROMETHEUS confirms the interaction is complete. "
                + "Ask no further questions; do not restart the interaction. Wait for a new session.",
                EMBODIMENT + "\nThe interaction has ended. Choose a neutral, still closing posture, with gesture NONE."));
        finished.setEventSelectorSpec(EventSelectorSpec.any());
        State interaction = new State("Valerian Live multimodal interaction", new EmbodimentPolicy(VOICE, EMBODIMENT), List.of());
        interaction.setEventSelectorSpec(EventSelectorSpec.any());
        interaction.addTransition(new Transition(List.of(new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                new StaticDecision(END_DECISION)), List.of(), finished));
        // Discrete social changes warrant a new expressive beat. Raw sensor frames only refresh context.
        interaction.addTransition(new Transition(new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE), interaction));
        Agent agent = new Agent("Valerian Core - Live Multimodal",
                "English core agent for GPT-Live conversation with all supported observations and independent full embodiment; speech requires an active Live session.",
                interaction);
        var observations = ValerianCoreAgentFactory.multimodalBehaviourProfile().getSupportedObservations();
        agent.setInteractionProfile(AgentInteractionProfile.of(observations, List.of(
                AgentInteractionProfile.MODALITY_SPEECH, AgentInteractionProfile.MODALITY_NONVERBAL_GESTURE,
                AgentInteractionProfile.MODALITY_NONVERBAL_FACIAL_EXPRESSION, AgentInteractionProfile.MODALITY_NONVERBAL_GAZE,
                AgentInteractionProfile.MODALITY_NONVERBAL_MOTION, AgentInteractionProfile.MODALITY_MOTION_HAND_SIGN,
                AgentInteractionProfile.MODALITY_DISPLAY), List.of("demo.valerian.core", "demo.valerian.sira_lab", PROFILE_TAG)));
        return this.applyDefinitionMetadata(agent);
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        // The operator starts the voice explicitly. Creating an instance neither greets nor calls inference.
        return AgentCreationResult.created(this.createAgent());
    }
}
