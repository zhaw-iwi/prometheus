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
class SingleStateCoCreation {

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

                        FÃ¼hre ein Story-Co-Creation-Spiel durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein MenÃ¼ an.
                        Frage zuerst nach Genre und einer Figur.
                        Erstelle dann gemeinsam eine kurze Geschichte Zug fÃ¼r Zug, insgesamt hÃ¶chstens 8 Assistant-ZÃ¼ge.
                        Halte jeden Assistant-Zug bei maximal zwei SÃ¤tzen.

                        Das Co-Creation-Spiel ist beendet, wenn folgendes zutrifft:
                        - ein vollstÃ¤ndiges Ende wurde erzeugt, und
                        - der Nutzer bestÃ¤tigt den Abschluss explizit.

                        Um das Ende klar erkennbar zu machen, bitte den Nutzer nach dem Ende mit folgender Antwort:
                        "Die Geschichte ist zu Ende"
                        Sobald der Nutzer diese BestÃ¤tigung eingeht, antworte mit einer kurzen Abschlusszeile und erweitere die Geschichte nicht weiter.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        BegrÃ¼ÃŸe den Nutzer kurz auf Deutsch und frage ihn, ein Genre und eine Figur fÃ¼r den Start zu wÃ¤hlen.
                        """;

        private static final String PROMPT_TO_FINAL = """
                        Detect exit condition X for a story co-creation session where assistant and user elaborate a story together.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true if either:
                        - the story has a clear ending, and
                        - the latest user message clearly confirms that the story is complete (including paraphrases),
                        OR
                        - the latest user message clearly expresses global quit/end intent for the whole interaction.
                        Examples for true:
                        - "Ja, die Geschichte ist fertig."
                        - "Das Ende passt, wir sind durch."
                        - "Die Geschichte ist zu Ende."
                        - "Ich moechte die Interaktion beenden."
                        - "Lass uns hier aufhoeren."
                        - "Das war's, ich bin raus."
                        - "Bye, ich moechte nicht weitermachen."
                        Return false if the message asks to continue/modify the story, for ambiguous/non-committal messages,
                        and for mode-switch/meta/capability utterances.
                        Examples for false:
                        - "lass uns weiterschreiben"
                        - "ok"
                        - "Was kannst du alles?"
                        - "Gehen wir in einen anderen Modus?"
                        """;

        private static final String PROMPT_OUTCOME_EXTRACTION = """
                        Extract the outcome of the just-completed specialized interaction from the conversation and return STRICT JSON only.
                        Do not return markdown, code fences, or explanatory text.

                        The JSON object must have exactly this top-level structure and field names:
                        {
                          "flow_type": "single_state",
                          "outcomes": [
                            {
                              "interaction_type": "story_co_creation",
                              "completed": true|false,
                              "result_summary": "string",
                              "user_confirmation": "string|null"
                            }
                          ],
                          "overall_summary": "string"
                        }

                        Rules:
                        - Include exactly one entry in "outcomes".
                        - "interaction_type" must be exactly "story_co_creation".
                        - Set "completed" to true only when the specialized story completion condition was reached.
                        - Set "completed" to false when the transition happened due to global quit/end intent.
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Keep summaries concise and evidence-based from the conversation only.
                        - Keep "flow_type" exactly "single_state".
                        """;

        private static final String PROMPT_FINAL = """
                        Dies ist der finale Zustand und die Sitzung ist abgeschlossen.
                        Gib die Ausgabe auf Deutsch aus, ausser der Nutzer hat explizit eine andere Sprache verlangt.
                        Beruecksichtige beide Pfade:
                        - Bei abgeschlossener Story-Co-Creation: gib eine kurze, sinnvolle Zusammenfassung
                          (Genre/Figur/Ende und bestaetigter Abschluss).
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
                State sessionFinal = new Final("Session Goodbye Final", SingleStateCoCreation.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateCoCreation.PROMPT_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateCoCreation.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                State interactionState = new State(
                                "Story Co Creation",
                                new PromptPolicy(
                                                SingleStateCoCreation.PROMPT_STATE,
                                                SingleStateCoCreation.PROMPT_STATE_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(toFinal));

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Co-Creation)",
                                "Single-state story co-creation demo with outcome extraction and final summary.",
                                interactionState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}

