package ch.zhaw.prometheus.application;

public class DuplicateAccessCodeException extends RuntimeException {
    public DuplicateAccessCodeException(String code) {
        super("access code already exists: " + code);
    }
}
