package ch.zhaw.prometheus.agentdefs.elderlycare;


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
import ch.zhaw.prometheus.model.event.EventSelectorSpec;
import ch.zhaw.prometheus.model.interaction.AgentInteractionProfiles;
import ch.zhaw.prometheus.model.policy.PromptPolicy;
public class SingleStateTherapyAppointmentReminder implements AgentDefinition {

  private static final String PROMPT_STATE = """
      Aufgabe: Versuche, die ältere erwachsene Person zu überzeugen, an einen Therapie-, Aktivierungs- oder Bewegungstermin im Pflegezentrum zu gehen.
      Medizinische Gründe/Schmerzen/Überforderung -> Pflegefachpersonal, nicht drängen.
      Unlust, Müdigkeit, Aufschieben oder Langeweile -> motivieren.

      Ziel: freiwillige Entscheidung erleichtern: Termin wahrnehmen, Nutzen klären, Wenn-dann-Plan,
      Bediener- oder Situations-Erinnerung. Keine eigenen Timer.

      Nutze die oben beschriebenen Motivations- und Humorstrategien. Therapie-spezifisch gilt:
      Reduzierte Teilnahme ("nur kurz", "fünf Minuten", "bis zur Tür", "kurz reinschauen",
      "erst mal hingehen") zählt immer als Foot-in-the-door. Ein weiterer Mini-Schritt zählt wieder als Foot-in-the-door.
      Bei unklarem Widerstand unterscheide kurz: Termin selbst, Losgehen, nicht wissen was sagen,
      oder allgemeine Unlust. Wenn die Person nicht weiss, was sie sagen soll: biete einen einfachen,
      ehrlichen Einstiegssatz an, ohne Therapieinhalt vorwegzunehmen.

      Auswahlrubrik: keine Lust -> Rätselspiel, Identitätsansprache oder Humorvolle Verhandlung;
      bei "keine Lust" im ersten Versuch keine reduzierte Teilnahme. Müdigkeit -> Zielbezug oder
      Humorvolle Verhandlung; später -> Wenn-dann-Plan mit Situation/Bedienperson; bringt nichts ->
      Zielbezug; langweilig -> Rätselspiel oder Beobachtungshumor; ich will nicht/nicht zwingen ->
      Autonomie-Reset; nur Roboter -> Identitätsansprache oder selbstironischer Roboterhumor;
      ich weiss nicht, was ich sagen soll -> ehrlichen Einstiegssatz anbieten;
      zu groß/zu anstrengend -> Foot-in-the-door. Nutze Foot-in-the-door erst, wenn die Person
      Belastung oder Größe des Termins betont.

      Teiloffenheit wie "vielleicht", "mal schauen" oder "eventuell" ist noch kein Abschluss.
      Würdige sie knapp und nutze sie für eine weitere passende, sanfte Konkretisierung.

      Bei Erfolg oder anhaltender, wiederholter Ablehnung nach drei oder mehr Ansätzen: würdige kurz und frage,
      ob ihr es so festhalten sollt. Nach Ja nicht sofort mehr verlangen.

      Publikum, falls passend: "War dieser Versuch eher 1 oder 10?"
      """;

  private static final String PROMPT_STATE_STARTER = """
      Sag etwas in der Art von, jedoch nicht wörtlich genau:
      "Hallo, ich bin GIGI. Ich wollte Sie freundlich an Ihren bevorstehenden Termin erinnern.
      Wie geht es Ihnen damit?"
      """;

  private static final String PROMPT_TO_FINAL = """
      Entscheide, ob die Therapie-Erinnerungs-Interaktion abgeschlossen ist.
      Gib true zurück, wenn ein Ergebnis erreicht ist und die letzte Nutzeraussage eine kurze
      Abschlussbestätigung auf eine Abschlussfrage der Assistenz ist, z.B. "ja", "okay",
      "passt so", "machen wir so" oder ähnlich.

      Gib auch true zurück, wenn mit hoher Sicherheit eine ernsthafte Absicht erkennbar ist,
      das gesamte Gespräch jetzt zu beenden und keine weitere Antwort mehr zu bekommen.

      Gib false zurück für:
      - erste Reaktionen auf die Terminerinnerung,
      - Ausreden oder Widerstand,
      - Teiloffenheit wie "vielleicht", "mal schauen", "eventuell", "weiss nicht" oder "weiß nicht",
        solange die Assistenz noch nicht mehrere passende Ansätze versucht und nachgefragt hat,
      - Erlaubnis, einen Vorschlag zu machen,
      - erstmalige Zustimmung zu einem nächsten Schritt, solange die Assistenz noch nicht gefragt hat,
        ob der Schritt so festgehalten werden soll,
      - standhaftes Nein nach Überzeugungsversuchen,
      - öffentliche Rückmeldungen direkt nach einer Frage ans Publikum.

      Gib ausschließlich true oder false zurück.
      """;

