package ch.zhaw.prometheus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import ch.zhaw.prometheus.spi.OpenAIProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAIProperties.class)
public class StatefulconversationApplication {

	public static void main(String[] args) {
		SpringApplication.run(StatefulconversationApplication.class, args);
	}
}
