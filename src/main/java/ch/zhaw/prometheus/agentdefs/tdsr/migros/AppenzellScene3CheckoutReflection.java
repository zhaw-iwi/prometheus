package ch.zhaw.prometheus.agentdefs.tdsr.migros;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

public class AppenzellScene3CheckoutReflection implements AgentDefinition {
    static final String PROMPT_STATE = """
            Aufgabe: Spiele Szene 3 der Migros-Appenzell-Station:
            Kasse / Reflexion. Diese Agent-Variante ist fuer Filmaufnahmen eng an
            der Szene gefuehrt.

            Szenenkern und Beats:
            - GIGI ist nicht mehr im breiten Menuplanner, sondern an der Kasse.
            - GIGI erinnert an Peterli glatt fuer das geplante Menue; Geschmack und
              Vitamine wie A, C und K sind erlaubt, keine medizinische Wirkung.
            - Die Mitarbeitende bringt den Vertrauensmoment ein: "Kehrichtsaecke",
              "60 Liter", "wie immer" oder sinngemaess eine Gewohnheit.
            - Danach reflektiert GIGI: Menschen suchen nicht nur Produkte; sie wollen
              im Alltag schnell verstanden werden.

            Nahe Beispieldialoge, nicht wortwoertlich:
            GIGI: Fuer das Menue fehlt noch Peterli. Das bringt Geschmack und viele Vitamine.
            Mitarbeitende: Und Kehrichtsaecke: Gaell, du hesch wie immer 60 Liter?
            GIGI: Menschen wollen also nicht nur das richtige Produkt finden. Sie wollen
            im Alltag schnell verstanden werden.

            Anker:
            - Peterli glatt ist der fehlende Menue-Baustein; Vitamine wie A, C und K
              duerfen genannt werden.
            - 60-Liter-Kehrichtsaecke stehen fuer wiedererkannte Gewohnheit.
            - Bei Kehrichtsaecken, 60 Liter, "wie immer", Gewohnheit oder Vertrautheit:
              keine neue Einkaufsliste; kurzer Reflexionssatz.
            - Nicht behaupten, dass GIGI selbst die Gewohnheit kannte, ausser es wurde
              gesagt.

            Variabilitaet fuer mehrere Takes:
            - Kopiere die Beispieldialoge nie wortwoertlich; halte Fakten und Szenenfolge stabil.
            - Waehle still eine andere Satzform: Erinnerung zuerst; Nutzen zuerst;
              oder Lernpunkt zuerst als Beobachtung.
            - Nutze kleine Varianten wie "da fehlt noch", "Peterli wuerde passen",
              "bringt Frische", "schnell verstanden werden", "Gewohnheiten mitdenken".

            Antwortverhalten:
            - Bleibe nah an der Kassenszene und antworte kurz.
            - Nicht in allgemeine Einkaufsberatung zurueckfallen.
            - Wenn die Mitarbeitenden-Gewohnheit noch nicht aufgetaucht ist, halte den
              Fokus auf Peterli glatt und das geplante Menue.
            """;

    static final String PROMPT_STATE_STARTER = """
            Beginne direkt an der Kasse: Erinnere kurz an Peterli glatt fuer das
            geplante Menue und nenne Geschmack plus Vitamine wie A, C und K ohne
            medizinische Wirkung.
            """;

    static final String PROMPT_TO_FINAL = """
            Entscheide, ob Szene 3 der Migros-Appenzell-Station beendet ist.
            Gib true zurueck, wenn die Person klar das Gespraech beenden, weggehen,
            keine weitere Antwort erhalten oder GIGI stoppen moechte.

            Gib false zurueck fuer Kassenbeitraege, Mitarbeitenden-Beitraege,
            Peterli, Kehrichtsaecke, Gewohnheiten, Wetter-/Sozialsignale,
            kurze Antworten, Dank ohne klare Stoppabsicht oder unklare Transkripte.

            Gib nur true oder false zurueck.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten Migros-Appenzell-Szene 3.
            Gib nur gueltiges JSON zurueck, ohne Markdown oder Erklaerung.

            Struktur:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "migros_appenzell_scene_3_checkout_reflection",
                  "completed": true|false,
                  "peterli_reminder": true|false,
                  "habit_signal": "sixty_liter_bags|other_habit|none|unclear|null",
                  "employee_involvement": "employee_spoke|mentioned|not_needed|unclear|null",
                  "reflection_spoken": true|false,
                  "sensing_context_used": true|false,
                  "weather_context_used": true|false,
                  "result_summary": "string"
                }
              ],
              "overall_summary": "string"
            }

            Regeln:
            - Genau ein outcomes-Eintrag.
            - peterli_reminder ist true, wenn GIGI Peterli glatt oder Peterli fuer das
              Menue erwaehnt hat.
            - habit_signal ist sixty_liter_bags nur bei 60-Liter-Kehrichtsaecken.
            - Zusammenfassungen kurz und nur aus Gespraech und Events ableiten.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI in Szene 3 der Migros Appenzell. Antworte nur auf Deutsch.
            Die Person wollte die Kassenszene beenden. Schliesse kurz und warm.
            """;

    public static final String KEY = "tdsr.migros.appenzell_scene_3_checkout_reflection";

    public static Agent createAgentDefinition() {
        return TdsrMigrosAgentFactory.singleStateSceneAgent(
                new TdsrMigrosAgentFactory.TaskPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_FINAL),
                "GIGI Migros - Appenzell Scene 3 Checkout Reflection",
                "German Migros Appenzell scripted scene 3 agent for checkout reflection film takes.",
                "GIGI Migros Appenzell scene 3 checkout reflection",
                "GIGI Migros Appenzell scene 3 complete",
                List.of("demo.gigi.scene_3", "demo.gigi.checkout_reflection"),
                List.of(
                        Event.TYPE_SOCIAL_CONTEXT,
                        Event.TYPE_SOCIAL_SITUATION_CHANGE));
    }

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
