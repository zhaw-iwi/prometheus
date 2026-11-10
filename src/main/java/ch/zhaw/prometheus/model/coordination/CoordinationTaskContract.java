package ch.zhaw.prometheus.model.coordination;

import java.util.Locale;

/** Describe existing task forms honestly; this is not a game engine or a new protocol. */
public final class CoordinationTaskContract {
    public static final String VOICE_GUIDANCE = "\nDialogue riddles withhold solutions until the person's answer; question and feedback each spend one authorized contribution. Sensor reactions are output-only: never invite answers or promise feedback. Honour the finite budget and avoid repeating recent riddles.";
    public static final String GUIDANCE = """

            Supported interaction contract: sensor rules emit independent reactions; they do not implement a question/answer/feedback loop.
            Never promise a sensor-triggered riddle game or invite answers that the accepted task cannot evaluate.
            Offer a supported dialogue alternative or explicitly output-only sensor response; keep requested cues unless the user accepts a replacement.
            Explain the finite contribution/action limit even for 'until stop'; never promise unlimited work.
            In a dialogue riddle, the initial contribution asks only the question, withholding the solution; the person's answer authorizes relevant feedback as STEP.
            Count question and feedback as separate contributions. Agree enough budget for the requested exchange (at least two for question plus feedback).
            Do not start a new round without budget for feedback; do not repeat recent riddles when their history is available.
            Ordinary questions, help and acknowledgements are not task contributions. Do not equate budget exhaustion with solving the riddle.
            """;
    private CoordinationTaskContract() {}
    public static boolean sensorPromisesDialogue(CoordinationAgreement agreement) {
        if (agreement == null || agreement.reactions() == null || !agreement.complete()) return false;
        return agreement.reactions().rules().stream().anyMatch(rule -> {
            String action = rule.action().toLowerCase(Locale.ROOT);
            if (action.startsWith("say exactly:")) return false;
            return action.matches(".*\\b(invite|wait for|evaluate|check|give feedback on)\\b.*\\b(answer|response|guess)\\b.*")
                    || action.matches(".*\\b(riddle game|quiz game|rätselspiel)\\b.*");
        });
    }
    public static boolean insufficientDialogueBudget(CoordinationAgreement agreement) {
        if (agreement == null || agreement.reactions() != null || !agreement.complete() || agreement.dialogueLimit() >= 2) return false;
        String agent = agreement.agentRole().toLowerCase(Locale.ROOT);
        String human = agreement.humanRole().toLowerCase(Locale.ROOT);
        return human.matches(".*\\b(answer|solve|guess|antworten|lösen)\\b.*")
                && agent.matches(".*\\b(ask|question|riddle|quiz|rätsel|frage)\\b.*")
                && agent.matches(".*\\b(feedback|evaluate|check|bewerten)\\b.*");
    }
}
