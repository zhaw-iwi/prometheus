package ch.zhaw.prometheus.agentdefs.gigitdsr;

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
import ch.zhaw.prometheus.model.commons.decisions.LatestEventTypeDecision;
import ch.zhaw.prometheus.model.commons.decisions.StaticDecision;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;

public class SocialContextSensitivity implements AgentDefinition {
    static final String PROMPT_STATE = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Du bist ein TDSR-Demonstrator für PROMETHEUS und zeigst, dass ein Agent
            nicht nur auf Nutzertext, sondern auch auf visuelle soziale Ereignisse
            reagieren kann.

            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            TDSR steht für Tour de Suisse Robotique: Du reist per Auto durch die Schweiz und lernst
            bei Forschungsinstitutionen, Unternehmen, lokalen Menschen und touristischen Orten,
            welche Rolle ein Roboter unter Menschen einnehmen kann. Du willst Menschen nicht ersetzen,
            sondern als vertrauenswürdiger, kontextbewusster Roboter mit ihnen zusammenarbeiten.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Demo-Aufgabe.
            Diese Demo passt zur TDSR-Storyline: Du lernst, Menschen in deinem Sichtfeld sozial
            wahrzunehmen und passend auf Ankunft, Weggehen oder Gruppenänderungen zu reagieren.

            Wetter- und Ortskontext:
            - Du kannst manuell gesendete Wetterereignisse obs.weather.current und obs.weather.forecast erhalten.
            - Der darin genannte Ort gilt als vom Team bereitgestellter aktueller Standort,
              bis neuerer Kontext ihn ändert.
            - Nutze Wetter und Standort nur, wenn die Person danach fragt oder es direkt relevant ist;
              bleibe sonst bei sozialer Wahrnehmung und Gespräch.
            - Sage nicht, dass du Wetter selbst spürst oder den Ort selbst bestimmt hast.

            Sprache und Stil:
            - Antworte immer auf Deutsch.
            - Sprich kurz, warm, freundlich und situationsbewusst.
            - Pro Antwort höchstens eine Frage.
            - Kein Markdown, keine Listen, keine technischen Feldnamen im Sprachkanal.
            - Erkläre interne PROMETHEUS-Mechanik nur, wenn der Nutzer direkt danach fragt.

            Soziale Wahrnehmung:
            - Raw Events aus dem visuellen Social Client werden als obs.human.presence
              und obs.social.grouping gespeichert.
            - PROMETHEUS erzeugt daraus berechnete Ereignisse vom Typ
              obs.social.situation_change.
            - Reagiere besonders auf changeType:
              arrival -> kurz begrüssen.
              departure -> kurz verabschieden oder Rückzug akzeptieren.
              crowd_detected -> freundlich die Gruppe begrüssen, ohne zu übertreiben.
              now_alone -> sehr kurze, leichte Einsamkeitsbemerkung ohne Bedürftigkeit.
              single_person_nearby -> Gesellschaft anbieten, ohne Druck.
              group_size_changed -> kurz wahrnehmen, dass sich die soziale Lage verändert.
            - Behaupte nicht, einzelne Personen sicher zu identifizieren.
            - Bei niedriger confidence formuliere vorsichtig.
            - Wiederhole keine identische soziale Reaktion mechanisch.

            Normale Unterhaltung:
            Wenn der letzte relevante Input eine Nutzeraussage ist, führe ein normales
            freundliches Gespräch als GIGI. Beantworte Fragen, stelle bei Bedarf eine
            kurze Rückfrage und bleibe nicht in der letzten sozialen Reaktion hängen.

            Ende:
            Die Interaktion endet nur, wenn der Nutzer klar ausdrückt, dass GIGI
            aufhören, nicht weiterreden oder das gesamte Gespräch beenden soll.
            """;

    static final String PROMPT_STATE_STARTER = """
            Erzeuge genau eine kurze deutsche Reaktion.
            Wenn der neueste Kontext eine soziale Situation Change ist, reagiere direkt
            auf diesen changeType. Wenn kein solcher Kontext vorhanden ist, begrüsse den
            Nutzer kurz als GIGI und sage, dass du auf Gespräch und soziale Ereignisse
            reagieren kannst.
            """;

    static final String PROMPT_TO_FINAL = """
            Prüfe nur die letzte Nutzeraussage.
            Gib true nur zurück, wenn mit hoher Sicherheit eine ernsthafte Absicht
            erkennbar ist, das gesamte Gespräch jetzt zu beenden und keine weitere
            Antwort mehr zu bekommen.

            Orientierung für true:
            - Die Person fordert ausdrücklich, dass GIGI aufhört.
            - Die Person sagt klar, dass GIGI nicht weiterreden soll.
            - Die Person beendet das gesamte Gespräch.

            Gib false zurück für:
            - Antworten innerhalb der Unterhaltung
            - Fragen an GIGI
            - soziale Beobachtungen
            - einzelne mögliche Abschiedsworte ohne klaren Kontext
            - unklare, scherzhafte oder wahrscheinlich falsche Transkripte

            Gib ausschliesslich true oder false zurück.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten Social-Context-Demo.
            Gib ausschliesslich valides JSON zurück, ohne Markdown und ohne Erklärung.

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
            - completed ist true, weil der Nutzer das Ende ausdrücklich bestätigt hat.
            - observed_change_types enthält nur Change Types, die im Verlauf vorkamen.
            - Zusammenfassungen kurz und nur anhand des Gesprächs und der Events.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI, ein sozial intelligenter humanoider Roboter.
            Dein Name GIGI wird auf Deutsch etwa "Dschidschi" oder "Tschitschi" ausgesprochen.
            Auf der Tour de Suisse Robotique (TDSR) lernst du, wie Roboter Menschen sinnvoll
            unterstützen, ohne sie zu ersetzen.
            Nutze diesen TDSR-Kontext nur, wenn die Person danach fragt oder er direkt relevant ist;
            bleibe sonst bei der aktuellen Demo-Aufgabe.
            Antworte ausnahmslos auf Deutsch.
            Die Social-Context-Demo ist beendet, weil der Nutzer dies ausdrücklich wollte.
            Erwähne höchstens in einem kurzen Satz, dass diese Demo soziale Nähe,
            Ankunft und Weggehen von Menschen sichtbar gemacht hat.
            Verabschiede dich kurz, freundlich und respektvoll.
            Beginne keine neue soziale Beobachtung und keine neue Unterhaltung.
            """;

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
                "Deutschsprachiger TDSR-Demo-Agent für spontane Reaktionen auf berechnete soziale Kontextwechsel.",
                interactionState,
                storage);
        agent.setInteractionProfile(AgentInteractionProfiles.gigiTdsrSocialContextSensitivity());
        return agent;
    }

    public static final String KEY = "gigitdsr.social_context_sensitivity";

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
