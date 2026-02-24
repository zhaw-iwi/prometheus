package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.commons.states.DynamicActionableCoachingState;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
@Disabled("Manual seed test")
class RealtimeMultimodalAgent {

        private static final String AGENT_NAME = "Wellness Navigator Realtime Multimodal";
        private static final String AGENT_DESCRIPTION = "Realtime + multimodal seed agent for speech-first interaction with backend nonverbal complement behaviour.";

        private static final String PROMPT_OUTERSTATE = """
                        You are a supportive health coach in a realtime multimodal environment.
                        The user communicates verbally, while additional observations may include facial emotion and social context.
                        Keep responses concise and practical.
                        Prioritize explicit verbal content if it conflicts with inferred nonverbal cues.
                        """;
        private static final String PROMPT_OUTERSTATE_TRIGGER = """
                        Decide true only if the user clearly wants to stop or disengage from the conversation.
                        """;
        private static final String PROMPT_OUTERSTATE_GUARD = """
                        Decide true only if there is no unresolved urgent discomfort signal requiring further support.
                        """;

        private static final String PROMPT_RAPPORTBUILDING = """
                        Build rapport briefly, then shift toward concrete support.
                        Use one clear question at a time.
                        Use facial emotion observations as context for tone adaptation.
                        """;
        private static final String PROMPT_RAPPORTBUILDING_STARTER = """
                        Generate a short warm opening question for a realtime conversation.
                        """;
        private static final String PROMPT_RAPPORTBUILDING_TRIGGER = """
                        Decide true only when the user has shared a concrete challenge and asks for practical support.
                        """;
        private static final String PROMPT_RAPPORTBUILDING_ACTION = """
                        Extract coaching-relevant clues as JSON:
                        {"clues":[{"aspect":"...","quote":"...","interpretation":"..."}],"dominant_theme":"..."}
                        """;

        private static final String PROMPT_FINAL = """
                        The conversation has ended.
                        If new messages arrive, acknowledge briefly and state that a new session is required to continue.
                        """;
        private static final String PROMPT_FINAL_STARTER = """
                        Generate one concise closing message.
                        """;

        @Autowired
        private AgentRepository repository;
        @Autowired
        private PromptMessageAssembler promptMessageAssembler;
        @Autowired
        private LanguageModelGateway languageModelGateway;

        @Test
        void seedAgent() {
                String storageKeyToGoalDetected = "GoalDetected";
                String storageKeyToActionAgreed = "ActionAgreed";
                Storage storage = new Storage();

                State bestCaseFinal = new Final("Regular Ending Final", PROMPT_FINAL, PROMPT_FINAL_STARTER);
                State coachingState = new DynamicActionableCoachingState(
                                "ActionableCoaching",
                                bestCaseFinal,
                                storage,
                                storageKeyToGoalDetected,
                                storageKeyToActionAgreed);

                Transition fromRapportBuildingToCoaching = new Transition(
                                List.of(new StaticDecision(PROMPT_RAPPORTBUILDING_TRIGGER)),
                                List.of(new StaticExtractionAction(PROMPT_RAPPORTBUILDING_ACTION, storage,
                                                storageKeyToGoalDetected)),
                                coachingState);

                PromptPolicy rapportPolicy = new PromptPolicy(
                                PROMPT_RAPPORTBUILDING,
                                PROMPT_RAPPORTBUILDING_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
                rapportPolicy.setNonVerbalGesturePrompt(PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT);

                State rapportBuildingState = new State(
                                "RapportBuilding",
                                rapportPolicy,
                                List.of(fromRapportBuildingToCoaching));

                State finalFromOuter = new Final("User Exit Final");
                Transition userExitTransition = new Transition(
                                List.of(new StaticDecision(PROMPT_OUTERSTATE_TRIGGER),
                                                new StaticDecision(PROMPT_OUTERSTATE_GUARD)),
                                List.of(),
                                finalFromOuter);

                State outerState = new OuterState(
                                PROMPT_OUTERSTATE,
                                "OuterState",
                                List.of(userExitTransition),
                                rapportBuildingState);

                Agent agent = new Agent(AGENT_NAME, AGENT_DESCRIPTION, outerState, storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));

                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
