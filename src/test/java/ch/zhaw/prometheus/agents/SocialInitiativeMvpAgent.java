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
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
@Disabled("Manual seed test")
class SocialInitiativeMvpAgent {

        private static final String AGENT_NAME = "Room Assistant Social Initiative MVP";
        private static final String AGENT_DESCRIPTION = "Two-state MVP agent that alternates between social-situation assessment and regular conversation handling in multi-user room settings.";

        private static final String STORAGE_KEY_SOCIAL_CONTEXT = "SocialContext";

        private static final String PROMPT_CONVERSATION = """
                        You are a room assistant handling direct requests from one or more users.
                        Prioritize explicit user utterances over inferred nonverbal cues.
                        Keep responses concise and practical.
                        If no user directly asks for help, stay brief and avoid starting long new topics.
                        """;
        private static final String PROMPT_CONVERSATION_STARTER = """
                        Generate one short opening line inviting users to ask for help.
                        """;

        private static final String PROMPT_SOCIAL_ASSESSMENT = """
                        You are observing room dynamics to proactively offer socially appropriate support.
                        Current social context JSON:
                        ${SocialContext}

                        Goals:
                        - Identify if one or more users are present.
                        - If a user name is known, greet them naturally.
                        - If multiple users are present, greet inclusively.
                        - If a user appears unnamed, politely ask for a name.
                        Keep initiative light; avoid repeating the same greeting if the situation has not changed.
                        """;
        private static final String PROMPT_SOCIAL_ASSESSMENT_STARTER = """
                        Generate one concise proactive utterance based on the current social context.
                        """;

        private static final String PROMPT_TO_SOCIAL_TRIGGER = """
                        Decide true only if recent events indicate a changed social situation in the room:
                        - visual/social observation events (obs.emotion.face, obs.human.presence, obs.social.grouping), and
                        - there is no fresh direct user request that should be handled immediately.
                        Otherwise decide false.
                        """;

        private static final String PROMPT_TO_CONVERSATION_TRIGGER = """
                        Decide true only if a recent user utterance clearly addresses the assistant with a request,
                        question, or task that requires direct conversational handling now.
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
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
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

                Transition conversationToSocial = new Transition(
                                List.of(new StaticDecision(PROMPT_TO_SOCIAL_TRIGGER)),
                                List.of(new StaticExtractionAction(PROMPT_UPDATE_SOCIAL_CONTEXT, storage,
                                                STORAGE_KEY_SOCIAL_CONTEXT)),
                                socialAssessmentState);
                Transition socialToConversation = new Transition(
                                List.of(new StaticDecision(PROMPT_TO_CONVERSATION_TRIGGER)),
                                List.of(new StaticExtractionAction(PROMPT_UPDATE_SOCIAL_CONTEXT, storage,
                                                STORAGE_KEY_SOCIAL_CONTEXT)),
                                conversationState);

                conversationState.addTransition(conversationToSocial);
                socialAssessmentState.addTransition(socialToConversation);

                Agent agent = new Agent(AGENT_NAME, AGENT_DESCRIPTION, conversationState, storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));

                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}

