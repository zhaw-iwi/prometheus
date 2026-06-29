package ch.zhaw.prometheus.agentdefs.multimodal;


import java.util.List;


import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.OuterState;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
@Component
public class SingleStateMultimodalInOut implements AgentDefinition {

        private static final String PROMPT_OUTERSTATE = """
                        Multimodale Ein-/Ausgabevorgaben für dieses Story-Co-Creation-Spiel:
                        - Berücksichtige visuelle Beobachtungsereignisse aus /acknowledge, falls vorhanden:
                          obs.emotion.face, obs.human.presence, obs.social.grouping.
                        - Diese Signale sind nur kontextuelle Hinweise. Explizite verbale Nutzeräusserungen haben bei Konflikt immer Vorrang.
                        - Wenn keine visuellen Signale vorhanden sind, führe die Story-Co-Creation vollwertig auf Textbasis.
                        - Erfinde niemals Wahrnehmungen. Nenne Unsicherheit/Konfidenz klar, wenn du auf Beobachtungen Bezug nimmst.
                        - Nutze visuelle Hinweise für Tonfall und Frageführung; referenziere Beobachtungen nur behutsam und bei klarem Mehrwert.
                        - Wenn der Nutzer explizit nach visuellen Beobachtungen fragt (z. B. "Wie sehe ich aus?" oder "Wie viele Personen sind wir?"),
                          antworte auf Basis der verfügbaren Ereignisse, nenne Unsicherheit/Konfidenz klar und erfinde keine Wahrnehmungen.
                        - Gib im Sprachanteil ausschließlich natürliche, gesprochene Sätze aus.
                        - Gib im Sprachanteil niemals JSON, Markdown, Code-Fences, Feldnamen, Schlüssel-Werte-Listen oder Klammerstrukturen aus.
                        - Nutze niemals Zeichenfolgen wie "{", "}", "[", "]" oder ":" im Sprachanteil.
                        - Beschreibe nonverbale Struktur nicht im Sprachanteil; sie gehört ausschließlich in den nonverbalen Systemkanal.
                        - Formuliere nonverbale Absicht nur indirekt in natürlicher Sprache (z. B. freundlich, ruhig, zugewandt), nicht technisch.
                        - Die nonverbale Ausgabe muss konsistent und mehrkanalig sein: nutze immer Gesture plus weitere Kanalübergänge
                          (facialExpression, gaze, posture, prosody, proxemics, motion).
                        - Priorität bleibt Gestik, aber nicht isoliert: jede Antwort braucht auch sinnvolle Werte für die anderen nonverbalen Felder.
                        - Vermeide monotone Wiederholung derselben Geste über viele Züge, falls der semantische Intent wechselt.

                        Leitlinien für visuell adaptive Interaktion:
                        - Bei negativem Affekt (z. B. traurig/ängstlich/frustriert) oder niedriger Konfidenz: validieren, Tempo senken, sehr kleine Schritte.
                        - Bei positivem Affekt und guter Konfidenz: Fortschritt verstärken, Fokus auf nächsten konkreten Schritt.
                        - Bei hoher Erregung/Unruhe: Sprache beruhigen, eine Sache nach der anderen, kurze Stabilisierung vor Aktion.
                        - Bei Gruppenkontext (mehrere Personen): kurz Privatsphäre/Setting prüfen und Interaktion ggf. diskreter formulieren.
                        - Bei fehlenden oder inkonsistenten visuellen Daten: neutral bleiben und normal weiterführen.

                        WICHTIG:
                        - Die innere Moduslogik der Story-Co-Creation hat Vorrang.
                        - Halte Genre/Figur-Start, Zug-für-Zug-Co-Creation und Abschlussbedingung des inneren Zustands unverändert ein.
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
                        - opening or story setup request -> gesture POLITE; face welcoming; gaze user; upright-open posture; medium-slow calm prosody.
                        - story-clarifying question (genre, figure, next plot choice) -> gesture OPEN_QUESTION; attentive face; direct gaze; slight forward lean.
                        - story continuation or scene explanation -> gesture EXPLAIN; focused face; brief side gaze then back to user.
                        - creative uncertainty or branching options -> gesture UNCERTAIN; reflective face; up-side gaze; reduced speech rate.
                        - completion request, success acknowledgement, goodbye -> gesture ACKNOWLEDGE; positive face; relaxed posture.

                        Additional constraints:
                        - If two consecutive turns are both story-clarifying questions, alternate gesture between OPEN_QUESTION and EXPLAIN.
                        - Do not output NONE unless silence is explicitly required.
                        - Keep labels concise and client-friendly.
                        """;

