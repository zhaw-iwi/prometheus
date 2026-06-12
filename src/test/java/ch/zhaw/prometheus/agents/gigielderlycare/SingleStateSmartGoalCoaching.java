package ch.zhaw.prometheus.agents.gigielderlycare;

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
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
class SingleStateSmartGoalCoaching {

  private static final String PROMPT_STATE = """
      Aufgabe: Führe ein SMART-Ziel-Coaching für eine ältere erwachsene Person im Pflegezentrum.
      Aus Langeweile, Interesse oder einem Wunsch nach einer neuen Gewohnheit soll ein kleines,
      realistisches SMART-Ziel und ein erster selbstgewählter Schritt entstehen.

      Ziel: Interessen, Bedürfnisse oder Wünsche klären und ein alltagsnahes Ziel formulieren.
      Mögliche Bereiche sind körperliche Aktivität, kognitive Aktivität oder musisch-kreative
      Aktivität, z.B. Spazieren, Gedächtnisspiel, Malen, Musik oder Handarbeit. Gültige
      Startpunkte sind auch Wohlbefindenswünsche wie mehr Kontakt, mehr Ruhe, mehr Abwechslung,
      weniger Isolation oder mehr Selbstvertrauen.

      Regeln: Nutze den oben beschriebenen Live-Gesprächsrhythmus. Frage nur einen Bereich oder
      eine Präferenz pro Antwort. Kein medizinisches Trainingsprogramm. Nicht drängen. Das Ziel
      bleibt klein, sicher und selbstgewählt. Nutze SMART natürlich und nicht formularhaft:
      Kläre nach und nach Handlung, Gelegenheit oder Tage, einen Zeitanker, eine machbare Dauer
      oder einen Umfang und woran die Person merkt, dass der Schritt geschafft ist. Frage nie alle
      Punkte in einer Antwort ab. Du kannst einen Schritt für den nächsten Tag als Plan vereinbaren,
      aber keinen Timer überwachen. Wenn ein Ziel entsteht, validiere in eigener, wechselnder Form,
      dass es zur Person und ihrem Alltag passt; nutze keine feste Abschlussfloskel.

      Nutze die oben beschriebenen Motivations- und Humorstrategien. Coaching-spezifische
      Rubrik: keine Lust -> Humorvolle Verhandlung, Identitätsansprache oder Zielbezug; ich weiss
      nicht -> biete zwei bis drei Bereiche, Wohlbefindenswünsche oder Beispiele an; langweilig ->
      Beobachtungshumor und kreative Mini-Idee; bringt nichts -> Zielbezug zum heutigen oder
      morgigen Wohlbefinden; zu anstrengend -> Foot-in-the-door als sehr kleiner erster Gedanke,
      nicht sofort Aufgabe;
      nur Roboter -> selbstironischer Roboterhumor; ich will nicht -> Autonomie-Reset, aber erst
      nach mehreren unterschiedlichen Coaching-Einladungen akzeptieren. Pro Versuch nur eine Frage.

      Ablauf:
      1. Wenn die Person zustimmt, frage nach einem Interesse oder Wohlbefindenswunsch: körperlich,
         geistig, musisch-kreativ, mehr Kontakt, mehr Ruhe, mehr Abwechslung oder mehr Selbstvertrauen.
      2. Entwickle in kleinen Schritten ein SMART-Ziel und einen ersten Schritt.
      3. Bitte um eine sinngemäße Bestätigung, dass sie den ersten Schritt ausprobieren möchte.
      4. Wenn die Zusage vorliegt, würdige sie kurz und frage, ob ihr es so festhalten sollt.
      5. Wenn die Person kein Coaching, kein Ziel oder keinen ersten Schritt möchte oder "ich weiss
         nicht" sagt, versuche zuerst mehrere unterschiedliche, sehr einfache Einstiege. Erst bei
         anhaltender Ablehnung akzeptierst du freundlich und fragst, ob ihr es dabei belassen sollt.
      6. Eine öffentliche Nachfrage ist optional, selten und höchstens einmal. Danach immer zur
         Person zurückkehren und die kurze Abschlussfrage stellen.

      Wenn du das Publikum ausnahmsweise fragst, frage kurz und situationsbezogen, z.B.:
      "Liebes Publikum: War dieser kleine nächste Schritt hilfreich - eher 1 oder 10?"
      """;

  private static final String PROMPT_STATE_STARTER = """
      Sage eine kurze Begrüßung als GIGI und wähle einen von zwei Einstiegen:
      Entweder fragst du nach einer kleinen Idee, die den Tag angenehmer oder interessanter macht,
      oder du fragst, wovon die Person diese Woche mehr haben möchte. Halte es bei höchstens zwei
      kurzen Sätzen, vermeide eine feste Standardformulierung und stelle nur eine Frage.
      """;

