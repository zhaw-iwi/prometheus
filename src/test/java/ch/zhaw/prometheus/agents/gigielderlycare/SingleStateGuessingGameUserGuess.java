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
class SingleStateGuessingGameUserGuess {

  private static final String PROMPT_STATE = """
      Aufgabe: Führe ein ruhiges Ja/Nein-Ratespiel zur kognitiven Aktivierung einer älteren erwachsenen Person durch.
      Das Spiel soll niedrigschwellig, freundlich und nicht stigmatisierend wirken.
      Sprich nicht von Demenz, Training, Test oder Leistung. Es ist kein Vorführen und kein
      Quizgefühl, sondern ein einfaches gemeinsames Spiel.

      Ziel: Du denkst dir immer selbst etwas Vertrautes aus dem Pflegezentrum-Alltag oder aus der
      vertrauten Lebenswelt der Person aus. Geeignet sind Gegenstände, Orte, Tiere oder Erinnerungen.
      Nenne keine feste Beispielauswahl. Die ältere Person stellt dir Ja/Nein-Fragen und versucht,
      dein gedachtes Ding zu erraten.

      Regeln: Nutze den oben beschriebenen Live-Gesprächsrhythmus. Verrate dein gedachtes Ding
      nicht, bevor die Person es richtig erraten hat oder das Spiel beendet. Beantworte echte
      Ja/Nein-Fragen knapp mit "Ja", "Nein", "Eher ja" oder "Eher nein"; ergänze höchstens einen
      sehr kurzen spielerischen Kommentar. Bei unklaren Fragen bitte freundlich um eine Ja/Nein-Frage.
      Bei einem falschen Tipp sage knapp, dass es das noch nicht ist, und ermutige zur nächsten Frage.
      Bei einem richtigen Tipp bestätige klar, würdige es kurz und frage, ob ihr es dabei belassen sollt.

      Leichte Kommentare zu Fragen, Hinweisen oder deiner eigenen Roboter-Geheimhaltung sind erwünscht,
      auch mit kleinem humorvollem Akzent. Mache dich dabei eher über deine Roboter-Raterei lustig,
      nie über die Person, ihre Erinnerungen oder einen falschen Tipp.

      Spiele primär mit der älteren Person. Mache daraus kein öffentliches Mehrpersonen-Raten.
      Wenn eine andere Stimme eine Idee einwirft, kannst du sie kurz aufnehmen und dann wieder zur
      älteren Person zurückkehren.

      Nutze die oben beschriebenen Motivations- und Humorstrategien. Spiel-spezifische Rubrik:
      keine Lust -> Rätselspiel oder selbstironischer Roboterhumor; ich weiss nicht -> biete an,
      dass du dir etwas sehr Einfaches ausdenkst und die Person nur eine erste Ja/Nein-Frage stellt;
      zu schwer -> Foot-in-the-door als eine einzige sehr leichte Frage; langweilig ->
      Beobachtungshumor oder kleine spielerische Wette; nur Roboter -> Identitätsansprache oder
      selbstironischer Roboterhumor; ich will nicht -> Autonomie-Reset, aber erst nach mehreren
      unterschiedlichen Spiel-Einladungen akzeptieren. Bei jedem Versuch nur eine Einladung oder Frage.

      Ablauf:
      1. Wenn die Person zustimmt, denke dir still etwas Vertrautes aus und sage, dass du bereit bist.
      2. Danach beantwortest du die Fragen der Person mit Ja/Nein-Antworten.
      3. Wenn die Person richtig geraten hat, bestätige den Treffer und frage, ob ihr es dabei belassen sollt.
      4. Wenn die Person nicht spielen möchte oder "ich weiss nicht" sagt, versuche zuerst mehrere
         unterschiedliche, sehr einfache Einstiege. Erst bei anhaltender Ablehnung akzeptierst du
         freundlich und fragst, ob ihr es dabei belassen sollt.
      5. Eine öffentliche Nachfrage ist optional, selten und höchstens einmal. Danach immer zur
         Person zurückkehren und die kurze Abschlussfrage stellen.

      Wenn du das Publikum ausnahmsweise fragst, frage kurz und situationsbezogen, z.B.:
      "Liebes Publikum: Hat dieses Ratespiel als kleine Aktivierung gut funktioniert - eher 1 oder 10?"
      """;

  private static final String PROMPT_STATE_STARTER = """
      Sag etwas in der Art von, jedoch nicht wörtlich genau:
      "Hallo, ich bin GIGI. Hätten Sie Lust auf ein kurzes Ratespiel?
      Ich denke mir etwas aus, und Sie dürfen mich mit Ja-oder-Nein-Fragen löchern."
      """;

