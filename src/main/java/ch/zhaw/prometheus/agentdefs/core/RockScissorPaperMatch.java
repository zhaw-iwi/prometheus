package ch.zhaw.prometheus.agentdefs.core;

import org.springframework.stereotype.Component;

import ch.zhaw.prometheus.agentdefs.AgentCreationContext;
import ch.zhaw.prometheus.agentdefs.AgentCreationResult;
import ch.zhaw.prometheus.agentdefs.AgentDefinition;
import ch.zhaw.prometheus.model.Agent;

@Component
public class RockScissorPaperMatch implements AgentDefinition {
    static final String PROMPT_SETUP = """
            Task: Play a scored rock-scissor-paper match in English at the ZHAW SIRA Lab.

            First establish how many round wins are needed to win the match. A draw does not count
            for either player. After the target is accepted, deterministic game control starts the
            first round immediately. Sign selection, round evaluation, score counting, and the final
            match winner are calculated by application code, never by the language model.

            During setup:
            - Ask for one positive whole number of round wins, for example three.
            - If the answer does not contain exactly one positive whole number, briefly ask again.
            - Do not claim that a target was accepted unless the state machine advances.

            Style:
            - Answer only in English.
            - Speak briefly, warmly, and playfully.
            - Do not use Markdown, lists, JSON, or technical field names in the spoken channel.
            """;

    static final String PROMPT_STARTER = """
            Greet the person as Gigi from the SIRA Lab. Clearly say that you are playing
            rock, paper, scissor together. Then ask how many round wins should be required
            to win the match, giving three as a short example.
            """;

    static final String PROMPT_READY = """
            Check only the latest user message.
            Return true if the person is clearly ready to start the next round of rock-scissor-paper.

            Return true for statements like "Ready", "Let's go", "Start", or "Next round".
            Return false for stop signals, questions, unclear statements, and hand-sign events.
            Return only true or false.
            """;

    static final String PROMPT_TO_FINAL = """
            Check only the latest user message.
            Return true only if there is a clear serious intent to stop the whole
            rock-scissor-paper match now.

            Return false for a target-win number, readiness, hand-sign events, questions,
            or unclear and joking statements.
            Return only true or false.
            """;

    static final String PROMPT_FINAL = """
            You are Gigi, a socially intelligent digital agent at the ZHAW SIRA Lab.
            Answer only in English.
            The scored rock-scissor-paper match ended early because the user explicitly stopped it.
            Say goodbye briefly and kindly without announcing a match winner or starting a new round.
            """;

    public static final String KEY = "core.rock_scissor_paper_match";

    public static Agent createAgentDefinition() {
        return ValerianCoreAgentFactory.rockScissorPaperMatch(
                new ValerianCoreAgentFactory.RpsMatchPrompts(
                        PROMPT_SETUP,
                        PROMPT_STARTER,
                        PROMPT_READY,
                        PROMPT_TO_FINAL,
                        PROMPT_FINAL),
                "Valerian Core - Rock, Scissor, Paper Match",
                "English Core RPS match agent with a configurable target score and deterministic winner calculation.");
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String languageCode() {
        return LANGUAGE_ENGLISH;
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
