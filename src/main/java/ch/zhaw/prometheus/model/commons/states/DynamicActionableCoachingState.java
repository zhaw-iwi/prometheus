package ch.zhaw.prometheus.model.commons.states;

import java.util.List;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.actions.TransferEventHistoryAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import jakarta.persistence.Entity;

@Entity
public class DynamicActionableCoachingState extends State {

        private static final String PROMPT_BEFORE = "The patient has just revealed the following clues regarding a key health-related goal: ";
        private static final String PROMPT_AFTER = """
                        Guide the patient to define one practical health action for this week that aligns with this goal.
                        Help refine it until it is realistic, motivating, and SMART.
                        Offer support, examples, and obstacle planning.
                        Maintain an empathetic tone.
                        """;
        private static final String STARTER_PROMPT = "Generate a brief message that acknowledges the patient's newly identified health goal and invites them to explore a single practical action they can take this week to improve their well-being.";
        private static final String TRIGGER = """
                        Analyze the following conversation and determine if the patient has committed to a specific, realistic action to implement their newly identified health goal in their daily self-care or well-being routine.
                        Return \"true\" if such an action is clearly stated and the patient agrees to it. Otherwise, return \"false.\"
                        """;;
        private static final String ACTION = """
                        Analyze the following conversation and identify the specific health action the patient agreed to undertake, ensuring it reflects a SMART plan (Specific, Measurable, Achievable, Relevant, Time-bound).
                        Summarize the plan in JSON format with clear attributes, for example:
                        {
                                \"action\": {
                                        \"description\": \"..\",
                                        \"timeline\": \"...\",
                                        \"measure_of_success\": \"...\",
                                        \"relevance_to_health_goal\": \"...\",
                                        \"obstacles_or_support_needed\": \"...\"
                                }
                        }
                        """;
        protected DynamicActionableCoachingState() {

        }

        public DynamicActionableCoachingState(String name, State subsequentState,
                        Storage storage,
                        String storageKeyFrom,
                        String storageKeyTo) {
                this(name, subsequentState, storage, storageKeyFrom, storageKeyTo, true, false);
        }

        public DynamicActionableCoachingState(String name, State subsequentState,
                        Storage storage,
                        String storageKeyFrom,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptPolicy(
                                                DynamicActionableCoachingState.PROMPT_BEFORE + "${" + storageKeyFrom
                                                                + "}" + DynamicActionableCoachingState.PROMPT_AFTER,
                                                DynamicActionableCoachingState.STARTER_PROMPT,
                                                DynamicActionableCoachingState.ACTION,
                                                storage,
                                                List.of(storageKeyFrom),
                                                PromptValueShape.OBJECT),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new StaticDecision(DynamicActionableCoachingState.TRIGGER);
                Action action = new StaticExtractionAction(
                                DynamicActionableCoachingState.ACTION,
                                storage,
                                storageKeyTo);
                Transition transition = new Transition(List.of(trigger),
                                List.of(action, new TransferEventHistoryAction(subsequentState)), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "DynamicActionableCoachingState IS-A " + super.toString();
        }
}
