package ch.zhaw.prometheus.application;

public class DemoAccessDeniedException extends RuntimeException {
    public DemoAccessDeniedException() {
        super("access code is invalid or disabled");
    }
}
