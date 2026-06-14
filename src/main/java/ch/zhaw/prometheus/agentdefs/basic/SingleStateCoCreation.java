package ch.zhaw.prometheus.agentdefs.basic;


import java.util.List;


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
public class SingleStateCoCreation implements AgentDefinition {

        private static final String PROMPT_STATE = """
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
        public static Agent createAgentDefinition() {
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
                agent.setInteractionProfile(AgentInteractionProfiles.speechOnly());
                return agent;
        }

    public static final String KEY = "basic.single_state_co_creation";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Agent createAgent() {
        return createAgentDefinition();
    }

    @Override
    public AgentCreationResult createInstance(AgentCreationContext context) {
        Agent agent = createAgentDefinition();
        return AgentCreationResult.started(agent, agent.start(context.runtime()));
    }
}
