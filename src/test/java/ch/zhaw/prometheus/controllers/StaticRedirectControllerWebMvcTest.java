package ch.zhaw.prometheus.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StaticRedirectController.class, properties = "spring.web.resources.add-mappings=false")
class StaticRedirectControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void legacyClientRoutesAreNotRedirectedAnymore() throws Exception {
        for (String route : List.of(
                "/monitor",
                "/monitor/",
                "/realtime",
                "/realtime/",
                "/visual/facial",
                "/visual/facial/",
                "/visual/multifacial",
                "/visual/multifacial/",
                "/visual/social",
                "/visual/social/",
                "/nonverbal",
                "/nonverbal/",
                "/visual/nonverbal",
                "/visual/nonverbal/")) {
            this.mockMvc.perform(get(route + "?agentId=uuid"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void redirectsRootToValerianStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/?agentId=uuid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/valerian/index.html?agentId=uuid"));
    }

    @Test
    void redirectsValerianToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/valerian?agentId=uuid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/valerian/index.html?agentId=uuid"));
    }

    @Test
    void redirectsValerianAdminToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/valerian-admin?view=root"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/valerian-admin/index.html?view=root"));
    }

    @Test
    void redirectsApiWorkbenchToStaticIndexPreservingQuery() throws Exception {
        this.mockMvc.perform(get("/apiworkbench?view=streams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apiworkbench/index.html?view=streams"));

        this.mockMvc.perform(get("/apiworkbench/?view=streams"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apiworkbench/index.html?view=streams"));
    }
}
