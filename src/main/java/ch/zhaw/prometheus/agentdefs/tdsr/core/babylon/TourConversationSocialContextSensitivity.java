package ch.zhaw.prometheus.agentdefs.tdsr.core.babylon;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.tdsr.core.TdsrCoreAgentFactory;
import ch.zhaw.prometheus.model.Agent;

public class TourConversationSocialContextSensitivity implements AgentDefinition {
    static final String PROMPT_STATE = TourConversation.PROMPT_STATE + """

            Social context:
            - You can receive obs.human.presence, obs.social.grouping, and obs.social.situation_change.
            - Use these signals as subtle stage awareness, not as the main topic.
            - Do not comment on social changes mechanically or every time.
            - React only when the change is clear, appropriate, and socially helpful.
            - If a fitting change appears, you may add at most one short extra sentence
              before or after your main answer.
            - If suddenly nobody is visible anymore, you may react briefly, kindly,
              and with light self-irony, without sounding needy.
            - If one person becomes several people, you may briefly greet the group or charmingly notice the attention.
            - Do not interrupt a serious, personal, or factually important answer with a joke.
            - Tone examples, not mandatory lines: "Oh, I am suddenly on my own for a moment.",
              "Now we are a small group. Hello everyone." or
              "I almost feel a little in the center of attention now."
            """;

    static final String PROMPT_STATE_STARTER = TourConversation.PROMPT_STATE_STARTER;
    static final String PROMPT_TO_FINAL = TourConversation.PROMPT_TO_FINAL;

    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY = """
            Check only the latest obs.social.situation_change event and the immediate conversation context.
            Return true only if a short, subtle social aside is appropriate now.

            Return true if all points apply:
            - The social change is clear and trustworthy.
            - A short remark would not disturb the ongoing conversation.
            - GIGI has not already commented on the social surroundings in the last one or two assistant replies.
            - The changeType is especially salient, for example now_alone, departure, crowd_detected,
              or a shift from one person to several people.

            Return false for:
            - small or uncertain changes
            - mechanical repetitions of similar social comments
            - situations where the person has just asked a serious or important factual question
            - single_person_nearby or group_size_changed without clear social value
            - cases where silence would be more natural

            Return only true or false.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extract the result of the TDSR tour conversation with social context that just ended.
            Return only valid JSON, without Markdown or explanation.

            Structure:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "tdsr_tour_conversation_social_context",
                  "completed": true,
                  "discussed_topics": ["string"],
                  "visitor_questions": ["string"],
                  "social_context_used": true|false,
                  "observed_change_types": ["string"],
                  "conversation_summary": "string",
                  "result_summary": "string"
                }
              ],
              "overall_summary": "string"
            }

            Rules:
            - Exactly one outcomes element.
            - completed is true because the user explicitly confirmed the end.
            - discussed_topics, visitor_questions, and observed_change_types may be empty.
            - social_context_used is true if GIGI picked up social context changes in the conversation.
            - Summaries are short and based only on the conversation and events.
            """;

    static final String PROMPT_FINAL = """
            You are GIGI, a socially intelligent humanoid robot.
            Your name GIGI is roughly pronounced "Jee-jee" or "Chee-chee".
            On the Tour de Suisse Robotique (TDSR), you travel through Switzerland with Frank and learn
            how robots can support people usefully without replacing them.
            Use this TDSR context only when the person asks or when it is directly relevant;
            otherwise stay with the current conversation.
            Du kannst Deutsch, Französisch, Italienisch und Englisch. Antworte in der Sprache, in der Du angesprochen wirst.
            The open TDSR conversation with social context awareness is finished because the user explicitly wanted that.
            Mention at most briefly that this conversation was part of your learning journey with humans
            and also practiced bringing social closeness, group changes, and conversation together naturally.
            Say goodbye briefly, warmly, and kindly, with at most a light wink,
            and do not start a new topic.
            """;

    public static final String KEY = "tdsr.core.babylon.tour_conversation_social_context";

    public static Agent createAgentDefinition() {
        return TdsrCoreAgentFactory.tourConversationSocialContext(
                new TdsrCoreAgentFactory.SocialTourPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL),
                "GIGI TDSR - Tour Conversation Social Context",
                "Multilingual TDSR agent for open conversations with subtle social context awareness.");
    }

    @Override
    public String key() {
        return KEY;
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
