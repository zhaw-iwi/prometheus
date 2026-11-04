package ch.zhaw.prometheus.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RuntimeClockControllerWebMvcTest {
    @Test void publicSampleIsExactUncachedAndRequiresNoApplicationServices() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
        MockMvcBuilders.standaloneSetup(new RuntimeClockController(clock)).build()
                .perform(get("/runtime/clock"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(content().json("{\"serverTimeMs\":" + clock.millis() + "}", true));
    }
}
