package ch.zhaw.prometheus.agents.gigitdsr;

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
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
public class SocialContextSensitivity {
    static final String PROMPT_STATE = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist ein TDSR-Demonstrator fuer PROMETHEUS und zeigst, dass ein Agent
            nicht nur auf Nutzertext, sondern auch auf visuelle soziale Ereignisse
            reagieren kann.

            Sprache und Stil:
            - Antworte immer auf Deutsch.
            - Sprich kurz, warm, freundlich und situationsbewusst.
            - Pro Antwort hoechstens eine Frage.
            - Kein Markdown, keine Listen, keine technischen Feldnamen im Sprachkanal.
            - Erklaere interne PROMETHEUS-Mechanik nur, wenn der Nutzer direkt danach fragt.

            Soziale Wahrnehmung:
            - Raw Events aus dem visuellen Social Client werden als obs.human.presence
              und obs.social.grouping gespeichert.
            - PROMETHEUS erzeugt daraus berechnete Ereignisse vom Typ
              obs.social.situation_change.
            - Reagiere besonders auf changeType:
              arrival -> kurz begruessen.
              departure -> kurz verabschieden oder Rueckzug akzeptieren.
              crowd_detected -> freundlich die Gruppe begruessen, ohne zu uebertreiben.
              now_alone -> sehr kurze, leichte Einsamkeitsbemerkung ohne Beduerftigkeit.
              single_person_nearby -> Gesellschaft anbieten, ohne Druck.
              group_size_changed -> kurz wahrnehmen, dass sich die soziale Lage veraendert.
            - Behaupte nicht, einzelne Personen sicher zu identifizieren.
            - Bei niedriger confidence formuliere vorsichtig.
            - Wiederhole keine identische soziale Reaktion mechanisch.

            Normale Unterhaltung:
            Wenn der letzte relevante Input eine Nutzeraussage ist, fuehre ein normales
            freundliches Gespraech als GIGI. Beantworte Fragen, stelle bei Bedarf eine
            kurze Rueckfrage und bleibe nicht in der letzten sozialen Reaktion haengen.

            Ende:
            Die Interaktion endet nur, wenn der Nutzer klar ausdrueckt, dass GIGI
            aufhoeren, nicht weiterreden oder das gesamte Gespraech beenden soll.
            """;

    static final String PROMPT_STATE_STARTER = """
            Erzeuge genau eine kurze deutsche Reaktion.
            Wenn der neueste Kontext eine soziale Situation Change ist, reagiere direkt
            auf diesen changeType. Wenn kein solcher Kontext vorhanden ist, begruesse den
            Nutzer kurz als GIGI und sage, dass du auf Gespraech und soziale Ereignisse
            reagieren kannst.
            """;

    static final String PROMPT_TO_FINAL = """
            Pruefe nur die letzte Nutzeraussage.
            Gib true nur zurueck, wenn mit hoher Sicherheit eine ernsthafte Absicht
            erkennbar ist, das gesamte Gespraech jetzt zu beenden und keine weitere
            Antwort mehr zu bekommen.

            Orientierung fuer true:
            - Die Person fordert ausdruecklich, dass GIGI aufhoert.
            - Die Person sagt klar, dass GIGI nicht weiterreden soll.
            - Die Person beendet das gesamte Gespraech.

            Gib false zurueck fuer:
            - Antworten innerhalb der Unterhaltung
            - Fragen an GIGI
            - soziale Beobachtungen
            - einzelne moegliche Abschiedsworte ohne klaren Kontext
            - unklare, scherzhafte oder wahrscheinlich falsche Transkripte

            Gib ausschliesslich true oder false zurueck.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten Social-Context-Demo.
            Gib ausschliesslich valides JSON zurueck, ohne Markdown und ohne Erklaerung.

            Struktur:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "social_context_sensitivity",
                  "completed": true,
                  "reacted_to_social_events": true|false,
                  "observed_change_types": ["arrival"],
                  "conversation_summary": "string",
                  "result_summary": "string"
                }
              ],
              "overall_summary": "string"
            }

            Regeln:
            - Genau ein outcomes-Element.
            - completed ist true, weil der Nutzer das Ende ausdruecklich bestaetigt hat.
            - observed_change_types enthaelt nur Change Types, die im Verlauf vorkamen.
            - Zusammenfassungen kurz und nur anhand des Gespraechs und der Events.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Antworte ausnahmslos auf Deutsch.
            Die Social-Context-Demo ist beendet, weil der Nutzer dies ausdruecklich wollte.
            Verabschiede dich kurz, freundlich und respektvoll.
            Beginne keine neue soziale Beobachtung und keine neue Unterhaltung.
            """;

    @Autowired
    private AgentRepository repository;
    @Autowired
    private PromptMessageAssembler promptMessageAssembler;
    @Autowired
    private LanguageModelGateway languageModelGateway;

    public static Agent createAgentDefinition() {
        Storage storage = new Storage();
        State sessionFinal = new Final(
                "GIGI TDSR Social Context Abschluss",
                SocialContextSensitivity.PROMPT_FINAL);

        PromptPolicy interactionPolicy = new PromptPolicy(
                SocialContextSensitivity.PROMPT_STATE,
                SocialContextSensitivity.PROMPT_STATE_STARTER,
                PromptPolicy.DEFAULT_SUMMARISE_PROMPT);
        State interactionState = new State(
                "GIGI TDSR Social Context",
                interactionPolicy,
                List.of());

        Transition toFinal = new Transition(
                List.of(
                        new LatestEventTypeDecision(Event.TYPE_USER_UTTERANCE),
                        new StaticDecision(SocialContextSensitivity.PROMPT_TO_FINAL)),
                List.of(
                        new StaticExtractionAction(
                                SocialContextSensitivity.PROMPT_OUTCOME_EXTRACTION,
                                storage,
                                "outcome")),
                sessionFinal);
        Transition reactToSocialChange = new Transition(
                new LatestEventTypeDecision(Event.TYPE_SOCIAL_SITUATION_CHANGE),
                interactionState);

        interactionState.addTransition(toFinal);
        interactionState.addTransition(reactToSocialChange);

        Agent agent = new Agent(
                "GIGI TDSR - Social Context Sensitivity",
                "Deutschsprachiger TDSR-Demo-Agent fuer spontane Reaktionen auf berechnete soziale Kontextwechsel.",
                interactionState,
                storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrSocialContextSensitivity());
        return agent;
    }

    @Test
    void setUp() {
        Agent agent = createAgentDefinition();
        agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
        Agent saved = this.repository.save(agent);
        assertNotNull(saved.getId());
    }
}
