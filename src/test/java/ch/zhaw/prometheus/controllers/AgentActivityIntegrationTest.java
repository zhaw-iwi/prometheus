package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.zhaw.prometheus.application.*;
import fixtures.gptlive.LiveSmokeConfiguration;

@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false"})
@AutoConfigureMockMvc
@Import(LiveSmokeConfiguration.class)
class AgentActivityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AgentActivityService activity;
    @Autowired LiveSmokeConfiguration.Provider provider;
    @Test void scopedStreamReportsThinkingBeforeHttpCompletesAndNeverExportsContent() throws Exception {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0,5);
        var access = admin.createAccessCode(code,true);
        admin.replaceAllowedAgentTypes(access.getId(),List.of("core.multimodal_behaviour"));
        var created = mvc.perform(post("/demo/agents").header(ScopedDemoController.ACCESS_CODE_HEADER,code)
                .contentType(MediaType.APPLICATION_JSON).content("{\"agentDefinitionKey\":\"core.multimodal_behaviour\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("id").asText());
        String path = "/demo/agents/"+id;
        mvc.perform(get(path+"/monitor/stream").header(ScopedDemoController.ACCESS_CODE_HEADER,"ZZZZZ"))
                .andExpect(status().is4xxClientError());
        var stream = mvc.perform(get(path+"/monitor/stream").header(ScopedDemoController.ACCESS_CODE_HEADER,code))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(post(path+"/acknowledge?profile=full_plan")
                .header(ScopedDemoController.ACCESS_CODE_HEADER,code).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"obs.user_utterance\",\"actor\":\"user\",\"kind\":\"observation\",\"payload\":\"PRIVATE_USER_SENTINEL\"}"))
                .andExpect(status().isOk());
        provider.inferenceGate = new CompletableFuture<>();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = workers.submit(() -> mvc.perform(post(path+"/behaviour/generate")
                    .header(ScopedDemoController.ACCESS_CODE_HEADER,code))
                    .andExpect(status().isOk()).andReturn());
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertTrue(activity.snapshot(id).active().stream().anyMatch(a -> a.stage().equals("thinking")));
                activity.flush();
                assertTrue(stream.getResponse().getContentAsString().contains("\"stage\":\"thinking\""));
            });
            assertFalse(pending.isDone());
            provider.inferenceGate.complete(null); pending.get(15,TimeUnit.SECONDS);
            assertTrue(activity.snapshot(id).active().isEmpty());
            assertFalse(json.writeValueAsString(activity.snapshot(id)).contains("PRIVATE_USER_SENTINEL"));
            assertTrue(activity.snapshot(id).recent().stream().anyMatch(e -> e.outcome().equals("complete")));
        } finally {
            provider.inferenceGate.complete(null); provider.inferenceGate=null;
            mvc.perform(delete(path).header(ScopedDemoController.ACCESS_CODE_HEADER,code)).andExpect(status().isNoContent());
        }
    }
}