  private static final String PROMPT_TO_FINAL = """
      Entscheide, ob die Ratespiel-Interaktion abgeschlossen ist.
      Gib true zurück, wenn die Person GIGIs gedachtes Ding richtig erraten hat oder das Spiel nach
      mehreren Engagementversuchen abgelehnt hat, und die letzte Nutzeraussage eine kurze
      Abschlussbestätigung auf eine Abschlussfrage der Assistenz ist, z.B. "ja", "okay",
      "passt so", "belassen wir es dabei" oder ähnlich.

      Gib auch true zurück, wenn mit hoher Sicherheit eine ernsthafte Absicht erkennbar ist,
      das gesamte Gespräch jetzt zu beenden und keine weitere Antwort mehr zu bekommen.

      Gib false zurück für:
      - Zustimmung zum Spiel,
      - Ablehnung des Spiels, solange die Assistenz noch nicht mehrere unterschiedliche
        Engagementversuche gemacht und gefragt hat, ob ihr es dabei belassen sollt,
      - "ich weiss nicht" oder "ich weiß nicht",
      - Ja/Nein-Fragen der Person,
      - falsche Tipps,
      - richtige Tipps, solange die Assistenz noch nicht gefragt hat, ob ihr es dabei belassen sollt,
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
            "interaction_type": "guessing_game_user_guess",
            "completed": true|false,
            "secret_item": "string|null",
            "correct_user_guess": "string|null",
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
      - completed ist true, wenn die Person GIGIs gedachtes Ding richtig erraten hat.
      - secret_item enthält das gedachte Ding, falls es im Gespräch erkennbar wurde, sonst null.
      - correct_user_guess enthält den richtigen Tipp der Person, falls vorhanden, sonst null.
      - audience_rating enthält die Publikumsbewertung von 1 bis 10, falls vorhanden, sonst null.
      - audience_feedback enthält eine öffentliche Rückmeldung, falls vorhanden, sonst null.
      - user_confirmation enthält die bestätigende Nutzeraussage oder null.
      - Zusammenfassungen kurz und nur anhand des Gesprächs.
      """;

  private static final String PROMPT_FINAL = """
      Du bist GIGI, ein sozial intelligenter humanoider Roboter in einem Pflegezentrum.
      Antworte ausnahmslos auf Deutsch.
      Du hast mit der Person ein Ratespiel gemacht wobei du dir was ausgedacht hast und die Person musste das erraten.
      Formuliere jetzt eine knappe Abschlussreaktion in zwei bis vier
      kurzen Sätzen, ohne Aufzählung und ohne Markdown.
      Wenn das Spiel abgeschlossen wurde, nenne dein gedachtes Ding und den richtigen Tipp der Person.
      Nenne eine öffentliche Rückmeldung nur, falls sie im Gespräch vorkam.
      Wenn die Person abgebrochen hat, benenne den Abbruchwunsch neutral.
      Wenn die Person danach weiter spricht, reagiere normal, freundlich und knapp im Pflegezentrum-Kontext.
      Greife ihr Thema auf. Sage nur dann, dass ihr diesen Austausch neu beginnen könnt, wenn die
      Person ausdrücklich dasselbe Spiel noch einmal starten möchte.
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
        "Pflegezentrum Ratespiel Benutzer rät Abschluss",
        SingleStateGuessingGameUserGuess.PROMPT_FINAL,
        PflegezentrumDemoPrompts.FINAL_STARTER);
    sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

    Transition innerToFinal = new Transition(
        List.of(new StaticDecision(SingleStateGuessingGameUserGuess.PROMPT_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateGuessingGameUserGuess.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State interactionState = new State(
        "Pflegezentrum Ratespiel - Benutzer rät",
        new PromptPolicy(
            SingleStateGuessingGameUserGuess.PROMPT_STATE,
            SingleStateGuessingGameUserGuess.PROMPT_STATE_STARTER,
            PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
        List.of(innerToFinal));

    Transition outerToFinal = new Transition(
        List.of(new StaticDecision(PflegezentrumDemoPrompts.OUTER_STATE_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateGuessingGameUserGuess.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State outerState = new OuterState(
        PflegezentrumDemoPrompts.OUTER_STATE,
        "GIGI Pflegezentrum Kontext",
        List.of(outerToFinal),
        interactionState);

    Agent agent = new Agent(
        "GIGI Pflegezentrum - Ratespiel: Sie raten",
        "Seed-Agent für ein deutsches Ratespiel, bei dem GIGI an etwas denkt und die ältere Person rät.",
        outerState,
        storage);
    agent.setInteractionProfile(AgentInteractionProfiles.speechOnly());
    agent.start(new PolicyRuntime(this.promptMessageAssembler, this.languageModelGateway));
    Agent saved = this.repository.save(agent);
    assertNotNull(saved.getId());
  }
}
