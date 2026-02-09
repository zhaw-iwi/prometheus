package ch.zhaw.prometheus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import ch.zhaw.prometheus.spi.OpenAIProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAIProperties.class)
@EnableScheduling
public class PrometheusApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrometheusApplication.class, args);
	}
}
