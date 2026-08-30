package ch.zhaw.prometheus.definition.runtime;

public record RuntimePromptBundle(
        String responsePrompt,
        String starterPrompt,
        String summaryPrompt,
        String nonverbalPlanPrompt,
        String gesturePrompt,
        boolean starting) {
}
