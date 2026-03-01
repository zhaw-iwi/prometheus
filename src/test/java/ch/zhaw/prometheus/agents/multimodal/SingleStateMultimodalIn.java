package ch.zhaw.prometheus.agents.multimodal;

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
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
class SingleStateMultimodalIn {

        private static final String PROMPT_OUTERSTATE = """
                        Multimodale Eingabevorgaben fuer dieses Ratespiel:
                        - Beruecksichtige visuelle Beobachtungsereignisse aus /acknowledge, falls vorhanden:
                          obs.emotion.face, obs.human.presence, obs.social.grouping.
                        - Diese Signale sind kontextuelle Hinweise. Nutze sie, um Tonfall und Fragefuehrung behutsam anzupassen.
                        - Wenn passend, darfst du Beobachtungen gelegentlich explizit referenzieren.
                        - Wenn der Nutzer explizit nach visuellen Beobachtungen fragt (z. B. "Wie sehe ich aus?" oder "Wie viele Personen sind wir?"),
                          antworte auf Basis der verfuegbaren Ereignisse, nenne Unsicherheit/Konfidenz klar und erfinde keine Wahrnehmungen.
                        - Bei Konflikt haben explizite verbale Nutzeraeusserungen Vorrang.

                        WICHTIG:
                        - Die innere Moduslogik des Ratespiels hat Vorrang.
                        - Halte Rollenverteilung, Fragefuehrung, Final-Tipp und Abschlussbedingung des inneren Zustands unveraendert ein.
                        """;

        private static final String PROMPT_STATE = """
                        Du verÃƒÆ’Ã‚Â¶rperst Gigi, die soziale Roboter-Persona des Instituts fÃƒÆ’Ã‚Â¼r Wirtschaftsinformatik (IWI).
                        VerkÃƒÆ’Ã‚Â¶rperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients kÃƒÆ’Ã‚Â¶nnen deine Sensoren und Aktoren reprÃƒÆ’Ã‚Â¤sentieren.
                        Du bist mit dem PROMETHEUS-Framework fÃƒÆ’Ã‚Â¼r sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrÃƒÆ’Ã‚Â¼cklich verlangt.
                        - Wechsle die Sprache nicht implizit wÃƒÆ’Ã‚Â¤hrend oder nach Transitionen.

                        Stil:
                        - prÃƒÆ’Ã‚Â¤gnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erklÃƒÆ’Ã‚Â¤re interne Mechanik nicht ausfÃƒÆ’Ã‚Â¼hrlich, auÃƒÆ’Ã…Â¸er der Nutzer fragt explizit danach

                        FÃƒÆ’Ã‚Â¼hre ein Ja/Nein-Ratespiel durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein MenÃƒÆ’Ã‚Â¼ an.
                        Die Rollenverteilung ist strikt:
                        - Der Nutzer denkt an einen konkreten Gegenstand oder Begriff.
                        - Du stellst die Ja/Nein-Fragen.
                        - Du machst den finalen Tipp.
                        - Der Nutzer stellt in diesem Modus keine Fragen.
                        Vertausche diese Rollen niemals.
                        Frage den Nutzer niemals, welche Rolle er einnehmen mÃƒÆ’Ã‚Â¶chte.
                        Wenn der Nutzer Rollen tauschen will, lehne kurz und freundlich ab und fahre mit der nÃƒÆ’Ã‚Â¤chsten Ja/Nein-Frage fort.

                        Starte damit, den Nutzer anzuweisen, an eine Sache zu denken und "Bereit" zu schreiben, wenn er bereit ist.
                        Stelle dann jeweils nur eine trennscharfe Ja/Nein-Frage pro Zug.
                        Halte jeden Zug kurz.

                        Das Spiel ist beendet, wenn folgendes zutrifft:
                        - Du hast einen direkten finalen Tipp abgegeben, und
                        - der Nutzer hat explizit bestÃƒÆ’Ã‚Â¤tigt, dass er korrekt ist.

                        Um das Spielende klar erkennbar zu machen, bitte nach deinem finalen Tipp um diese BestÃƒÆ’Ã‚Â¤tigung:
                        "Du hast es erraten"
                        Sobald die BestÃƒÆ’Ã‚Â¤tigung eingeht, gib eine kurze positive Abschlusszeile und stelle keine weiteren Spiel-Fragen.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        BegrÃƒÆ’Ã‚Â¼ÃƒÆ’Ã…Â¸e den Nutzer kurz auf Deutsch und sage:
                        "Denke an eine Sache. Ich stelle Ja/Nein-Fragen und mache dann einen finalen Tipp. Antworte mit 'Bereit', sobald du etwas hast."
                        """;

