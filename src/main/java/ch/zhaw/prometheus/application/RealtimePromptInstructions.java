package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.policy.PromptMessage;

final class RealtimePromptInstructions {
    private static final String TELEMETRY_INSTRUCTION = "Perception telemetry is provided in the PROMETHEUS prompt "
            + "context. Treat it as available sensor input, keep uncertainty explicit, and do not claim that "
            + "PROMETHEUS cannot perceive a modality when telemetry for that modality is present.";

    private RealtimePromptInstructions() {
    }

    static String systemInstructions(PolicyResponseView prompt) {
        if (prompt == null || prompt.getPromptMessages() == null || prompt.getPromptMessages().isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (PromptMessage message : prompt.getPromptMessages()) {
            if (message == null) {
                continue;
            }
            String role = message.getRole() == null ? "user" : message.getRole();
            String content = message.getContent() == null ? "" : message.getContent();
            String line = role + ": " + content;
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    static String responseInstruction(PolicyResponseView prompt) {
        if (prompt != null && !prompt.isActive()) {
            return "The interaction has ended. Briefly acknowledge and do not continue with new topics. "
                    + TELEMETRY_INSTRUCTION;
        }
        if (prompt == null || prompt.getPromptMessages() == null || prompt.getPromptMessages().size() <= 1
                || (prompt != null && prompt.isStarting())) {
            return "Begin the interaction now. " + TELEMETRY_INSTRUCTION;
        }
        boolean hasUserMessage = prompt.getPromptMessages().stream()
                .anyMatch(message -> message != null && "user".equalsIgnoreCase(message.getRole()));
        if (hasUserMessage) {
            return "Respond to the user's latest message while strictly following the PROMETHEUS system "
                    + "instructions. " + TELEMETRY_INSTRUCTION;
        }
        return "Respond to the latest input while strictly following the PROMETHEUS system instructions. "
                + TELEMETRY_INSTRUCTION;
    }
}
