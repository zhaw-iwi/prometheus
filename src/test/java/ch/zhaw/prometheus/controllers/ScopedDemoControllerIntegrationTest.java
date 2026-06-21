package ch.zhaw.prometheus.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.zhaw.prometheus.application.AccessCodeAdminService;
import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.access.AccessCode;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeAllowedAgentTypeRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScopedDemoControllerIntegrationTest {
    private static final String HEADER = ScopedDemoController.ACCESS_CODE_HEADER;
    private static final String TYPE_KEY = "basic.single_state_micro_coaching";
    private static final String TDSR_TOUR_TYPE_KEY = "tdsr.core.de.tour_conversation";
    private static final String TDSR_TOUR_SOCIAL_TYPE_KEY = "tdsr.core.de.tour_conversation_social_context";

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

    @BeforeEach
    void setUp() {
        this.accessCodeAgents.deleteAll();
        this.allowedAgentTypes.deleteAll();
        this.accessCodes.deleteAll();
        when(this.languageModelGateway.complete(any())).thenReturn("Scoped response.");
    }

    @Test
    void codeAAndCodeBCreateSameTypeButOnlySeeOwnInstances() throws Exception {
        this.allowType("A49a1", TYPE_KEY);
        this.allowType("B49b2", TYPE_KEY);

        UUID agentA = this.createAgent("A49a1", TYPE_KEY);
        UUID agentB = this.createAgent("B49b2", TYPE_KEY);

        assertNotEquals(agentA, agentB);

        this.mockMvc.perform(get("/demo/agent-types")
                .header(HEADER, "A49a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value(TYPE_KEY));

        this.mockMvc.perform(get("/demo/agents")
                .header(HEADER, "A49a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(agentA.toString()))
                .andExpect(jsonPath("$[0].languageCode").value("de"));

        this.mockMvc.perform(get("/demo/agents")
                .header(HEADER, "B49b2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(agentB.toString()));

        this.mockMvc.perform(get("/demo/agents/" + agentB + "/info")
                .header(HEADER, "A49a1"))
                .andExpect(status().isNotFound());

        this.mockMvc.perform(post("/demo/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "accessCode": "A49a1"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCode").value("A49a1"))
                .andExpect(jsonPath("$.agentTypes", hasSize(1)))
                .andExpect(jsonPath("$.agents", hasSize(1)))
                .andExpect(jsonPath("$.agents[0].languageCode").value("de"));
    }

    @Test
    void disabledAccessCodeRejectsSessionListCreateAndRuntimeCalls() throws Exception {
        AccessCodeView code = this.allowType("D49d3", TYPE_KEY);
        UUID agentId = this.createAgent("D49d3", TYPE_KEY);

        this.adminService.updateAccessCodeEnabled(code.getId(), false).orElseThrow();

        this.mockMvc.perform(post("/demo/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "accessCode": "D49d3"
                        }
                        """))
                .andExpect(status().isUnauthorized());

        this.mockMvc.perform(get("/demo/agents")
                .header(HEADER, "D49d3"))
                .andExpect(status().isUnauthorized());

        this.mockMvc.perform(post("/demo/agents")
                .header(HEADER, "D49d3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentDefinitionKey": "basic.single_state_micro_coaching"
                        }
                        """))
                .andExpect(status().isUnauthorized());

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/info")
                .header(HEADER, "D49d3"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disallowedTypeCreationReturnsForbidden() throws Exception {
        this.allowType("F49f4", TYPE_KEY);

        this.mockMvc.perform(post("/demo/agents")
                .header(HEADER, "F49f4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentDefinitionKey": "tdsr.core.de.rock_scissor_paper"
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void tdsrTourConversationCanBeAllowedCreatedAndConnectedThroughScopedDemoApi() throws Exception {
        this.allowType("G49g9", TDSR_TOUR_TYPE_KEY);

        this.mockMvc.perform(get("/demo/agent-types")
                .header(HEADER, "G49g9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value(TDSR_TOUR_TYPE_KEY))
                .andExpect(jsonPath("$[0].displayName").value("GIGI TDSR - Tour Conversation"));

        UUID agentId = this.createAgent("G49g9", TDSR_TOUR_TYPE_KEY);

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/info")
                .header(HEADER, "G49g9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("GIGI TDSR - Tour Conversation"))
                .andExpect(jsonPath("$.languageCode").value("de"))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[0]").value("obs.user_utterance"))
                .andExpect(jsonPath("$.interactionProfile.supportedBehaviourModalities[0]").value("speech"))
                .andExpect(jsonPath("$.interactionProfile.supportedBehaviourModalities[1]")
                        .value("nonVerbal.gesture"))
                .andExpect(jsonPath("$.interactionProfile.profileTags[0]").value("demo.gigi.tdsr"))
                .andExpect(jsonPath("$.interactionProfile.profileTags[1]").value("demo.gigi.tour_conversation"));
    }

    @Test
    void tdsrTourConversationSocialContextCanBeAllowedCreatedAndConnectedThroughScopedDemoApi() throws Exception {
        this.allowType("H49h1", TDSR_TOUR_SOCIAL_TYPE_KEY);

        this.mockMvc.perform(get("/demo/agent-types")
                .header(HEADER, "H49h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value(TDSR_TOUR_SOCIAL_TYPE_KEY))
                .andExpect(jsonPath("$[0].displayName").value("GIGI TDSR - Tour Conversation Social Context"));

        UUID agentId = this.createAgent("H49h1", TDSR_TOUR_SOCIAL_TYPE_KEY);

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/info")
                .header(HEADER, "H49h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("GIGI TDSR - Tour Conversation Social Context"))
                .andExpect(jsonPath("$.languageCode").value("de"))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[0]").value("obs.user_utterance"))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[1]").value("obs.human.presence"))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[2]").value("obs.social.grouping"))
                .andExpect(jsonPath("$.interactionProfile.supportedObservations[3]")
                        .value("obs.social.situation_change"))
                .andExpect(jsonPath("$.interactionProfile.supportedBehaviourModalities[0]").value("speech"))
                .andExpect(jsonPath("$.interactionProfile.supportedBehaviourModalities[1]")
                        .value("nonVerbal.gesture"))
                .andExpect(jsonPath("$.interactionProfile.profileTags[0]").value("demo.gigi.tdsr"))
                .andExpect(jsonPath("$.interactionProfile.profileTags[1]").value("demo.gigi.tour_conversation"))
                .andExpect(jsonPath("$.interactionProfile.profileTags[2]").value("demo.gigi.social_context"));
    }

    @Test
    void deleteRemovesVisibilityAndAgentWhenNoOtherAccessCodeLinksRemain() throws Exception {
        this.allowType("C49c5", TYPE_KEY);
        UUID agentId = this.createAgent("C49c5", TYPE_KEY);

        this.mockMvc.perform(delete("/demo/agents/" + agentId)
                .header(HEADER, "C49c5"))
                .andExpect(status().isNoContent());

        this.mockMvc.perform(get("/demo/agents")
                .header(HEADER, "C49c5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        assertFalse(this.agents.existsById(agentId));
        assertFalse(this.accessCodeAgents.findByAccessCode_IdAndAgent_Id(
                this.accessCodes.findByCode("C49c5").orElseThrow().getId(), agentId).isPresent());
    }

    @Test
    void deleteOnlyRemovesLinkWhenAnotherAccessCodeStillReferencesAgent() throws Exception {
        AccessCodeView codeA = this.allowType("S49s6", TYPE_KEY);
        AccessCodeView codeB = this.allowType("T49t7", TYPE_KEY);
        UUID agentId = this.createAgent("S49s6", TYPE_KEY);
        AccessCode accessCodeB = this.accessCodes.findById(codeB.getId()).orElseThrow();
        Agent agent = this.agents.findById(agentId).orElseThrow();
        this.accessCodeAgents.save(new AccessCodeAgent(accessCodeB, agent));

        this.mockMvc.perform(delete("/demo/agents/" + agentId)
                .header(HEADER, "S49s6"))
                .andExpect(status().isNoContent());

        assertFalse(this.accessCodeAgents.findByAccessCode_IdAndAgent_Id(codeA.getId(), agentId).isPresent());
        assertTrue(this.accessCodeAgents.findByAccessCode_IdAndAgent_Id(codeB.getId(), agentId).isPresent());
        assertTrue(this.agents.existsById(agentId));

        this.mockMvc.perform(get("/demo/agents")
                .header(HEADER, "T49t7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(agentId.toString()));
    }

    @Test
    void streamEndpointsAcceptAccessCodeQueryParameter() throws Exception {
        this.allowType("Q49q8", TYPE_KEY);
        UUID agentId = this.createAgent("Q49q8", TYPE_KEY);

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/behaviour/stream")
                .param("accessCode", "Q49q8")
                .param("lastEventId", "cursor-1"))
                .andExpect(status().isOk());

        this.mockMvc.perform(get("/demo/agents/" + agentId + "/monitor/stream")
                .param("accessCode", "Q49q8"))
                .andExpect(status().isOk());
    }

    private AccessCodeView allowType(String code, String typeKey) {
        AccessCodeView created = this.adminService.createAccessCode(code, true);
        return this.adminService.replaceAllowedAgentTypes(created.getId(), List.of(typeKey)).orElseThrow();
    }

    private UUID createAgent(String code, String typeKey) throws Exception {
        MvcResult result = this.mockMvc.perform(post("/demo/agents")
                .header(HEADER, code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "agentDefinitionKey": "%s"
                        }
                        """.formatted(typeKey)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode json = this.objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }
}
