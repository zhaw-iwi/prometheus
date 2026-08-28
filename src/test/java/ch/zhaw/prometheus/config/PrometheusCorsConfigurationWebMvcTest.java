package ch.zhaw.prometheus.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ch.zhaw.prometheus.application.ScopedDemoService;
import ch.zhaw.prometheus.controllers.ScopedDemoController;

@WebMvcTest(controllers = ScopedDemoController.class)
@Import(PrometheusCorsConfiguration.class)
@TestPropertySource(properties = {
        "prometheus.cors.allowed-origins=https://cockpit.example.test",
        "prometheus.cors.allowed-origin-patterns=http://localhost:*,http://127.0.0.1:*"
})
class PrometheusCorsConfigurationWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScopedDemoService demoService;

    @Test
    void allowsExactExternalCockpitOriginForScopedJsonCalls() throws Exception {
        this.mockMvc.perform(options("/demo/session")
                .header(HttpHeaders.ORIGIN, "https://cockpit.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type,x-prometheus-access-code"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://cockpit.example.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("x-prometheus-access-code")));
    }

    @Test
    void allowsConfiguredLocalhostPatternForLiveTranscriptionCalls() throws Exception {
        UUID agentId = UUID.randomUUID();

        this.mockMvc.perform(options("/demo/agents/" + agentId + "/transcription/session")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5010")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "content-type,x-prometheus-access-code"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://127.0.0.1:5010"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("content-type")));
    }

    @Test
    void allowsSseCursorHeaderForBehaviourStreamPreflight() throws Exception {
        UUID agentId = UUID.randomUUID();

        this.mockMvc.perform(options("/demo/agents/" + agentId + "/behaviour/stream")
                .header(HttpHeaders.ORIGIN, "http://localhost:5010")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "last-event-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5010"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("last-event-id")));
    }

    @Test
    void rejectsOriginsOutsideConfiguredAllowlist() throws Exception {
        this.mockMvc.perform(options("/demo/session")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isForbidden());
    }
}
