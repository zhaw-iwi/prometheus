package ch.zhaw.statefulconversation.model.commons.states;

import java.util.List;
import ch.zhaw.statefulconversation.model.Action;
import ch.zhaw.statefulconversation.model.Decision;
import ch.zhaw.statefulconversation.model.PromptStateResponsePolicy;
import ch.zhaw.statefulconversation.model.PromptValueShape;
import ch.zhaw.statefulconversation.model.State;
import ch.zhaw.statefulconversation.model.Storage;
import ch.zhaw.statefulconversation.model.Transition;
import ch.zhaw.statefulconversation.model.commons.actions.DynamicExtractionAction;
import ch.zhaw.statefulconversation.model.commons.actions.DynamicRemoveTopicAction;
import ch.zhaw.statefulconversation.model.commons.decisions.DynamicDecision;
import jakarta.persistence.Entity;

@Entity
public class DynamicSingleChoiceStateShrinking extends State {

        private static final String SINGLECHOICE_PROMPT = "Ask the user to choose one item out of the following list of items: ";
        private static final String SINGLECHOICE_STARTER_PROMPT = "Ask the user.";
        private static final String SINGLECHOICE_TRIGGER = "Examine the following chat and decide if the user indicates one choice among the following choices: ";
        private static final String SINGLECHOICE_ACTION = "Examine the following chat and extract extract the one choice the user made among the following choices: ";
        protected DynamicSingleChoiceStateShrinking() {

        }

        public DynamicSingleChoiceStateShrinking(String name, State subsequentState, Storage storage,
                        String storageKeyFrom,
                        String storageKeyTo) {
                this(name, subsequentState, storage, storageKeyFrom, storageKeyTo, true, false);
        }

        public DynamicSingleChoiceStateShrinking(String name, State subsequentState, Storage storage,
                        String storageKeyFrom,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptStateResponsePolicy(
                                                DynamicSingleChoiceStateShrinking.SINGLECHOICE_PROMPT + "${"
                                                                + storageKeyFrom + "}",
                                                DynamicSingleChoiceStateShrinking.SINGLECHOICE_STARTER_PROMPT,
                                                PromptStateResponsePolicy.DEFAULT_SUMMARISE_PROMPT,
                                                storage,
                                                List.of(storageKeyFrom),
                                                PromptValueShape.ARRAY),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new DynamicDecision(
                                DynamicSingleChoiceStateShrinking.SINGLECHOICE_TRIGGER + "${" + storageKeyFrom + "}",
                                storage,
                                storageKeyFrom);
                Action action = new DynamicExtractionAction(
                                DynamicSingleChoiceStateShrinking.SINGLECHOICE_ACTION + "${" + storageKeyFrom + "}",
                                storage,
                                storageKeyFrom, storageKeyTo);
                Action removeAction = new DynamicRemoveTopicAction(
                                "",
                                storage,
                                storageKeyFrom, storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action, removeAction),
                                subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "DynamicSingleChoiceStateShrinking IS-A " + super.toString();
        }
}
