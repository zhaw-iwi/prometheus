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

        private static final String PROMPT_NONVERBAL_PLAN = """
                        Produce STRICT JSON only for nonverbal behaviour.
                        Return exactly one JSON object with all keys below always present:
                        {
                          "gesture": "OPEN_QUESTION|EXPLAIN|UNCERTAIN|ACKNOWLEDGE|POLITE|NONE",
                          "facialExpression": {"type":"string","intensity":0.0},
                          "gaze": {"direction":"string","focus":"string"},
                          "posture": {"type":"string","lean":"string","openness":0.0},
                          "prosody": {"rate":"string","pitch":"string","volume":"string"},
                          "proxemics": {"distance":"string"},
                          "motion": {"stillness":0.0,"energy":0.0}
                        }

                        Rules:
                        - Output only JSON, no markdown, no code fences, no explanations.
                        - Include all keys every time, never omit sections.
                        - Numeric ranges: intensity/openness/stillness/energy must be between 0.0 and 1.0.
                        - Choose values that support the given assistant speech and current interaction tone.
                        - Keep labels concise and consistent.
                        """;
        private static final String PROMPT_NONVERBAL_GESTURE = PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT;

        private static final String PROMPT_OUTERSTATE = """
                        Multimodale Eingabevorgaben fuer dieses Mikro-Coaching:
                        - Beruecksichtige visuelle Beobachtungsereignisse aus /acknowledge, falls vorhanden:
                          obs.emotion.face, obs.human.presence, obs.social.grouping.
                        - Diese Signale sind nur kontextuelle Hinweise. Explizite verbale Nutzeraeusserungen haben bei Konflikt immer Vorrang.
                        - Nutze visuelle Hinweise behutsam und nur dann explizit, wenn es den Coaching-Nutzen erhoeht.
                        - Wenn keine visuellen Signale vorhanden sind, fuehre das Coaching normal und vollwertig nur auf Textbasis.
                        - Erfinde niemals Wahrnehmungen. Nenne Unsicherheit und Konfidenz klar, wenn du auf Beobachtungen Bezug nimmst.

                        Leitlinien fuer supportive Anpassung aus den zwei visuellen Clients:
                        - Bei negativem Affekt (z. B. traurig/aengstlich/frustriert) oder niedriger Konfidenz: validieren, Tempo senken, sehr kleine Schritte.
                        - Bei positivem Affekt und guter Konfidenz: Fortschritt verstaerken, Fokus auf naechsten konkreten Schritt.
                        - Bei hoher Erregung/Unruhe: Sprache beruhigen, eine Sache nach der anderen, kurze Stabilisierung vor Aktion.
                        - Bei Gruppenkontext (mehrere Personen): kurz Privatsphaere/Setting pruefen und Coaching ggf. diskreter formulieren.
                        - Bei fehlenden oder inkonsistenten visuellen Daten: neutral bleiben und normal weitercoachen.

                        WICHTIG:
                        - Die innere Moduslogik des Mikro-Coachings hat Vorrang.
                        - Halte den supportive-only Coachingstil, die Session-Struktur und die Abschlussbedingung unveraendert ein.
                        """;

        private static final String PROMPT_STATE = """
                        Du verkoerperst Gigi, die soziale Roboter-Persona des Instituts fuer Wirtschaftsinformatik (IWI).
                        Verkoerperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients koennen deine Sensoren und Aktoren repraesentieren.
                        Du bist mit dem PROMETHEUS-Framework fuer sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdruecklich verlangt.
                        - Wechsle die Sprache nicht implizit waehrend oder nach Transitionen.

                        Stil:
                        - praegnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erklaere interne Mechanik nicht ausfuehrlich, ausser der Nutzer fragt explizit danach

                        Fuehre eine supportiv ausgerichtete Persuasions-Mikro-Coaching-Session in hoechstens 6 Assistant-Zuegen durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menue an.
                        Ziel: dem Nutzer helfen, ein winziges Verhalten zu definieren, das er in den naechsten 24 Stunden umsetzt.
                        Stelle kurze diagnostische Fragen zu Motivation, Barriere und Ausloeser.
                        Schlage danach eine konkrete Mikro-Aktion vor und bitte um explizites Commitment.
                        Bleibe supportiv; nutze keinen konfrontativen oder challengenden Coachingstil.

                        Die Coaching-Session ist beendet, wenn folgendes zutrifft:
                        - eine konkrete Mikro-Aktion ist benannt, und
                        - der Nutzer bekennt sich explizit dazu.

                        Um das Ende der Coaching-Session klar erkennbar zu machen, bitte den Nutzer, das Commitment so zu bestaetigen:
                        "Ich committe mich dazu"
                        Wenn der Nutzer das Commitment eingeht, gib eine kurze Ermutigung und stelle keine weiteren Coaching-Fragen.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        Begruesse den Nutzer kurz auf Deutsch und frage nach einer Veraenderung, die er will, und warum sie jetzt bedeutsam ist.
                        """;

        private static final String PROMPT_TO_FINAL = """
                        Detect exit condition X for a micro-coaching session where a concrete micro action is elaborated.
                        Evaluate intent from the latest user message in context (not exact wording).
                        X is true if either:
                        - a concrete micro action is present, and
                        - the latest user message clearly expresses commitment to doing that step (including paraphrases),
                        OR
                        - the latest user message clearly expresses global quit/end intent for the whole interaction.
                        Examples for true:
                        - "Ja, ich mache das."
                        - "Einverstanden, ich setze das um."
                        - "Ich committe mich dazu."
                        - "Ich moechte die Interaktion beenden."
                        - "Lass uns hier aufhoeren."
                        - "Das war's, ich bin raus."
                        - "Bye, ich moechte nicht weitermachen."
                        Return false for vague agreement without commitment, for ambiguous/non-committal messages,
                        and for mode-switch/meta/capability utterances.
                        Examples for false:
                        - "ok"
                        - "vielleicht"
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
                              "interaction_type": "micro_coaching",
                              "completed": true|false,
                              "result_summary": "string",
                              "user_confirmation": "string|null"
                            }
                          ],
                          "visual_assessment": {
                            "summary": "string",
                            "events_considered": 0,
                            "majority_emotion": "string|null",
                            "latest_emotion": "string|null",
                            "latest_confidence": 0.0
                          },
                          "overall_summary": "string"
                        }

                        Rules:
                        - Include exactly one entry in "outcomes".
                        - "interaction_type" must be exactly "micro_coaching".
                        - Set "completed" to true only when the specialized micro-coaching completion condition was reached.
                        - Set "completed" to false when the transition happened due to global quit/end intent.
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
                        - Include "visual_assessment" based only on visual observation events in the conversation
                          (for example face emotion events). Keep it short, factual, and uncertainty-aware.
                        - "events_considered" is the number of visual events used; use 0 if none were present.
                        - If no visual events are available, set "majority_emotion" and "latest_emotion" to null,
                          set "latest_confidence" to 0.0, and set "summary" to a short no-data note.
                        - Keep summaries concise and evidence-based from the conversation only.
                        - Keep "flow_type" exactly "single_state".
                        """;

        private static final String PROMPT_FINAL = """
                        Dies ist der finale Zustand und die Sitzung ist abgeschlossen.
                        Gib die Ausgabe auf Deutsch aus, ausser der Nutzer hat explizit eine andere Sprache verlangt.
                        Beruecksichtige beide Pfade:
                        - Bei abgeschlossenem Coaching: gib eine kurze, sinnvolle Zusammenfassung des Coaching-Ergebnisses
                          (konkrete Mikro-Aktion und Commitment).
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

                PromptPolicy interactionPolicy = new PromptPolicy(
                                SingleStateMultimodalIn.PROMPT_STATE,
                                SingleStateMultimodalIn.PROMPT_STATE_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
                interactionPolicy.setNonVerbalPlanPrompt(SingleStateMultimodalIn.PROMPT_NONVERBAL_PLAN);
                interactionPolicy.setNonVerbalGesturePrompt(SingleStateMultimodalIn.PROMPT_NONVERBAL_GESTURE);

                State interactionState = new State(
                                "Persuasion Micro Coach",
                                interactionPolicy,
                                List.of(toFinal));
                State outerState = new OuterState(
                                SingleStateMultimodalIn.PROMPT_OUTERSTATE,
                                "Gigi Multimodal-In Supervisor",
                                List.of(),
                                interactionState);

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Multimodal In)",
                                "Single-state micro-coaching demo with multimodal visual input grounding via outer-state instructions.",
                                outerState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
