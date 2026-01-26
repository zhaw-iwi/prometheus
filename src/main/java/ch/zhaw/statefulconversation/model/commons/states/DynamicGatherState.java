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
import ch.zhaw.statefulconversation.model.commons.decisions.DynamicDecision;
import jakarta.persistence.Entity;

@Entity
public class DynamicGatherState extends State {

        private static final String GATHER_PROMPT = "Ask the user to provide one value for each of the following slots: ";
        private static final String GATHER_STARTER_PROMPT = "Ask the user.";
        private static final String GATHER_TRIGGER = "Examine the following chat and decide if the user provides all values for the following slots: ";
        private static final String GATHER_ACTION = "Examine the following chat and extract each value for all of the following slots: ";
        protected DynamicGatherState() {

        }

        public DynamicGatherState(String name, State subsequentState, Storage storage, String storageKeyFrom,
                        String storageKeyTo) {
                this(name, subsequentState, storage, storageKeyFrom, storageKeyTo, true, false);
        }

        public DynamicGatherState(String name, State subsequentState, Storage storage, String storageKeyFrom,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptStateResponsePolicy(
                                                DynamicGatherState.GATHER_PROMPT + "${" + storageKeyFrom + "}",
                                                DynamicGatherState.GATHER_STARTER_PROMPT,
                                                PromptStateResponsePolicy.DEFAULT_SUMMARISE_PROMPT,
                                                storage,
                                                List.of(storageKeyFrom),
                                                PromptValueShape.ARRAY),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new DynamicDecision(DynamicGatherState.GATHER_TRIGGER + "${" + storageKeyFrom + "}",
                                storage, storageKeyFrom);
                Action action = new DynamicExtractionAction(
                                DynamicGatherState.GATHER_ACTION + "${" + storageKeyFrom + "}",
                                storage,
                                storageKeyFrom,
                                storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "DynamicGatherState IS-A " + super.toString();
        }
}
