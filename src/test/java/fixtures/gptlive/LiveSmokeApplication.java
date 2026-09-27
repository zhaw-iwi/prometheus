package fixtures.gptlive;

public final class LiveSmokeApplication {
    public static void main(String[] args) {
        new org.springframework.boot.SpringApplication(ch.zhaw.prometheus.PrometheusApplication.class, LiveSmokeConfiguration.class).run(args);
    }
}
