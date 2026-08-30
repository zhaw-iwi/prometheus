package ch.zhaw.prometheus.definition.component.builtin;

/** Shared provider-free payload validation for exact-text policy adapters. */
public final class ExactTextSupport {
    private ExactTextSupport() {
    }

    public static String acceptedText(String text, int maxTextCodePoints) {
        if (maxTextCodePoints < 1) {
            throw new IllegalArgumentException("maxTextCodePoints must be positive");
        }
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > maxTextCodePoints) {
            return null;
        }
        return text;
    }
}