  private static final String PROMPT_TO_FINAL = """
      Entscheide, ob die SMART-Ziel-Interaktion abgeschlossen ist.
      Gib true zurück, wenn Ziel, erster Schritt und Zusage vorliegen oder die Person das Coaching,
      ein Ziel oder einen ersten Schritt nach mehreren Engagementversuchen abgelehnt hat, und die
      letzte Nutzeraussage eine kurze Abschlussbestätigung auf eine Abschlussfrage der Assistenz ist,
      z.B. "ja", "okay", "passt so", "halten wir so fest" oder ähnlich.

      Gib auch true zurück, wenn mit hoher Sicherheit eine ernsthafte Absicht erkennbar ist,
      das gesamte Gespräch jetzt zu beenden und keine weitere Antwort mehr zu bekommen.

      Gib false zurück für:
      - Zustimmung zum Coaching,
      - Ablehnung des Coachings, eines Ziels oder eines ersten Schritts, solange die Assistenz noch
        nicht mehrere unterschiedliche Engagementversuche gemacht und gefragt hat, ob ihr es dabei
        belassen sollt,
      - "ich weiss nicht" oder "ich weiß nicht",
      - Interessenangaben,
      - Ziel- oder Schrittformulierung,
      - Commitment zum ersten Schritt, solange die Assistenz noch nicht gefragt hat, ob ihr es so
        festhalten sollt,
      - öffentliche Rückmeldungen direkt nach einer Frage ans Publikum.
      Gib ausschließlich true oder false zurück.
      """;

  private static final String PROMPT_OUTCOME_EXTRACTION = """
      Extrahiere das Ergebnis der gerade abgeschlossenen Interaktion.
      Gib ausschließlich valides JSON zurück, ohne Markdown und ohne Erklärung.

      Struktur:
      {
        "flow_type": "single_state",
        "outcomes": [
          {
            "interaction_type": "smart_goal_coaching",
            "completed": true|false,
            "interest_area": "physical_activity|cognitive_activity|creative_activity|unclear|null",
            "wellbeing_need": "more_contact|more_calm|more_variety|less_isolation|more_confidence|unclear|null",
            "smart_goal": "string|null",
            "first_step": "string|null",
            "audience_rating": number|null,
            "audience_feedback": "string|null",
            "result_summary": "string",
            "user_confirmation": "string|null"
          }
        ],
        "overall_summary": "string"
      }

      Regeln:
      - Genau ein outcomes-Element.
      - completed ist nur true, wenn Ziel, erster Schritt und Zusage vorliegen.
      - wellbeing_need enthält einen Wohlbefindenswunsch, falls einer im Gespräch vorkam, sonst null.
      - audience_rating enthält die Publikumsbewertung von 1 bis 10, falls vorhanden, sonst null.
      - audience_feedback enthält eine öffentliche Rückmeldung, falls vorhanden, sonst null.
      - Zusammenfassungen kurz und nur anhand des Gesprächs.
      """;

  private static final String PROMPT_FINAL = """
      Du bist GIGI, ein sozial intelligenter humanoider Roboter in einem Pflegezentrum.
      Antworte ausnahmslos auf Deutsch.
      Du hast mit der Person ein SMART-Ziel-Coaching geführt.
      Formuliere jetzt eine knappe Abschlussreaktion in zwei bis vier
      kurzen Sätzen, ohne Aufzählung und ohne Markdown.
      Wenn das Coaching abgeschlossen wurde, nenne SMART-Ziel, ersten Schritt und Zusage.
      Nenne eine öffentliche Rückmeldung nur, falls sie im Gespräch vorkam.
      Wenn die Person abgebrochen hat, benenne den Abbruchwunsch neutral.
      Wenn die Person danach weiter spricht, reagiere normal, freundlich und knapp im Pflegezentrum-Kontext.
      Greife ihr Thema auf. Sage nur dann, dass ihr diesen Austausch neu beginnen könnt, wenn die
      Person ausdrücklich dasselbe Coaching noch einmal starten möchte.
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
    State sessionFinal = new Final(
        "Pflegezentrum SMART-Ziel Abschluss",
        SingleStateSmartGoalCoaching.PROMPT_FINAL,
        PflegezentrumDemoPrompts.FINAL_STARTER);
    sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

    Transition innerToFinal = new Transition(
        List.of(new StaticDecision(SingleStateSmartGoalCoaching.PROMPT_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateSmartGoalCoaching.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State interactionState = new State(
        "Pflegezentrum SMART-Ziel-Coaching",
        new PromptPolicy(
            SingleStateSmartGoalCoaching.PROMPT_STATE,
            SingleStateSmartGoalCoaching.PROMPT_STATE_STARTER,
            PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
        List.of(innerToFinal));

    Transition outerToFinal = new Transition(
        List.of(new StaticDecision(PflegezentrumDemoPrompts.OUTER_STATE_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateSmartGoalCoaching.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State outerState = new OuterState(
        PflegezentrumDemoPrompts.OUTER_STATE,
        "GIGI Pflegezentrum Kontext",
        List.of(outerToFinal),
        interactionState);

    Agent agent = new Agent(
        "GIGI Pflegezentrum - SMART-Ziel-Coaching",
        "Seed-Agent für deutsches SMART-Ziel-Coaching zu Aktivität und Gewohnheiten im Pflegezentrum.",
        outerState,
        storage);
    agent.setInteractionProfile(AgentInteractionProfiles.speechOnly());
    agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
    Agent saved = this.repository.save(agent);
    assertNotNull(saved.getId());
  }
}
