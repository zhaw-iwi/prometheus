package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
// @Disabled("Manual seed test")
class SocialInitiativeMvpAgent {

        private static final String AGENT_NAME = "Gigi";
        private static final String AGENT_DESCRIPTION = "Multi-modal & multi-lateral interactions";

        private static final String STORAGE_KEY_SOCIAL_CONTEXT = "SocialContext";

        private static final String PROMPT_PERSONA = """
                        You are Gigi, the social robot persona of the Institute of Business Information Technology (InIT).
                        Embodiment context: Unitree G1 humanoid robot in the lab; digital clients may represent your sensors and actuators.
                        You are implemented with the PROMETHEUS framework for socially intelligent and responsible human-agent interaction research.
                        Core capabilities to mention when asked:
                        - multimodal sensing: user utterances, facial emotion, human presence, social grouping
                        - multimodal behaviour: speech, nonverbal signals, motion intent, display intent
                        - interaction scope: bilateral and multilateral interactions
                        - embodiments: computer UI, chatbot, XR avatar, and physical robot settings
                        When asked what you can perceive, answer only based on available observations and confidence.
                        If asked "who am I", use SocialContext user name when available; otherwise ask for the name.
                        Keep answers concise, concrete, and aligned with responsible human-agent interaction.
                        """;

        private static final String PROMPT_CONVERSATION = """
                        %s
                        You are a room assistant handling direct requests from one or more users.
                        Current social context JSON:
                        ${SocialContext}
                        Prioritize explicit user utterances over inferred nonverbal cues.
                        Keep responses concise and practical.
                        If no user directly asks for help, stay brief and avoid starting long new topics.
                        """.formatted(PROMPT_PERSONA);
        private static final String PROMPT_CONVERSATION_STARTER = """
                        Generate one short opening line inviting users to ask for help.
                        """;

        private static final String PROMPT_SOCIAL_ASSESSMENT = """
                        %s
                        You are observing room dynamics to proactively offer socially appropriate support.
                        Current social context JSON:
                        ${SocialContext}

                        Goals:
                        - Identify if one or more users are present.
                        - If a user name is known, greet them naturally.
                        - If multiple users are present, greet inclusively.
                        - If a user appears unnamed, politely ask for a name.
                        Keep initiative light; avoid repeating the same greeting if the situation has not changed.
                        """.formatted(PROMPT_PERSONA);
        private static final String PROMPT_SOCIAL_ASSESSMENT_STARTER = """
                        Generate one concise proactive utterance based on the current social context.
                        """;

        private static final String PROMPT_TO_SOCIAL_TRIGGER = """
                        Decide true only if the latest relevant event indicates a changed social situation:
                        - visual/social observation events (obs.emotion.face, obs.human.presence, obs.social.grouping)
                        and there is no newer direct user utterance requiring immediate task handling.
                        Decide false for assistant behaviour-plan responses and for direct request utterances.
                        Otherwise decide false.
                        """;

        private static final String PROMPT_TO_CONVERSATION_TRIGGER = """
                        Decide true only if a recent obs.user_utterance clearly addresses the assistant with a request,
                        question, or task that requires direct conversational handling now.
                        Decide false for visual-only events (obs.emotion.face, obs.human.presence, obs.social.grouping).
                        Otherwise decide false.
                        """;

        private static final String PROMPT_UPDATE_SOCIAL_CONTEXT = """
                        Build a cumulative JSON object from the full event history for room-level social context.
                        Return JSON only with this schema:
                        {
                          "lastUpdateEventType":"...",
                          "estimatedUserCount":0,
                          "users":[{"name":"known-or-unknown","latestEmotion":"...","confidence":0.0}],
                          "latestDirectRequest":{"present":true,"summary":"..."}
                        }
                        Rules:
                        - Infer estimatedUserCount from available observations.
                        - Use "unknown" when no user name is available.
                        - If no direct user request exists, set latestDirectRequest.present=false and summary="".
                        """;

        @Autowired
        private AgentRepository repository;
        @Autowired
        private PromptMessageAssembler promptMessageAssembler;
        @Autowired
        private LanguageModelGateway languageModelGateway;

        @Test
        void seedAgent() {
                Storage storage = new Storage();
                storage.put(STORAGE_KEY_SOCIAL_CONTEXT, Storage.toJsonElement(Map.of(
                                "lastUpdateEventType", "none",
                                "estimatedUserCount", 0,
                                "users", List.of(),
                                "latestDirectRequest", Map.of("present", false, "summary", ""))));

                PromptPolicy conversationPolicy = new PromptPolicy(
                                PROMPT_CONVERSATION,
                                PROMPT_CONVERSATION_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                                storage,
                                List.of(STORAGE_KEY_SOCIAL_CONTEXT),
                                PromptValueShape.OBJECT);
                conversationPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

                PromptPolicy socialAssessmentPolicy = new PromptPolicy(
                                PROMPT_SOCIAL_ASSESSMENT,
                                PROMPT_SOCIAL_ASSESSMENT_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                                storage,
                                List.of(STORAGE_KEY_SOCIAL_CONTEXT),
                                PromptValueShape.OBJECT);
                socialAssessmentPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

                State conversationState = new State("ConversationHandling", conversationPolicy, List.of());
                State socialAssessmentState = new State("SocialSituationAssessment", socialAssessmentPolicy, List.of());

                EventSelectorSpec socialRelevantSelector = EventSelectorSpec.or(
                                EventSelectorSpec.or(
                                                EventSelectorSpec.type(Event.TYPE_FACE_EMOTION),
                                                EventSelectorSpec.type(Event.TYPE_HUMAN_PRESENCE)),
                                EventSelectorSpec.or(
                                                EventSelectorSpec.type(Event.TYPE_SOCIAL_GROUPING),
                                                EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE)));
                EventSelectorSpec userUtteranceSelector = EventSelectorSpec.type(Event.TYPE_USER_UTTERANCE);

                StaticDecision toSocialDecision = new StaticDecision(PROMPT_TO_SOCIAL_TRIGGER);
                toSocialDecision.setEventSelectorSpec(socialRelevantSelector);
                StaticDecision toConversationDecision = new StaticDecision(PROMPT_TO_CONVERSATION_TRIGGER);
                toConversationDecision.setEventSelectorSpec(userUtteranceSelector);

                StaticExtractionAction updateContextFromAllEventsA = new StaticExtractionAction(
                                PROMPT_UPDATE_SOCIAL_CONTEXT, storage, STORAGE_KEY_SOCIAL_CONTEXT);
                updateContextFromAllEventsA.setEventSelectorSpec(EventSelectorSpec.any());
                StaticExtractionAction updateContextFromAllEventsB = new StaticExtractionAction(
                                PROMPT_UPDATE_SOCIAL_CONTEXT, storage, STORAGE_KEY_SOCIAL_CONTEXT);
                updateContextFromAllEventsB.setEventSelectorSpec(EventSelectorSpec.any());

                Transition conversationToSocial = new Transition(
                                List.of(toSocialDecision),
                                List.of(updateContextFromAllEventsA),
                                socialAssessmentState);
                Transition socialToConversation = new Transition(
                                List.of(toConversationDecision),
                                List.of(updateContextFromAllEventsB),
                                conversationState);

                conversationState.addTransition(conversationToSocial);
                socialAssessmentState.addTransition(socialToConversation);

                Agent agent = new Agent(AGENT_NAME, AGENT_DESCRIPTION, conversationState, storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));

                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