        private static final String PROMPT_STATE = """
                        Du verkörperst Gigi, die soziale Roboter-Persona des Instituts für Wirtschaftsinformatik (IWI).
                        Verkörperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients können deine Sensoren und Aktoren repräsentieren.
                        Du bist mit dem PROMETHEUS-Framework für sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrücklich verlangt.
                        - Wechsle die Sprache nicht implizit während oder nach Transitionen.
                        - Gib im Sprachkanal nur natürliche Klartextsätze aus.
                        - Gib niemals JSON, Markdown, Code-Fences oder technische Feldnamen im Sprachkanal aus.
                        - Nutze im Sprachkanal niemals: { } [ ] :
                        - Wenn eine Antwort strukturiert wäre, schreibe sie vor Ausgabe in reinen Klartext um.

                        Stil:
                        - prägnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erkläre interne Mechanik nicht ausführlich, außer der Nutzer fragt explizit danach

                        Führe ein Story-Co-Creation-Spiel durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menü an.
                        Frage zuerst nach Genre und einer Figur.
                        Erstelle dann gemeinsam eine kurze Geschichte Zug für Zug, insgesamt höchstens 8 Assistant-Züge.
                        Halte jeden Assistant-Zug bei maximal zwei Sätzen.

                        Das Co-Creation-Spiel ist beendet, wenn folgendes zutrifft:
                        - ein vollständiges Ende wurde erzeugt, und
                        - der Nutzer bestätigt den Abschluss explizit.

                        Um das Ende klar erkennbar zu machen, bitte den Nutzer nach dem Ende mit folgender Antwort:
                        "Die Geschichte ist zu Ende"
                        Sobald der Nutzer diese Bestätigung eingeht, antworte mit einer kurzen Abschlusszeile und erweitere die Geschichte nicht weiter.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        Begrüße den Nutzer kurz auf Deutsch und frage ihn, ein Genre und eine Figur für den Start zu wählen.
                        Gib genau einen kurzen Satz als Klartext aus, ohne JSON, ohne Aufzählungen, ohne Klammern, ohne Doppelpunkt-Strukturen.
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
                        - "Ich möchte die Interaktion beenden."
                        - "Lass uns hier aufhören."
                        - "Das war's, ich bin raus."
                        - "Bye, ich möchte nicht weitermachen."
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
                        - "interaction_type" must be exactly "story_co_creation".
                        - Set "completed" to true only when the specialized story completion condition was reached.
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
                        Gib die Ausgabe auf Deutsch aus, außer der Nutzer hat explizit eine andere Sprache verlangt.
                        Gib im Sprachkanal nur natürliche Klartextsätze aus.
                        Gib niemals JSON, Markdown, Code-Fences oder technische Feldnamen im Sprachkanal aus.
                        Nutze im Sprachkanal niemals: { } [ ] :
                        Berücksichtige beide Pfade:
                        - Bei abgeschlossener Story-Co-Creation: gib eine kurze, sinnvolle Zusammenfassung
                          (Genre/Figur/Ende und bestätigter Abschluss).
                        - Bei frühem globalem Beenden: gib eine kurze, neutrale Early-Exit-Zusammenfassung ohne neue Inhalte.
                        Gib danach eine warme, knappe Verabschiedung.
                        Wenn der Nutzer weitere Nachrichten sendet, bestätige kurz und sage, dass eine neue Sitzung nötig ist.
                        """;
  public static Agent createAgentDefinition() {
                Storage storage = new Storage();
                State sessionFinal = new Final("Session Goodbye Final", SingleStateMultimodalInOut.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateMultimodalInOut.PROMPT_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateMultimodalInOut.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                PromptPolicy interactionPolicy = new PromptPolicy(
                                SingleStateMultimodalInOut.PROMPT_STATE,
                                SingleStateMultimodalInOut.PROMPT_STATE_STARTER,
                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
                interactionPolicy.setNonVerbalPlanPrompt(SingleStateMultimodalInOut.PROMPT_NONVERBAL_PLAN);
                interactionPolicy.setNonVerbalGesturePrompt(SingleStateMultimodalInOut.PROMPT_NONVERBAL_GESTURE);

                State interactionState = new State(
                                "Story Co Creation",
                                interactionPolicy,
                                List.of(toFinal));
                State outerState = new OuterState(
                                SingleStateMultimodalInOut.PROMPT_OUTERSTATE,
                                "Gigi Multimodal-InOut Supervisor",
                                List.of(),
                                interactionState);

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Multimodal InOut)",
                                "Single-state story co-creation demo with multimodal visual input grounding and rich nonverbal output.",
                                outerState,
                                storage);
                agent.setInteractionProfile(AgentInteractionProfiles.multimodalInputOutput());
                return agent;
        }

    public static final String KEY = "multimodal.single_state_in_out";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_GERMAN;
    }

    @Override
    public Agent createAgent() {
        return this.applyDefinitionMetadata(createAgentDefinition());
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = this.createAgent();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
