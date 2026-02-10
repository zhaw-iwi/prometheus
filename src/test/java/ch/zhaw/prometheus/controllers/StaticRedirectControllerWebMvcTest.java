package ch.zhaw.prometheus.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StaticRedirectController.class)
class StaticRedirectControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void redirectsMonitorToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/monitor?agentId=abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/monitor/index.html?agentId=abc"));
    }

    @Test
    void redirectsRealtimeToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/realtime?agent=xyz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/realtime/index.html?agent=xyz"));
    }

    @Test
    void redirectsVisualFacialToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/visual/facial?agentId=uuid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/visual/facial/index.html?agentId=uuid"));
    }
}
