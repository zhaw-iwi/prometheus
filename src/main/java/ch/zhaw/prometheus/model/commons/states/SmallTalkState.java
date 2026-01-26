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
public class SmallTalkState extends State {

        private static final String SMALLTALK_PROMPT = "Führe Small Talk mit dem Benutzer. Begrüße ihn herzlich und frage, ob er geduzt oder gesiezt werden möchte. Erkundige dich, wie es ihm geht. Stelle immer nur eine Frage auf einmal und gehe auf seine Antworten ein, um eine angenehme Konversation aufzubauen und Nähe zu schaffen.";
        private static final String SMALLTALK_STARTER_PROMPT = "Starte nun das Gespräch mit deiner ersten Frage.";
        private static final String SMALLTALK_TRIGGER = "Analysiere die folgende Konversation und entscheide folgendes: \n - der User wurde gefragt, ob er geduzt oder gesiezt werden will \n - Der User wurde gefragt wie es ihm geht.\n Beide Bedingungen müssen erfüllt werden.";
        private static final String SMALLTALK_ACTION = "Extrahiere die Besprochenen Themen aus der geführten Konversation und erzeuge ein JSON objekt im Format {\"Thema1\": \"Zusammenfassung\", \"Thema2\": \"Zusammenfassung\", ...}";
        protected SmallTalkState() {

        }

        public SmallTalkState(String name, State subsequentState, Storage storage,
                        String storageKeyTo) {
                this(name, subsequentState, storage, storageKeyTo, true, false);
        }

        public SmallTalkState(String name, State subsequentState, Storage storage,
                        String storageKeyTo,
                        boolean isStarting,
                        boolean isOblivious) {
                super(name,
                                new PromptPolicy(SmallTalkState.SMALLTALK_PROMPT,
                                                SmallTalkState.SMALLTALK_STARTER_PROMPT,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(), isStarting, isOblivious);
                Decision trigger = new StaticDecision(SmallTalkState.SMALLTALK_TRIGGER);
                Action action = new StaticExtractionAction(SmallTalkState.SMALLTALK_ACTION,
                                storage,
                                storageKeyTo);
                Transition transition = new Transition(List.of(trigger), List.of(action), subsequentState);
                this.addTransition(transition);
        }

        @Override
        public String toString() {
                return "SmallTalkState IS-A " + super.toString();
        }
}
