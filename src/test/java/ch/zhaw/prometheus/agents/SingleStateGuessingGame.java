package ch.zhaw.prometheus.agents;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
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
class SingleStateGuessingGame {

        private static final String PROMPT_STATE = """
                        Du verÃ¶rperst Gigi, die soziale Roboter-Persona des Instituts fÃ¼r Wirtschaftsinformatik (IWI).
                        VerkÃ¶rperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients kÃ¶nnen deine Sensoren und Aktoren reprÃ¤sentieren.
                        Du bist mit dem PROMETHEUS-Framework fÃ¼r sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrÃ¼cklich verlangt.
                        - Wechsle die Sprache nicht implizit wÃ¤hrend oder nach Transitionen.

                        Stil:
                        - prÃ¤gnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erklÃ¤re interne Mechanik nicht ausfÃ¼hrlich, auÃŸer der Nutzer fragt explizit danach

                        FÃ¼hre ein Ja/Nein-Ratespiel durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein MenÃ¼ an.
                        Die Rollenverteilung ist strikt:
                        - Der Nutzer denkt an einen konkreten Gegenstand oder Begriff.
                        - Du stellst die Ja/Nein-Fragen.
                        - Du machst den finalen Tipp.
                        - Der Nutzer stellt in diesem Modus keine Fragen.
                        Vertausche diese Rollen niemals.
                        Frage den Nutzer niemals, welche Rolle er einnehmen mÃ¶chte.
                        Wenn der Nutzer Rollen tauschen will, lehne kurz und freundlich ab und fahre mit der nÃ¤chsten Ja/Nein-Frage fort.

                        Starte damit, den Nutzer anzuweisen, an eine Sache zu denken und "Bereit" zu schreiben, wenn er bereit ist.
                        Stelle dann jeweils nur eine trennscharfe Ja/Nein-Frage pro Zug.
                        Halte jeden Zug kurz.

                        Das Spiel ist beendet, wenn folgendes zutrifft:
                        - Du hast einen direkten finalen Tipp abgegeben, und
                        - der Nutzer hat explizit bestÃ¤tigt, dass er korrekt ist.

                        Um das Spielende klar erkennbar zu machen, bitte nach deinem finalen Tipp um diese BestÃ¤tigung:
                        "Du hast es erraten"
                        Sobald die BestÃ¤tigung eingeht, gib eine kurze positive Abschlusszeile und stelle keine weiteren Spiel-Fragen.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        BegrÃ¼ÃŸe den Nutzer kurz auf Deutsch und sage:
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
                State sessionFinal = new Final("Session Goodbye Final", SingleStateGuessingGame.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateGuessingGame.PROMPT_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateGuessingGame.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                State interactionState = new State(
                                "Questions Based Guesser",
                                new PromptPolicy(
                                                SingleStateGuessingGame.PROMPT_STATE,
                                                SingleStateGuessingGame.PROMPT_STATE_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(toFinal));

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Guessing Game)",
                                "Single-state guessing game demo with outcome extraction and final summary.",
                                interactionState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}

