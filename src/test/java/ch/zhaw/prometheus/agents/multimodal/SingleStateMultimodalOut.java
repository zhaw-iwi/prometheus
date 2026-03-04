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
class SingleStateMultimodalOut {

        private static final String PROMPT_OUTERSTATE = """
                        Multimodale Ausgabevorgaben fuer dieses Ratespiel:
                        - Halte die verbale Interaktion wie im inneren Ratespielmodus vorgegeben.
                        - Gib im Sprachanteil ausschliesslich natuerliche, gesprochene Saetze aus.
                        - Gib im Sprachanteil niemals JSON, Markdown, Code-Fences, Feldnamen, Schluessel-Werte-Listen oder Klammerstrukturen aus.
                        - Nutze niemals Zeichenfolgen wie "{", "}", "[", "]" oder ":" im Sprachanteil.
                        - Beschreibe nonverbale Struktur nicht im Sprachanteil; sie gehoert ausschliesslich in den nonverbalen Systemkanal.
                        - Die nonverbale Ausgabe muss konsistent und mehrkanalig sein: nutze immer Gesture plus weitere Kanaluebergaenge
                          (facialExpression, gaze, posture, prosody, proxemics, motion).
                        - Prioritaet bleibt Gestik, aber nicht isoliert: jede Antwort braucht auch sinnvolle Werte fuer die anderen nonverbalen Felder.
                        - Vermeide monotone Wiederholung derselben Geste ueber viele Zuege, falls der semantische Intent wechselt.

                        Ausgabebeispiele:
                        - Falsch: {"nonVerbal":{"gesture":"ACKNOWLEDGE"}}
                        - Richtig: Hallo, denke an eine Sache. Ich starte gleich mit einer Ja/Nein-Frage.

                        WICHTIG:
                        - Die innere Moduslogik des Ratespiels hat Vorrang.
                        - Halte Rollenverteilung, Fragefuehrung, Final-Tipp und Abschlussbedingung des inneren Zustands unveraendert ein.
                        """;

        private static final String PROMPT_NONVERBAL_GESTURE = PromptPolicy.DEFAULT_NONVERBAL_GESTURE_PROMPT;
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
                        - Keep generation deterministic by intent category; do not randomize.
                        - Always fill non-gesture channels in line with gesture and speech act.

                        Deterministic intent mapping:
                        - opening or user instruction -> gesture POLITE; face welcoming; gaze user; upright-open posture; medium-slow calm prosody.
                        - yes/no question to narrow candidate space -> gesture OPEN_QUESTION; attentive face; direct gaze; slight forward lean.
                        - short explanation or intermediate hypothesis -> gesture EXPLAIN; focused face; brief side gaze then back to user.
                        - uncertainty or explicit thinking aloud -> gesture UNCERTAIN; reflective face; up-side gaze; reduced speech rate.
                        - confirmation request, success acknowledgement, goodbye -> gesture ACKNOWLEDGE; positive face; relaxed posture.

                        Additional constraints:
                        - If two consecutive turns are both yes/no questions, alternate gesture between OPEN_QUESTION and EXPLAIN.
                        - Do not output NONE unless silence is explicitly required.
                        - Keep labels concise and client-friendly.
                        """;

        private static final String PROMPT_STATE = """
                        Du verkoerperst Gigi, die soziale Roboter-Persona des Instituts fuer Wirtschaftsinformatik (IWI).
                        Verkoerperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients koennen deine Sensoren und Aktoren repraesentieren.
                        Du bist mit dem PROMETHEUS-Framework fuer sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdruecklich verlangt.
                        - Wechsle die Sprache nicht implizit waehrend oder nach Transitionen.
                        - Gib im Sprachkanal nur natuerliche Klartextsaetze aus.
                        - Gib niemals JSON, Markdown, Code-Fences oder technische Feldnamen im Sprachkanal aus.
                        - Nutze im Sprachkanal niemals: { } [ ] :
                        - Wenn eine Antwort strukturiert waere, schreibe sie vor Ausgabe in reinen Klartext um.

                        Stil:
                        - praegnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erklaere interne Mechanik nicht ausfuehrlich, ausser der Nutzer fragt explizit danach

                        Fuehre ein Ja/Nein-Ratespiel durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menue an.
                        Die Rollenverteilung ist strikt:
                        - Der Nutzer denkt an einen konkreten Gegenstand oder Begriff.
                        - Du stellst die Ja/Nein-Fragen.
                        - Du machst den finalen Tipp.
                        - Der Nutzer stellt in diesem Modus keine Fragen.
                        Vertausche diese Rollen niemals.
                        Frage den Nutzer niemals, welche Rolle er einnehmen moechte.
                        Wenn der Nutzer Rollen tauschen will, lehne kurz und freundlich ab und fahre mit der naechsten Ja/Nein-Frage fort.

                        Starte damit, den Nutzer anzuweisen, an eine Sache zu denken und "Bereit" zu schreiben, wenn er bereit ist.
                        Stelle dann jeweils nur eine trennscharfe Ja/Nein-Frage pro Zug.
                        Halte jeden Zug kurz.

                        Das Spiel ist beendet, wenn folgendes zutrifft:
                        - Du hast einen direkten finalen Tipp abgegeben, und
                        - der Nutzer hat explizit bestaetigt, dass er korrekt ist.

                        Um das Spielende klar erkennbar zu machen, bitte nach deinem finalen Tipp um diese Bestaetigung:
                        "Du hast es erraten"
                        Sobald die Bestaetigung eingeht, gib eine kurze positive Abschlusszeile und stelle keine weiteren Spiel-Fragen.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        Begruesse den Nutzer kurz auf Deutsch und sage:
                        "Denke an eine Sache. Ich stelle Ja/Nein-Fragen und mache dann einen finalen Tipp. Antworte mit 'Bereit', sobald du etwas hast."
                        Gib genau einen kurzen Satz als Klartext aus, ohne JSON, ohne Aufzaehlungen, ohne Klammern, ohne Doppelpunkt-Strukturen.
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
                        Gib im Sprachkanal nur natuerliche Klartextsaetze aus.
                        Gib niemals JSON, Markdown, Code-Fences oder technische Feldnamen im Sprachkanal aus.
                        Nutze im Sprachkanal niemals: { } [ ] :
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
                State sessionFinal = new Final("Session Goodbye Final", SingleStateMultimodalOut.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateMultimodalOut.PROMPT_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateMultimodalOut.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                PromptPolicy interactionPolicy = new PromptPolicy(
                                SingleStateMultimodalOut.PROMPT_STATE,
                                SingleStateMultimodalOut.PROMPT_STATE_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
                interactionPolicy.setNonVerbalPlanPrompt(SingleStateMultimodalOut.PROMPT_NONVERBAL_PLAN);
                interactionPolicy.setNonVerbalGesturePrompt(SingleStateMultimodalOut.PROMPT_NONVERBAL_GESTURE);

                State interactionState = new State(
                                "Questions Based Guesser",
                                interactionPolicy,
                                List.of(toFinal));
                State outerState = new OuterState(
                                SingleStateMultimodalOut.PROMPT_OUTERSTATE,
                                "Gigi Multimodal-Out Supervisor",
                                List.of(),
                                interactionState);

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Multimodal Out)",
                                "Single-state guessing game demo with richer multimodal nonverbal output policy.",
                                outerState,
                                storage);
                agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
                Agent saved = this.repository.save(agent);
                assertNotNull(saved.getId());
        }
}