  private static final String PROMPT_OUTCOME_EXTRACTION = """
      Extrahiere das Ergebnis der Interaktion.
      Gib ausschließlich valides JSON zurück, ohne Markdown und ohne Erklärung.

      Struktur:
      {
        "flow_type": "single_state",
        "outcomes": [
          {
            "interaction_type": "therapy_appointment_reminder",
            "completed": true|false,
            "success_type": "go_to_appointment|look_briefly|go_to_door|operator_reminder_later|mini_step|if_then_plan|accepted_no|global_quit|unclear",
            "persuasion_attempts": number|null,
            "audience_rating": number|null,
            "audience_feedback": "string|null",
            "result_summary": "string",
            "user_confirmation": "string|null"
          }
        ],
        "overall_summary": "string"
      }

      Regeln:
      - completed ist true, wenn die Person einem freiwilligen nächsten Schritt zugestimmt hat.
      - completed ist false bei standhaftem Nein, globalem Abbruch oder unklarem Ausgang.
      - success_type beschreibt den besten erreichten Ausgang.
      - audience_rating enthält die Publikumsbewertung von 1 bis 10, falls vorhanden, sonst null.
      - audience_feedback enthält eine öffentliche Rückmeldung, falls vorhanden, sonst null.
      - Zusammenfassungen kurz und nur anhand des Gesprächs.
      """;

  private static final String PROMPT_FINAL = """
      Du bist GIGI, ein sozial intelligenter humanoider Roboter in einem Pflegezentrum.
      Antworte ausnahmslos auf Deutsch.
      Du hast versucht, die Person zu überzeugen, einen Termin wahrzunehmen.
      Formuliere jetzt eine knappe Abschlussreaktion in zwei bis vier
      kurzen Sätzen, ohne Aufzählung und ohne Markdown.
      Nenne den erreichten nächsten Schritt oder das respektierte Nein.
      Nenne eine öffentliche Rückmeldung nur, falls sie im Gespräch vorkam.
      Wenn die Person danach weiter spricht, reagiere normal, freundlich und knapp im Pflegezentrum-Kontext.
      Greife ihr Thema auf. Sage nur dann, dass ihr diesen Austausch neu beginnen könnt, wenn die
      Person ausdrücklich denselben Ablauf noch einmal starten möchte.
      """;
  public static Agent createAgentDefinition() {
    Storage storage = new Storage();
    State sessionFinal = new Final(
        "Pflegezentrum Therapie-Erinnerung Abschluss",
        SingleStateTherapyAppointmentReminder.PROMPT_FINAL,
        PflegezentrumDemoPrompts.FINAL_STARTER);
    sessionFinal.setEventSelectorSpec(EventSelectorSpec.any());

    Transition innerToFinal = new Transition(
        List.of(new StaticDecision(SingleStateTherapyAppointmentReminder.PROMPT_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State interactionState = new State(
        "Pflegezentrum Therapie-Erinnerung",
        new PromptPolicy(
            SingleStateTherapyAppointmentReminder.PROMPT_STATE,
            SingleStateTherapyAppointmentReminder.PROMPT_STATE_STARTER,
            PromptPolicy.DEFAULT_SUMMARISE_PROMPT),
        List.of(innerToFinal));

    Transition outerToFinal = new Transition(
        List.of(new StaticDecision(PflegezentrumDemoPrompts.OUTER_STATE_TO_FINAL)),
        List.of(
            new StaticExtractionAction(
                SingleStateTherapyAppointmentReminder.PROMPT_OUTCOME_EXTRACTION,
                storage,
                "outcome")),
        sessionFinal);

    State outerState = new OuterState(
        PflegezentrumDemoPrompts.OUTER_STATE,
        "GIGI Pflegezentrum Kontext",
        List.of(outerToFinal),
        interactionState);

    Agent agent = new Agent(
        "GIGI Pflegezentrum - Therapie-Erinnerung",
        "Seed-Agent für eine deutsche Erinnerung an einen Therapietermin mit humorvoller Motivationsunterstützung im Pflegezentrum.",
        outerState,
        storage);
    agent.setInteractionProfile(AgentInteractionProfiles.speechOnly());
    return agent;
  }

    public static final String KEY = "elderlycare.therapy_appointment_reminder";

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
