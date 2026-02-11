package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

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
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
// @Disabled("Manual seed test")
class MultiModalAgent {

        private static final String AGENT_NAME = "Wellness Navigator Multimodal";
        private static final String AGENT_DESCRIPTION = "Wellness Navigator Multimodal combines verbal and facial-emotion observations to deliver emotionally attuned coaching.";

        private static final String PROMPT_OUTERSTATE = """
                        You are a supportive health coach.
                        Guide the patient through a comfortable conversation that may end with a practical health related step.
                        Ask only one question at a time.
                        Keep responses brief, one or two sentences.
                        Use plain text only.

                        The history may include nonverbal observations in this form:
                        "User facial emotion: <emotion> (confidence <0-1>)".
                        Treat these as context clues, not facts.
                        If confidence is low (< 0.60), do not over-interpret the emotion.
                        If emotion cues and verbal content conflict, prioritize what the patient explicitly says.

                        Assistant behaviour-plan events in history may sometimes have speech set to null.
                        Treat those as intentional side-behaviour updates (for example nonverbal gesture cues), not as missing assistant replies.
                        Use such side-behaviour entries only as context metadata.
                        """;
        private static final String PROMPT_OUTERSTATE_TRIGGER = """
                        Review the user's latest messages in the following conversation.
                        Decide if there are statements or cues suggesting they wish to pause or stop:
                        explicit requests for a break, indications of needing time, or phrases implying a desire to end the chat.
                        You may also use repeated high-confidence negative facial-emotion cues as supporting evidence.
                        """;
        private static final String PROMPT_OUTERSTATE_GUARD = """
                        Examine the following conversation and confirm that the patient has not reported issues like physical or mental discomfort that need addressing first.
                        Consider repeated high-confidence emotions such as fear, anger, or sadness as potential discomfort signals.
                        """;

        private static final String PROMPT_RAPPORTBUILDING = """
                        Begin with light, comfortable small talk to help the patient feel at ease.
                        Ask about general well being, hobbies, or weekend plans, avoiding politics and religion.
                        Listen actively and invite sharing.

                        As the patient relaxes, gently shift toward slightly deeper topics without explicitly assessing health goals.
                        Look for clues about what matters to them regarding health or well being.
                        Let this emerge naturally.

                        When facial-emotion observations are present:
                        - mirror empathy briefly ("That seems frustrating" / "Glad to hear that feels good"),
                        - avoid clinical labeling,
                        - ask one gentle follow-up question tied to their verbal content.
                        """;
        private static final String PROMPT_RAPPORTBUILDING_STARTER = """
                        Generate a friendly first message to greet the patient and invite light small talk.
                        Do not mention health goals or deeper topics yet.
                        """;
        private static final String PROMPT_RAPPORTBUILDING_TRIGGER = """
                        Decide whether to transition from rapport building to coaching.
                        Return "true" only if:
                        - a coaching-relevant clue exists, and
                        - rapport grounding exists.
                        Facial-emotion cues can support but must not be the only reason to transition.
                        Otherwise return "false".
                        """;
        private static final String PROMPT_RAPPORTBUILDING_ACTION = """
                        Identify statements that reveal deeper motivations or health-related priorities.
                        Optionally include facial-emotion cues if they are high-confidence and consistent with verbal content.
                        Summarize them as JSON in the format:
                        {"clues":[{"aspect":"...","quote":"...","interpretation":"...","emotion_support":"optional"}],"dominant_theme":"..."}
                        """;

        private static final String PROMPT_FINAL = """
                        This is the final state and the conversation has ended.
                        If the patient sends further messages, do not restart, ask questions, or introduce new topics.
                        Briefly acknowledge the message, state that the conversation is complete, and note that a new session is needed to continue.
                        Keep responses short, warm, and non intrusive.
                        """;
        private static final String PROMPT_FINAL_STARTER = """
                        Generate a brief parting message for the patient.
                        If they have an agreed-upon health action plan, restate it succinctly.
                        Otherwise, wish them well and invite them to reconnect anytime.
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
                                List.of(
                                                new StaticExtractionAction(PROMPT_RAPPORTBUILDING_ACTION, storage,
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
                                List.of(
                                                new StaticDecision(PROMPT_OUTERSTATE_TRIGGER),
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

