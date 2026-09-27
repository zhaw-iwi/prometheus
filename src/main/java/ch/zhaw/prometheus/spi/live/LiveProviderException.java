package ch.zhaw.prometheus.spi.live;

import com.google.gson.JsonParser;

public class LiveProviderException extends RuntimeException {
    public enum Reason { UNAVAILABLE, QUOTA_EXHAUSTED, RATE_LIMITED, AUTHENTICATION, ACCESS_DENIED }
    private final Reason reason;
    private final Integer providerStatus;

    public LiveProviderException(String message) { this(message, null); }
    public LiveProviderException(String message, Throwable cause) {
        super(message, cause); reason = Reason.UNAVAILABLE; providerStatus = null;
    }
    private LiveProviderException(int status, Reason reason) {
        super("Live provider rejected request (HTTP " + status + ")");
        this.reason = reason; providerStatus = status;
    }
    public Reason getReason() { return reason; }
    public Integer getProviderStatus() { return providerStatus; }

    /** Retain only an application-owned category, never the provider body or message. */
    public static LiveProviderException rejected(int status, String body) {
        Reason reason = switch (status) {
            case 401 -> Reason.AUTHENTICATION;
            case 403 -> Reason.ACCESS_DENIED;
            case 429 -> exhaustedQuota(body) ? Reason.QUOTA_EXHAUSTED : Reason.RATE_LIMITED;
            default -> Reason.UNAVAILABLE;
        };
        return new LiveProviderException(status, reason);
    }
    private static boolean exhaustedQuota(String body) {
        try {
            var error = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            for (String field : new String[] { "code", "type" }) {
                var value = error.get(field);
                if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && (value.getAsString().equals("credit_balance_exhausted")
                            || value.getAsString().equals("insufficient_quota"))) return true;
            }
        } catch (RuntimeException invalid) { /* Unknown response retains the HTTP category. */ }
        return false;
    }
}
