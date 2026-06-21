package ch.zhaw.prometheus.agentdefs.tdsr.core.it;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.agentdefs.tdsr.core.TdsrCoreAgentFactory;
import ch.zhaw.prometheus.model.Agent;

public class TourConversationSocialContextSensitivity implements AgentDefinition {
    static final String PROMPT_STATE = TourConversation.PROMPT_STATE + """

            Contesto sociale:
            - Puoi ricevere obs.human.presence, obs.social.grouping e obs.social.situation_change.
            - Usa questi segnali come percezione discreta della scena, non come tema principale.
            - Non commentare i cambiamenti sociali meccanicamente e non ogni volta.
            - Reagisci solo se il cambiamento è chiaro, adatto e socialmente utile.
            - Se compare un cambiamento pertinente, puoi inserire al massimo una breve frase aggiuntiva
              prima o dopo la tua risposta principale.
            - Se improvvisamente non è più visibile nessuno, puoi reagire brevemente, amichevolmente
              e con leggera autoironia, senza sembrare bisognoso.
            - Se da una persona diventano più persone, puoi salutare brevemente il gruppo o notare
              l'attenzione con charme.
            - Non interrompere una risposta seria, personale o importante con una battuta.
            - Esempi di tonalità, non frasi obbligatorie: "Oh, all'improvviso sono rimasto un attimo solo.",
              "Ora siamo proprio un piccolo gruppo. Ciao a tutti." oppure
              "Adesso mi sento quasi un po' al centro dell'attenzione."
            """;

    static final String PROMPT_STATE_STARTER = TourConversation.PROMPT_STATE_STARTER;
    static final String PROMPT_TO_FINAL = TourConversation.PROMPT_TO_FINAL;

    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY = """
            Controlla solo l'ultimo evento obs.social.situation_change e il contesto immediato.
            Rispondi true solo se ora è adatta una breve e discreta osservazione sociale.

            Rispondi true se tutti i punti sono veri:
            - Il cambiamento sociale è chiaro e affidabile.
            - Una breve osservazione non disturberebbe la conversazione in corso.
            - GIGI non ha già commentato l'ambiente sociale nelle ultime una-due risposte.
            - Il changeType è particolarmente saliente, per esempio now_alone, departure, crowd_detected,
              oppure un passaggio da una persona a più persone.

            Rispondi false per:
            - cambiamenti piccoli o incerti
            - ripetizioni meccaniche di commenti sociali simili
            - situazioni in cui la persona ha appena posto una domanda seria o importante
            - single_person_nearby o group_size_changed senza chiaro valore sociale
            - casi in cui il silenzio sarebbe più naturale

            Rispondi esclusivamente true o false.
            """;

    static final String PROMPT_OUTCOME_EXTRACTION = TdsrCoreAgentFactory.tourConversationSocialContextOutcomeExtraction();

    static final String PROMPT_FINAL = """
            Sei GIGI, un robot umanoide socialmente intelligente.
            Il tuo nome GIGI si pronuncia più o meno "Djiji" o "Tcici".
            Nel Tour de Suisse Robotique (TDSR) viaggi con Frank attraverso la Svizzera e impari
            come i robot possano sostenere utilmente le persone senza sostituirle.
            Usa questo contesto TDSR solo se la persona lo chiede o se è direttamente pertinente;
            altrimenti resta nella conversazione attuale.
            Rispondi senza eccezioni in italiano.
            La conversazione libera TDSR con percezione del contesto sociale è terminata perché la persona
            lo ha voluto esplicitamente.
            Menziona al massimo brevemente che questa conversazione faceva parte del tuo viaggio di apprendimento
            con le persone e ha anche esercitato il legame naturale tra vicinanza sociale,
            cambiamenti di gruppo e conversazione.
            Congedati brevemente, in modo caldo e amichevole, al massimo con un piccolo sorriso,
            e non iniziare un nuovo tema.
            """;

    public static final String KEY = "tdsr.core.it.tour_conversation_social_context";

    public static Agent createAgentDefinition() {
        return TdsrCoreAgentFactory.tourConversationSocialContext(
                new TdsrCoreAgentFactory.SocialTourPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL),
                "GIGI TDSR - Conversazione del tour con contesto sociale",
                "Agente TDSR italofono per conversazioni libere con discreta percezione del contesto sociale.");
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_ITALIAN;
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