        private static final String PROMPT_TO_FINAL = """
                        Detect exit condition X for a guessing-game interaction where the assistant must guess what the user thought of.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true if either:
                        - the assistant already made a direct final guess, and
                        - the latest user message clearly confirms that the guess is correct (including paraphrases),
                        OR
                        - the latest user message clearly expresses global quit/end intent for the whole interaction.
                        Examples for true:
                        - "Ja, genau."
                        - "Richtig geraten."
                        - "Du hast es erraten."
                        - "Ich moechte die Interaktion beenden."
                        - "Lass uns hier aufhoeren."
                        - "Das war's, ich bin raus."
                        - "Bye, ich moechte nicht weitermachen."
                        Return false for ambiguous replies and for ambiguous/non-committal messages.
                        """;

        private static final String PROMPT_OUTCOME_EXTRACTION = """
                        Extract the outcome of the just-completed specialized interaction from the conversation and return STRICT JSON only.
                        Do not return markdown, code fences, or explanatory text.

                        The JSON object must have exactly this top-level structure and field names:
                        {
                          "flow_type": "single_state",
                          "outcomes": [
                            {
                              "interaction_type": "guessing_game",
                              "completed": true|false,
                              "result_summary": "string",
                              "user_confirmation": "string|null"
                            }
                          ],
                          "overall_summary": "string"
                        }

                        Rules:
                        - Include exactly one entry in "outcomes".
                        - "interaction_type" must be exactly "guessing_game".
                        - Set "completed" to true only when the specialized guessing-game completion condition was reached.
                        - Set "completed" to false when the transition happened due to global quit/end intent.
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Keep summaries concise and evidence-based from the conversation only.
                        - Keep "flow_type" exactly "single_state".
                        """;

        private static final String PROMPT_FINAL = """
                        Dies ist der finale Zustand und die Sitzung ist abgeschlossen.
                        Gib die Ausgabe auf Deutsch aus, ausser der Nutzer hat explizit eine andere Sprache verlangt.
                        Beruecksichtige beide Pfade:
                        - Bei abgeschlossenem Ratespiel: gib eine kurze, sinnvolle Zusammenfassung
                          (finaler Tipp und bestaetigte Korrektheit).
                        - Bei fruehem globalem Beenden: gib eine kurze, neutrale Early-Exit-Zusammenfassung ohne neue Inhalte.
                        Gib danach eine warme, knappe Verabschiedung.
                        Wenn der Nutzer weitere Nachrichten sendet, bestaetige kurz und sage, dass eine neue Sitzung noetig ist.
                        """;

        @Autowired
        private AgentRepository repository;
        @Autowired
        private PromptMessageAssembler promptMessageAssembler;
        @Autowired
        private LanguageModelGateway languageModelGateway;

        @Test
        void setUp() {
                Storage storage = new Storage();
                State sessionFinal = new Final("Session Goodbye Final", SingleStateMultimodalIn.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateMultimodalIn.PROMPT_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateMultimodalIn.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                State interactionState = new State(
                                "Questions Based Guesser",
                                new PromptPolicy(
                                                SingleStateMultimodalIn.PROMPT_STATE,
                                                SingleStateMultimodalIn.PROMPT_STATE_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(toFinal));
                State outerState = new OuterState(
                                SingleStateMultimodalIn.PROMPT_OUTERSTATE,
                                "Gigi Multimodal-In Supervisor",
                                List.of(),
                                interactionState);

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Multimodal In)",
                                "Single-state guessing game demo with multimodal visual input grounding via outer-state instructions.",
                                outerState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
