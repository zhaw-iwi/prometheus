package ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr;

import ch.zhaw.prometheus.agentdefs.tdsr.shhd.BaseShhdAgentDefinition;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdAgentFactory;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdPromptLibrary;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdPromptLibrary.LanguageProfile;
import ch.zhaw.prometheus.agentdefs.tdsr.shhd.TdsrShhdPromptLibrary.ScenePrompt;

public class EPFLActive extends BaseShhdAgentDefinition {
    private static final LanguageProfile LANGUAGE = TdsrShhdPromptLibrary.FRENCH;
    private static final ScenePrompt SCENE = TdsrShhdPromptLibrary.epflActive(LANGUAGE);

    static final String PROMPT_STATE = TdsrShhdPromptLibrary.statePrompt(LANGUAGE, SCENE);
    static final String PROMPT_STATE_STARTER = TdsrShhdPromptLibrary.starterPrompt(LANGUAGE, SCENE);
    static final String PROMPT_TO_FINAL = LANGUAGE.toFinal();
    static final String PROMPT_OUTCOME_EXTRACTION = TdsrShhdPromptLibrary.outcomeExtractionPrompt(LANGUAGE, SCENE);
    static final String PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY = LANGUAGE.socialInterjectionOpportunity();
    static final String PROMPT_FINAL = TdsrShhdPromptLibrary.finalPrompt(LANGUAGE, SCENE);

    public static final String KEY = "tdsr.shhd.fr.epfl_active";

    public EPFLActive() {
        super(
                KEY,
                LANGUAGE.languageCode(),
                SCENE.agentName(),
                SCENE.agentDescription(),
                SCENE.stateName(),
                new TdsrShhdAgentFactory.ShhdPrompts(
                        PROMPT_STATE,
                        PROMPT_STATE_STARTER,
                        PROMPT_TO_FINAL,
                        PROMPT_OUTCOME_EXTRACTION,
                        PROMPT_SOCIAL_INTERJECTION_OPPORTUNITY,
                        PROMPT_FINAL));
    }
}
