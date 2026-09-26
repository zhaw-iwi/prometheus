package ch.zhaw.prometheus.controllers;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ch.zhaw.prometheus.application.*;
import ch.zhaw.prometheus.spi.live.LiveProviderException;

@WebMvcTest(ScopedLiveSessionController.class)
class ScopedLiveSessionControllerWebMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean ScopedLiveSessionService service;
    @MockitoBean ScopedDemoService demo;
    final UUID agent = UUID.randomUUID(), handle = UUID.randomUUID();
    String path() { return "/demo/agents/" + agent + "/live/sessions"; }
    @Test void typedSessionHidesProviderIdentityAndRejectsArbitraryConfiguration() throws Exception {
        when(service.create(eq("ABCDE"), eq(agent), any())).thenReturn(Optional.of(
                new ScopedLiveSessionService.SessionView(handle, "v=0 answer", "gpt-live-1", "marin", true)));
        mvc.perform(post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sdp\":\"v=0 offer\",\"voice\":\"marin\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.handle").value(handle.toString()))
                .andExpect(jsonPath("$.sidebandReady").value(true)).andExpect(jsonPath("$.clientSecret").doesNotExist());
        clearInvocations(service);
        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sdp\":\"v=0 offer\",\"instructions\":\"caller override\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void scopeAndProviderFailuresKeepStatusContractAndRedactErrors() throws Exception {
        when(service.status("ABCDE", agent, handle)).thenReturn(Optional.empty());
        mvc.perform(get(path() + "/" + handle).header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE"))
                .andExpect(status().isNotFound());
        when(service.create(eq("ABCDE"), eq(agent), any())).thenThrow(new DemoAccessDeniedException());
        mvc.perform(request()).andExpect(status().isUnauthorized());
        when(service.create(eq("ABCDE"), eq(agent), any())).thenThrow(new LiveSessionUnavailableException(true));
        mvc.perform(request()).andExpect(status().isConflict());
        when(service.create(eq("ABCDE"), eq(agent), any())).thenThrow(new LiveProviderException("private-sentinel"));
        mvc.perform(request()).andExpect(status().isBadGateway()).andExpect(content().string(""));
    }
    @Test void projectedHistoryKeepsPlannedTextSeparateFromConversation() throws Exception {
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(handle, UUID.randomUUID());
        var event = ch.zhaw.prometheus.model.event.Event.response("resp.behaviour_plan", "assistant",
                "{\"speech\":\"Planned only\",\"display\":{\"mode\":\"ready\"}}");
        event.speechProvenance(ch.zhaw.prometheus.model.event.SpeechProvenance.intent(owner));
        when(demo.getAgentEventHistory("ABCDE", agent)).thenReturn(Optional.of(java.util.List.of(event)));
        mvc.perform(get("/demo/agents/" + agent + "/live/history").header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].plannedSpeech").value("Planned only"))
                .andExpect(jsonPath("$[0].payload").value("{\"display\":{\"mode\":\"ready\"}}"))
                .andExpect(jsonPath("$[0].provenance.origin").value("BACKEND_INTENT"));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
        return post(path()).header(ScopedDemoController.ACCESS_CODE_HEADER, "ABCDE")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sdp\":\"v=0 offer\"}");
    }
}
