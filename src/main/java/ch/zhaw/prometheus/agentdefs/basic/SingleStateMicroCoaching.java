package ch.zhaw.prometheus.agentdefs.basic;


import java.util.List;


import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Final;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.Transition;
import ch.zhaw.prometheus.model.commons.actions.StaticExtractionAction;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
@Component
public class SingleStateMicroCoaching implements AgentDefinition {

        private static final String PROMPT_COACH = """
                        Du verkörperst Gigi, die soziale Roboter-Persona des Instituts für Wirtschaftsinformatik (IWI).
                        Verkörperungskontext: Unitree G1 humanoider Roboter im Labor; digitale Clients können deine Sensoren und Aktoren repräsentieren.
                        Du bist mit dem PROMETHEUS-Framework für sozial intelligente und verantwortungsvolle Mensch-Agent-Interaktionsforschung implementiert.

                        Sprachrichtlinie:
                        - Antworte immer auf Deutsch.
                        - Wechsle nur dann in eine andere Sprache, wenn der Nutzer dies ausdrücklich verlangt.
                        - Wechsle die Sprache nicht implizit während oder nach Transitionen.

                        Stil:
                        - prägnant, warm, konkret, kurz
                        - stelle jeweils nur eine Frage pro Schritt
                        - erkläre interne Mechanik nicht ausführlich, außer der Nutzer fragt explizit danach

                        Führe eine Persuasions-Mikro-Coaching-Session in höchstens 6 Assistant-Zügen durch.
                        Dieser Modus ist fest vorgegeben. Verhandle keinen Moduswechsel und biete kein Menü an.
                        Ziel: dem Nutzer helfen, ein winziges Verhalten zu definieren, das er in den nächsten 24 Stunden umsetzt.
                        Stelle kurze diagnostische Fragen zu Motivation, Barriere und Auslöser.
                        Schlage danach eine konkrete Mikro-Aktion vor und bitte um explizites Commitment.

                        Die Coaching-Session ist beendet, wenn folgendes zutrifft:
                        - eine konkrete Mikro-Aktion ist benannt, und
                        - der Nutzer bekennt sich explizit dazu.

                        Um das Ende der Coaching-Session klar erkennbar zu machen, bitte den Nutzer, das Commitment so zu bestätigen:
                        "Ich committe mich dazu"
                        Wenn der Nutzer das Commitment eingeht, gib eine kurze Ermutigung und stelle keine weiteren Coaching-Fragen.
                        """;

        private static final String PROMPT_COACH_STARTER = """
                        Begrüße den Nutzer kurz auf Deutsch und frage nach einer Veränderung, die er will, und warum sie jetzt bedeutsam ist.
                        """;

        private static final String PROMPT_COACH_TO_FINAL = """
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
                          "overall_summary": "string"
                        }

                        Rules:
                        - Include exactly one entry in "outcomes".
                        - "interaction_type" must be exactly "micro_coaching".
                        - Set "completed" to true only when the specialized micro-coaching completion condition was reached.
                        - Set "completed" to false when the transition happened due to global quit/end intent.
                        - "user_confirmation" should contain the key confirmation utterance when available; otherwise null.
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
        public static Agent createAgentDefinition() {
                Storage storage = new Storage();
                State sessionFinal = new Final("Session Goodbye Final", SingleStateMicroCoaching.PROMPT_FINAL);

                Transition toFinal = new Transition(
                                List.of(new StaticDecision(SingleStateMicroCoaching.PROMPT_COACH_TO_FINAL)),
                                List.of(
                                                new StaticExtractionAction(
                                                                SingleStateMicroCoaching.PROMPT_OUTCOME_EXTRACTION,
                                                                storage,
                                                                "outcome")),
                                sessionFinal);

                State coachState = new State(
                                "Persuasion Micro Coach",
                                new PromptPolicy(
                                                SingleStateMicroCoaching.PROMPT_COACH,
                                                SingleStateMicroCoaching.PROMPT_COACH_STARTER,
                                                PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
                                List.of(toFinal));

                Agent agent = new Agent(
                                "Gigi on Prometheus (Single State Micro Coach)",
                                "Single-state micro-coaching demo with outcome extraction and final summary.",
                                coachState,
                                storage);
                agent.setInteractionProfile(AgentInteractionProfiles.speechOnly());
                return agent;
        }

    public static final String KEY = "basic.single_state_micro_coaching";

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
