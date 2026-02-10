package ch.zhaw.prometheus.model.policy;

public final class PromptMessage {
    private final String role;
    private final String content;

    private PromptMessage(String role, String content) {
        this.role = role == null ? "user" : role;
        this.content = content == null ? "" : content;
    }

    public static PromptMessage of(String role, String content) {
        return new PromptMessage(role, content);
    }

    public static PromptMessage system(String content) {
        return new PromptMessage("system", content);
    }

    public static PromptMessage user(String content) {
        return new PromptMessage("user", content);
    }

    public static PromptMessage assistant(String content) {
        return new PromptMessage("assistant", content);
    }

    public String getRole() {
        return this.role;
    }

    public String getContent() {
        return this.content;
    }

    @Override
    public String toString() {
        return "{role=\"" + this.role + "\", content=\"" + this.content + "\"}";
    }
}
