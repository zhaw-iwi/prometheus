package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.zhaw.prometheus.agentdefs.core.TalkToMe;
import ch.zhaw.prometheus.agentdefs.core.TalkToMePolicy;
import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeAllowedAgentTypeRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.spi.SpeechAudio;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TalkToMeScopedIntegrationTest {
    private static final String ACCESS_CODE = "TTM31";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessCodeAdminService adminService;

    @Autowired
    private AccessCodeRepository accessCodes;

    @Autowired
    private AccessCodeAllowedAgentTypeRepository allowedAgentTypes;

    @Autowired
    private AccessCodeAgentRepository accessCodeAgents;

    @Autowired
    private AgentRepository agents;

    @MockitoBean
    private LanguageModelGateway languageModelGateway;

    @MockitoBean
    private SpeechSynthesisGateway speechSynthesisGateway;

    @BeforeEach
    void clearScopedTestData() {
        this.accessCodeAgents.deleteAll();
        this.allowedAgentTypes.deleteAll();
        this.accessCodes.deleteAll();
    }

    @Test
    void scopedSpeechPersistsExactPlanAndSynthesizesItsUnchangedText() throws Exception {
        this.allowTalkToMe(ACCESS_CODE);
        String text = "Gr\u00fcezi, \"Z\u00fcrich\"!\nPlease read line two \ud83c\udf0d";
        UUID agentId = this.createTalkToMeAgent(ACCESS_CODE);
        when(this.speechSynthesisGateway.synthesize(anyString(), anyString(), anyDouble()))
                .thenReturn(new SpeechAudio(new byte[] { 4, 5, 6 }, "audio/mpeg"));

        this.mockMvc.perform(post("/demo/talktome/agents/" + agentId + "/speech")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, ACCESS_CODE)
                .queryParam("voice", "marin")
                .queryParam("speed", "1.25")
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.objectMapper.writeValueAsString(Map.of(
                        "type", Event.TYPE_USER_UTTERANCE,
                        "actor", Event.ACTOR_USER,
                        "kind", Event.KIND_OBSERVATION,
                        "payload", text))))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("audio/mpeg", result.getResponse().getContentType()))
                .andExpect(result -> assertTrue(java.util.Arrays.equals(new byte[] { 4, 5, 6 },
                        result.getResponse().getContentAsByteArray())));

        Agent persisted = this.agents.findById(agentId).orElseThrow();
        List<Event> history = persisted.getEventHistory().toList();
        assertEquals(2, history.size());
        assertEquals(text, history.get(0).getPayload());
        assertEquals(text, BehaviourPlan.fromJson(history.get(1).getPayload()).getSpeech());
        verify(this.speechSynthesisGateway).synthesize(text, "marin", 1.25);
        verifyNoInteractions(this.languageModelGateway);
    }

    @Test
    void scopedLifecycleRequiresTypeAssignmentAndExposesTheTalkToMeProfileTag() throws Exception {
        AccessCodeView code = this.adminService.createAccessCode(ACCESS_CODE, true);

        this.mockMvc.perform(post("/demo/agents")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, ACCESS_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.objectMapper.writeValueAsString(Map.of("agentDefinitionKey", TalkToMe.KEY))))
                .andExpect(status().isForbidden());

        this.adminService.replaceAllowedAgentTypes(code.getId(), List.of(TalkToMe.KEY)).orElseThrow();

        this.mockMvc.perform(post("/demo/agents")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, ACCESS_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.objectMapper.writeValueAsString(Map.of("agentDefinitionKey", TalkToMe.KEY))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interactionProfile.profileTags[0]").value(TalkToMe.PROFILE_TAG))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[0]")
                        .value(Event.TYPE_USER_UTTERANCE))
                .andExpect(jsonPath("$.interactionProfile.supportedBehaviourModalities[0]").value("speech"));
    }

    @Test
    void overLimitSubmissionIsPersistedForInspectionButDoesNotEmitSpeech() throws Exception {
        this.allowTalkToMe(ACCESS_CODE);
        UUID agentId = this.createTalkToMeAgent(ACCESS_CODE);
        String text = "x".repeat(TalkToMePolicy.MAX_TEXT_CODE_POINTS + 1);

        this.mockMvc.perform(post("/demo/talktome/agents/" + agentId + "/speech")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, ACCESS_CODE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.objectMapper.writeValueAsString(Map.of(
                        "type", Event.TYPE_USER_UTTERANCE,
                        "actor", Event.ACTOR_USER,
                        "kind", Event.KIND_OBSERVATION,
                        "payload", text))))
                .andExpect(status().isConflict());

        List<Event> history = this.agents.findById(agentId).orElseThrow().getEventHistory().toList();
        assertEquals(1, history.size());
        assertEquals(text, history.get(0).getPayload());
        verifyNoInteractions(this.languageModelGateway);
        verifyNoInteractions(this.speechSynthesisGateway);
    }

    private AccessCodeView allowTalkToMe(String code) {
        AccessCodeView created = this.adminService.createAccessCode(code, true);
        return this.adminService.replaceAllowedAgentTypes(created.getId(), List.of(TalkToMe.KEY)).orElseThrow();
    }

    private UUID createTalkToMeAgent(String code) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/demo/agents")
                .header(ScopedDemoController.ACCESS_CODE_HEADER, code)
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.objectMapper.writeValueAsString(Map.of("agentDefinitionKey", TalkToMe.KEY))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(this.objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
    }
}
