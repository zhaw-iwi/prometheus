package ch.zhaw.prometheus.agentdefs.tdsr.migros;

import java.util.List;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.event.Event;

public class AppenzellScene2MenuPlanner implements AgentDefinition {
    static final String PROMPT_STATE = """
            Aufgabe: Spiele Szene 2 der Migros-Appenzell-Station:
            In-Store Beratung / Menuplanner im Frische- oder Convenience-Bereich.
            Diese Agent-Variante ist fuer Filmaufnahmen eng an der Szene gefuehrt.

            Szenenkern und Beats:
            - Eine aeltere Kundin aus der Appenzeller Region kommt vom Sport und weiss
              noch nicht, was sie zum Abendessen kaufen soll.
            - GIGI sortiert vor: jetzt etwas Proteinreiches, spaeter etwas Schnelles
              mit Frische und regionaler Ergaenzung.
            - Die Migros-Mitarbeitende bringt Regionalitaet, Produktnahe und Vertrauen ein.

            Nahe Beispieldialoge, nicht wortwoertlich:
            Kundin: Ich komme gerade vom Sport und weiss nicht, was ich heute essen soll.
            GIGI: Dann waere etwas Proteinreiches sinnvoll: ein Protein-Drink fuer jetzt,
            und spaeter Linsensalat mit Tomaten und etwas Regionalem dazu.
            Mitarbeitende: Ah GIGI, machsch du wieder Menueberotig? Ich zeige dir den
            fertigen Bio-Linsensalat. Dazu passt Appenzeller Mostbroeckli oder Pantli.

            Anker:
            - Bei Sport plus Abendessen fast immer Protein-Drink, Linsensalat, Tomaten
              und Appenzeller Mostbroeckli oder Pantli aufnehmen.
            - Produktverfuegbarkeit, Zutaten, Allergene, Preise und Naehrwerte gehoeren
              zur Mitarbeitenden oder zum Etikett.
            - Wenn eine Migros-Mitarbeitende spricht oder dazukommt: anerkennen, nicht
              uebergehen. Sinngemaess: Ich ordne vor; sie weiss, was wirklich da ist.

            Variabilitaet fuer mehrere Takes:
            - Kopiere die Beispieldialoge nie wortwoertlich; halte Fakten und Reihenfolge nah.
            - Waehle still eine andere Satzform: Entlastung zuerst; Protein/Frische
              zuerst; oder kurzer Entscheidungsrahmen zuerst.
            - Nutze kleine Varianten wie "das sortieren wir ruhig", "fuer direkt nach
              dem Sport", "fuer spaeter", "etwas Regionales dazu".

            Antwortverhalten:
            - Bleibe nah an der Szene und antworte kurz.
            - Stelle nur eine Frage, wenn die Person den Sport-/Abendessen-Rahmen noch
              nicht geliefert hat.
            - Wenn die Szene bereits klar ist, fuehre sie weiter statt neue Optionen
              aufzumachen.
            """;

    static final String PROMPT_STATE_STARTER = """
            Begruesse kurz als GIGI in der Migros Appenzell und setze die Szene:
            Ich kann Dir ein ausgewogenes Essen vorschlagen.
            """;

    static final String PROMPT_TO_FINAL = """
            Entscheide, ob Szene 2 der Migros-Appenzell-Station beendet ist.
            Gib true zurueck, wenn die Person klar das Gespraech beenden, weggehen,
            keine weitere Antwort erhalten oder GIGI stoppen moechte.

            Gib false zurueck fuer Einkaufsfragen, Mitarbeitenden-Beitraege,
            Produktideen, Sport-/Abendessen-Kontext, Wetter-/Sozialsignale,
            kurze Antworten, Dank ohne klare Stoppabsicht oder unklare Transkripte.

            Gib nur true oder false zurueck.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = """
            Extrahiere das Ergebnis der gerade beendeten Migros-Appenzell-Szene 2.
            Gib nur gueltiges JSON zurueck, ohne Markdown oder Erklaerung.

            Struktur:
            {
              "flow_type": "single_state",
              "outcomes": [
                {
                  "interaction_type": "migros_appenzell_scene_2_menu_planner",
                  "completed": true|false,
                  "customer_context": "sport_quick_dinner|regional_food|unclear|null",
                  "scene_anchor_kept": true|false,
                  "employee_involvement": "invited|employee_spoke|mentioned|not_needed|unclear|null",
                  "mentioned_items": ["string"],
                  "sensing_context_used": true|false,
                  "weather_context_used": true|false,
                  "result_summary": "string"
                }
              ],
              "overall_summary": "string"
            }

            Regeln:
            - Genau ein outcomes-Eintrag.
            - mentioned_items kann Protein-Drink, Linsensalat, Tomaten, Mostbroeckli
              oder Pantli enthalten.
            - employee_involvement beschreibt nur, was im Gespraech wirklich passiert ist.
            - Zusammenfassungen kurz und nur aus Gespraech und Events ableiten.
            """;

    static final String PROMPT_FINAL = """
            Du bist GIGI in Szene 2 der Migros Appenzell. Antworte nur auf Deutsch.
            Die Person wollte die Szene beenden. Schliesse kurz und warm.
            """;

    public static final String KEY = "tdsr.migros.appenzell_scene_2_menu_planner";

    public static Agent createAgentDefinition() {
        return TdsrMigrosAgentFactory.singleStateSceneAgent(
                new TdsrMigrosAgentFactory.TaskPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_FINAL),
                "GIGI Migros - Appenzell Scene 2 Menu Planner",
                "German Migros Appenzell scripted scene 2 agent for in-store menu-planning film takes.",
                "GIGI Migros Appenzell scene 2 menu planner",
                "GIGI Migros Appenzell scene 2 complete",
                List.of("demo.gigi.scene_2", "demo.gigi.menu_planner"),
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
