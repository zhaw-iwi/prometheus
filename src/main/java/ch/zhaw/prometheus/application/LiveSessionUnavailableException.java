package ch.zhaw.prometheus.application;

public class LiveSessionUnavailableException extends RuntimeException {
    private final boolean conflict;
    public LiveSessionUnavailableException(boolean conflict) {
        super(conflict ? "Live session already active or capacity reached" : "Live feature unavailable");
        this.conflict = conflict;
    }
    public boolean isConflict() { return conflict; }
}
