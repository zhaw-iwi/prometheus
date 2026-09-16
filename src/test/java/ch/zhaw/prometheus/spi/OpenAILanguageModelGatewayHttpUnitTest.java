package ch.zhaw.prometheus.spi;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;
import ch.zhaw.prometheus.model.policy.PromptMessage;

class OpenAILanguageModelGatewayHttpUnitTest {
    @Test void acceptsResponseWithoutUsageMetadata() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"true\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var properties = new OpenAIProperties();
            properties.setOpenaivsazureopenai("openai");
            properties.setModel("test-model");
            properties.setKey("test-key");
            properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
            var gateway = new OpenAILanguageModelGateway(properties);
            assertTrue(gateway.decide(List.of(PromptMessage.system("synthetic decision"))));
        } finally { server.stop(0); }
    }
}
