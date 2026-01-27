package ch.zhaw.prometheus.model.commons.states;

import java.util.List;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.policy.PromptValueShape;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.DynamicExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.DynamicDecision;
import jakarta.persistence.Entity;

@Entity
public class DynamicSingleChoiceState extends State {

        private static final String SINGLECHOICE_PROMPT = "Ask the user to choose one item out of the following list of items: ";
        private static final String SINGLECHOICE_STARTER_PROMPT = "Ask the user.";
        private static final String SINGLECHOICE_TRIGGER = "Examine the following chat and decide if the user indicates one choice among the following choices: ";
        private static final String SINGLECHOICE_ACTION = "Examine the following chat and extract extract the one choice the user made among the following choices: ";
        protected DynamicSingleChoiceState() {

        }

        public DynamicSingleChoiceState(String name, State subsequentState, Storage storage, String storageKeyFrom,
                        String storageKeyTo) {
                this(name, subsequentState, storage, storageKeyFrom, storageKeyTo, true, false);
        }

        public DynamicSingleChoiceState(String name, State subsequentState, Storage storage, String storageKeyFrom,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptPolicy(
                                                DynamicSingleChoiceState.SINGLECHOICE_PROMPT + "${" + storageKeyFrom
                                                                + "}",
                                                DynamicSingleChoiceState.SINGLECHOICE_STARTER_PROMPT,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT,
                                                storage,
                                                List.of(storageKeyFrom),
                                                PromptValueShape.ARRAY),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new DynamicDecision(
                                DynamicSingleChoiceState.SINGLECHOICE_TRIGGER + "${" + storageKeyFrom + "}", storage,
                                storageKeyFrom);
                Action action = new DynamicExtractionAction(
                                DynamicSingleChoiceState.SINGLECHOICE_ACTION + "${" + storageKeyFrom + "}", storage,
                                storageKeyFrom, storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "DynamicSingleChoiceState IS-A " + super.toString();
        }
}
