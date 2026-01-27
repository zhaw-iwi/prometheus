package ch.zhaw.prometheus.model.commons.states;

import java.util.List;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import jakarta.persistence.Entity;

@Entity
public class GatherState extends State {

        private static final String GATHER_PROMPT = "Ask the user to provide one value for each of the following slots: ";
        private static final String GATHER_STARTER_PROMPT = "Ask the user.";
        private static final String GATHER_TRIGGER = "Examine the following chat and decide if the user provides all values for the following slots: ";
        private static final String GATHER_ACTION = "Examine the following chat and extract each value for all of the following slots: ";

        protected GatherState() {

        }

        public GatherState(String name, List<String> slots, State subsequentState, Storage storage,
                        String storageKeyTo) {
                this(name, slots, subsequentState, storage, storageKeyTo, true, false);
        }

        public GatherState(String name, List<String> slots, State subsequentState, Storage storage, String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptPolicy(
                                                new StringBuilder(GatherState.GATHER_PROMPT)
                                                                .append(String.join(", ", slots))
                                                                .toString(),
                                                GatherState.GATHER_STARTER_PROMPT,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new StaticDecision(
                                new StringBuilder(GatherState.GATHER_TRIGGER)
                                                .append(String.join(", ", slots))
                                                .toString());
                Action action = new StaticExtractionAction(
                                new StringBuilder(GatherState.GATHER_ACTION)
                                                .append(String.join(", ", slots))
                                                .toString(),
                                storage,
                                storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "GatherState IS-A " + super.toString();
        }
}
