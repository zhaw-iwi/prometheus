package ch.zhaw.prometheus.application;

import java.util.ArrayList;
import java.util.List;

import ch.zhaw.prometheus.controllers.views.PolicyResponseView;
import ch.zhaw.prometheus.model.policy.PromptMessage;

final class RealtimePromptInstructions {
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

}
