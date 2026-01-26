package ch.zhaw.prometheus.model.commons.states;

import java.util.List;

import ch.zhaw.prometheus.model.Action;
import ch.zhaw.prometheus.model.Decision;
import ch.zhaw.prometheus.model.PromptPolicy;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import jakarta.persistence.Entity;

@Entity
public class SingleChoiceState extends State {

        private static final String SINGLECHOICE_PROMPT = "Ask the user to choose one item out of the following list of items: ";
        private static final String SINGLECHOICE_STARTER_PROMPT = "Ask the user.";
        private static final String SINGLECHOICE_TRIGGER = "Examine the following chat and decide if the user indicates one choice among the following choices: ";
        private static final String SINGLECHOICE_ACTION = "Examine the following chat and extract extract the one choice the user made among the following choices: ";
        protected SingleChoiceState() {

        }

        public SingleChoiceState(String name, List<String> choices, State subsequentState, Storage storage,
                        String storageKeyTo) {
                this(name, choices, subsequentState, storage, storageKeyTo, true, false);
        }

        public SingleChoiceState(String name, List<String> choices, State subsequentState, Storage storage,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptPolicy(
                                                new StringBuilder(SingleChoiceState.SINGLECHOICE_PROMPT)
                                                                .append(String.join(", ", choices))
                                                                .toString(),
                                                SingleChoiceState.SINGLECHOICE_STARTER_PROMPT,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new StaticDecision(
                                new StringBuilder(SingleChoiceState.SINGLECHOICE_TRIGGER)
                                                .append(String.join(", ", choices))
                                                .toString());
                Action action = new StaticExtractionAction(
                                new StringBuilder(SingleChoiceState.SINGLECHOICE_ACTION)
                                                .append(String.join(", ", choices))
                                                .toString(),
                                storage,
                                storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "SingleChoiceState IS-A " + super.toString();
        }
}
