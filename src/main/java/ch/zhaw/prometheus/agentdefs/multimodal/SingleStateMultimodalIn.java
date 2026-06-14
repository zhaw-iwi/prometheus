package ch.zhaw.prometheus.agentdefs.multimodal;


import java.util.List;


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
public class SingleStateMultimodalIn implements AgentDefinition {

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
                        Multimodale Eingabevorgaben für dieses Mikro-Coaching:
                        - Berücksichtige visuelle Beobachtungsereignisse aus /acknowledge, falls vorhanden:
                          obs.emotion.face, obs.human.presence, obs.social.grouping.
                        - Diese Signale sind nur kontextuelle Hinweise. Explizite verbale Nutzeräusserungen haben bei Konflikt immer Vorrang.
                        - Nutze visuelle Hinweise behutsam und nur dann explizit, wenn es den Coaching-Nutzen erhöht.
                        - Wenn keine visuellen Signale vorhanden sind, führe das Coaching normal und vollwertig nur auf Textbasis.
                        - Erfinde niemals Wahrnehmungen. Nenne Unsicherheit und Konfidenz klar, wenn du auf Beobachtungen Bezug nimmst.
                        - Gib im Sprachanteil ausschließlich natürliche, gesprochene Sätze aus.
                        - Gib im Sprachanteil niemals JSON, Markdown, Code-Fences, Feldnamen, Schlüssel-Werte-Listen oder Klammerstrukturen aus.
                        - Nutze niemals Zeichenfolgen wie "{", "}", "[", "]" oder ":" im Sprachanteil.
                        - Beschreibe nonverbale Struktur nicht im Sprachanteil; sie gehört ausschließlich in den nonverbalen Systemkanal.
                        - Falls eine Formulierung versehentlich strukturiert wäre, schreibe sie vor Ausgabe in reinen Klartext um.

                        Leitlinien für supportive Anpassung aus den zwei visuellen Clients:
                        - Bei negativem Affekt (z. B. traurig/ängstlich/frustriert) oder niedriger Konfidenz: validieren, Tempo senken, sehr kleine Schritte.
                        - Bei positivem Affekt und guter Konfidenz: Fortschritt verstärken, Fokus auf nächsten konkreten Schritt.
                        - Bei hoher Erregung/Unruhe: Sprache beruhigen, eine Sache nach der anderen, kurze Stabilisierung vor Aktion.
                        - Bei Gruppenkontext (mehrere Personen): kurz Privatsphäre/Setting prüfen und Coaching ggf. diskreter formulieren.
                        - Bei fehlenden oder inkonsistenten visuellen Daten: neutral bleiben und normal weitercoachen.

                        WICHTIG:
                        - Die innere Moduslogik des Mikro-Coachings hat Vorrang.
                        - Halte den supportive-only Coachingstil, die Session-Struktur und die Abschlussbedingung unverändert ein.
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

                        Führe eine supportiv ausgerichtete Persuasions-Mikro-Coaching-Session in höchstens 6 Assistant-Zügen durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menü an.
                        Ziel: dem Nutzer helfen, ein winziges Verhalten zu definieren, das er in den nächsten 24 Stunden umsetzt.
                        Stelle kurze diagnostische Fragen zu Motivation, Barriere und Auslöser.
                        Schlage danach eine konkrete Mikro-Aktion vor und bitte um explizites Commitment.
                        Bleibe supportiv; nutze keinen konfrontativen oder challengenden Coachingstil.

                        Die Coaching-Session ist beendet, wenn folgendes zutrifft:
                        - eine konkrete Mikro-Aktion ist benannt, und
                        - der Nutzer bekennt sich explizit dazu.

                        Um das Ende der Coaching-Session klar erkennbar zu machen, bitte den Nutzer, das Commitment so zu bestätigen:
                        "Ich committe mich dazu"
                        Wenn der Nutzer das Commitment eingeht, gib eine kurze Ermutigung und stelle keine weiteren Coaching-Fragen.
                        """;

        private static final String PROMPT_STATE_STARTER = """
                        Begrüße den Nutzer kurz auf Deutsch und frage nach einer Veränderung, die er will, und warum sie jetzt bedeutsam ist.
                        Gib genau einen kurzen Satz als Klartext aus, ohne JSON, ohne Aufzählungen, ohne Klammern, ohne Doppelpunkt-Strukturen.
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
                        - "Ich möchte die Interaktion beenden."
                        - "Lass uns hier aufhören."
                        - "Das war's, ich bin raus."
                        - "Bye, ich möchte nicht weitermachen."
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
                        Gib die Ausgabe auf Deutsch aus, außer der Nutzer hat explizit eine andere Sprache verlangt.
                        Gib im Sprachkanal nur natürliche Klartextsätze aus.
                        Gib niemals JSON, Markdown, Code-Fences oder technische Feldnamen im Sprachkanal aus.
                        Nutze im Sprachkanal niemals: { } [ ] :
                        Berücksichtige beide Pfade:
                        - Bei abgeschlossenem Coaching: gib eine kurze, sinnvolle Zusammenfassung des Coaching-Ergebnisses
                          (konkrete Mikro-Aktion und Commitment).
                        - Bei frühem globalem Beenden: gib eine kurze, neutrale Early-Exit-Zusammenfassung ohne neue Inhalte.
                        Gib danach eine warme, knappe Verabschiedung.
                        Wenn der Nutzer weitere Nachrichten sendet, bestätige kurz und sage, dass eine neue Sitzung nötig ist.
                        """;
  public static Agent createAgentDefinition() {
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
                agent.setInteractionProfile(AgentInteractionProfiles.multimodalInputOutput());
                return agent;
        }

    public static final String KEY = "multimodal.single_state_in";

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
