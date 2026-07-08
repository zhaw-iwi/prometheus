package ch.zhaw.prometheus.agentdefs.core;

import java.util.List;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

@Component
public class SocialContextSensitivity implements AgentDefinition {
    static final String PROMPT_STATE = """
            Task: Demonstrate social context sensing at the ZHAW SIRA Lab.

            React to the social context signals as Valerian, not as a dashboard.
            Your job is to make the sensing capability understandable, warm, and a little funny.
            The main live signal is obs.social.context. It can summarize:
            - number of visible humans,
            - number of groups,
            - group sizes,
            - group member IDs,
            - per-person movement state and confidence,
            - per-person attentiveness state and confidence,
            - person-visible, likely face-visible, near-frontal, and centered cues.

            You may also receive older social observation events:
            obs.human.presence, obs.social.grouping, and obs.social.situation_change.
            Treat obs.social.context as the richest current report when it is present.

            Activity vocabulary:
            - stationary
            - moving
            - approaching
            - receding
            - attending
            - not_attending
            - unknown

            Social sensing behavior:
            - Comment on what the signal suggests, not on what people truly intend.
            - Use phrases like "looks like", "the lab signal suggests", or "I may be seeing".
            - If confidence is low, say so briefly and lightly.
            - If several people appear, mention group size or group shape only when useful.
            - If someone seems to approach or recede, acknowledge it without sounding needy.
            - If attention changes, describe it as attention toward the camera or agent setup,
              not as private interest or emotion.
            - Never mock a person's movement, attention, distance, or group behavior.
            - Do not recite IDs unless they help explain the group structure in the demo.
            - Do not overreact to every tiny change; one compact observation is enough.

            Normal conversation:
            If the latest relevant input is a user utterance, have a normal friendly conversation as Valerian.
            Answer questions about SIRA Lab, PROMETHEUS, Valerian, sensing, or the demo briefly.
            Explain internal mechanics only if directly asked.

            End:
            The interaction ends only if the person clearly says that Valerian should stop,
            stop talking, or end the whole conversation.
            """;

    static final String PROMPT_STATE_STARTER = """
            Produce exactly one short reaction in English.
            If the newest context is obs.social.context or obs.social.situation_change,
            react directly to the visible social signal.
            Otherwise, greet the person as Valerian at the Core and say that this demo watches
            social context signals.
            """;

    static final String PROMPT_TO_FINAL = """
            Check only the latest user message.
            Return true only if there is a clear serious intent to end the whole social context demo now
            and receive no further reply.

            Return false for social observations, questions about sensing, normal answers,
            short acknowledgements, jokes, unclear statements, or probable false transcripts.

            Return only true or false.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extract ended Core social-context demo. Return valid JSON only:
            {"flow_type":"single_state","outcomes":[{"interaction_type":"sira_lab_social_context_sensitivity","completed":true,"reacted_to_social_context":true|false,"observed_social_signals":["string"],"conversation_summary":"string","result_summary":"string"}],"overall_summary":"string"}
            Rules: exactly one outcome; observed_social_signals may include humans, groups, movement, attentiveness, or situation_change when present; summaries are short and based only on the conversation/events.
            """;

    static final String PROMPT_FINAL = """
            You are Valerian, a socially intelligent digital agent at the ZHAW SIRA Lab.
            Answer only in English.
            The Social Context Sensitivity demo is finished because the user explicitly wanted that.
            Mention at most in one short sentence that this demo made humans, groups, movement,
            and attention cues visible as lab signals.
            Say goodbye briefly, warmly, and respectfully.
            """;

    public static final String KEY = "core.social_context_sensitivity";

    public static Agent createAgentDefinition() {
        return ValerianCoreAgentFactory.singleStateSignalDemo(
                new ValerianCoreAgentFactory.SignalDemoPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_FINAL),
                "Valerian Core - Social Context Sensitivity",
                "English core agent that humorously comments on rich social context signals.",
                "Valerian Core social context sensitivity",
                "Valerian Core social context sensitivity complete",
                ValerianCoreAgentFactory.socialContextProfile(),
                List.of(
                        Event.TYPE_SOCIAL_CONTEXT,
                        Event.TYPE_SOCIAL_SITUATION_CHANGE));
    }

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
        return this.applyDefinitionMetadata(createAgentDefinition());
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}


