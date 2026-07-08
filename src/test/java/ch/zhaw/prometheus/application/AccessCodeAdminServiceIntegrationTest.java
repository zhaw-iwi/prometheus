package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import ch.zhaw.prometheus.controllers.views.AccessCodeView;
import ch.zhaw.prometheus.controllers.views.AdminAgentTypeView;
import ch.zhaw.prometheus.controllers.views.AgentInfoView;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.State;
import ch.zhaw.prometheus.model.access.AccessCode;
import ch.zhaw.prometheus.model.access.AccessCodeAgent;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.repositories.AccessCodeAgentRepository;
import ch.zhaw.prometheus.repositories.AccessCodeAllowedAgentTypeRepository;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import ch.zhaw.prometheus.repositories.AgentRepository;

@SpringBootTest
@Transactional
class AccessCodeAdminServiceIntegrationTest {
    private static final String CORE_SOCIAL_CONTEXT = "core.social_context_sensitivity";
    private static final String CORE_MULTIMODAL_BEHAVIOUR = "core.multimodal_behaviour";
    private static final String HEALTHCARE_THERAPY_REMINDER = "usecases.healthcare.therapy_appointment_reminder";

    @Autowired
    private AccessCodeAdminService service;

    @Autowired
    private AccessCodeRepository accessCodes;

    @Autowired
    private AccessCodeAllowedAgentTypeRepository allowedAgentTypes;

    @Autowired
    private AccessCodeAgentRepository accessCodeAgents;

    @Autowired
    private AgentRepository agents;

    @BeforeEach
    void clearAccessCodeData() {
        this.accessCodeAgents.deleteAll();
        this.allowedAgentTypes.deleteAll();
        this.accessCodes.deleteAll();
    }

    @Test
    void listsRegisteredProductionAgentTypes() {
        List<AdminAgentTypeView> agentTypes = this.service.listAgentTypes();
        List<String> keys = agentTypes.stream()
                .map(AdminAgentTypeView::getKey)
                .toList();

        assertTrue(keys.contains(CORE_SOCIAL_CONTEXT));
        assertTrue(keys.contains(CORE_MULTIMODAL_BEHAVIOUR));
        assertTrue(keys.contains("core.rock_scissor_paper"));
        assertTrue(keys.contains(HEALTHCARE_THERAPY_REMINDER));
        assertTrue(keys.contains("usecases.healthcare.healthcare_conversation"));
        assertEquals(keys.size(), new java.util.HashSet<>(keys).size());
        assertEquals(List.of("core"), packagePath(agentTypes, CORE_SOCIAL_CONTEXT));
        assertEquals(List.of("usecases", "healthcare"), packagePath(agentTypes, HEALTHCARE_THERAPY_REMINDER));
    }

    @Test
    void createsCaseSensitiveFiveCharacterAccessCodes() {
        AccessCodeView first = this.service.createAccessCode("af7u1", null);
        AccessCodeView second = this.service.createAccessCode("Af7u1", false);

        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals("af7u1", first.getCode());
        assertEquals("Af7u1", second.getCode());
        assertTrue(first.isEnabled());
        assertFalse(second.isEnabled());
        assertEquals(2, this.accessCodes.findAll().size());
    }

    @Test
    void frameworkLineHasNoBuiltInAccessCodePresets() {
        assertTrue(this.service.listAccessCodePresets().isEmpty());
        assertTrue(this.service.applyAccessCodePreset("missing", List.of()).isEmpty());
    }

    @Test
    void rejectsDuplicateAndInvalidCodes() {
        this.service.createAccessCode("duP77", true);

        assertThrows(DuplicateAccessCodeException.class, () -> this.service.createAccessCode("duP77", true));
        assertThrows(IllegalArgumentException.class, () -> this.service.createAccessCode("abcd", true));
        assertThrows(IllegalArgumentException.class, () -> this.service.createAccessCode("abcdef", true));
        assertThrows(IllegalArgumentException.class, () -> this.service.createAccessCode("ab-12", true));
        assertThrows(IllegalArgumentException.class, () -> this.service.createAccessCode(" ab12", true));
    }

    @Test
    void enablesAndDisablesAccessCodes() {
        AccessCodeView created = this.service.createAccessCode("enA12", true);

        AccessCodeView disabled = this.service.updateAccessCodeEnabled(created.getId(), false).orElseThrow();
        AccessCodeView enabled = this.service.updateAccessCodeEnabled(created.getId(), true).orElseThrow();

        assertFalse(disabled.isEnabled());
        assertTrue(enabled.isEnabled());
        assertTrue(this.service.updateAccessCodeEnabled(java.util.UUID.randomUUID(), false).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> this.service.updateAccessCodeEnabled(created.getId(), null));
    }

    @Test
    void changesAllowedAgentTypesByAddingRemovingReplacingAndClearingExistingAssignments() {
        AccessCodeView created = this.service.createAccessCode("typ48", true);

        AccessCodeView assigned = this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                CORE_SOCIAL_CONTEXT,
                HEALTHCARE_THERAPY_REMINDER)).orElseThrow();

        assertEquals(List.of(CORE_SOCIAL_CONTEXT, HEALTHCARE_THERAPY_REMINDER),
                assigned.getAllowedAgentTypeKeys());
        assertEquals(2, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView added = this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                CORE_SOCIAL_CONTEXT,
                HEALTHCARE_THERAPY_REMINDER,
                CORE_MULTIMODAL_BEHAVIOUR)).orElseThrow();

        assertEquals(List.of(
                CORE_MULTIMODAL_BEHAVIOUR,
                CORE_SOCIAL_CONTEXT,
                HEALTHCARE_THERAPY_REMINDER), added.getAllowedAgentTypeKeys());
        assertEquals(3, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView removed = this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                CORE_SOCIAL_CONTEXT,
                HEALTHCARE_THERAPY_REMINDER)).orElseThrow();

        assertEquals(List.of(CORE_SOCIAL_CONTEXT, HEALTHCARE_THERAPY_REMINDER),
                removed.getAllowedAgentTypeKeys());
        assertEquals(2, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView replaced = this.service.replaceAllowedAgentTypes(created.getId(),
                List.of(HEALTHCARE_THERAPY_REMINDER)).orElseThrow();

        assertEquals(List.of(HEALTHCARE_THERAPY_REMINDER), replaced.getAllowedAgentTypeKeys());
        assertEquals(1, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView cleared = this.service.replaceAllowedAgentTypes(created.getId(), List.of()).orElseThrow();

        assertTrue(cleared.getAllowedAgentTypeKeys().isEmpty());
        assertTrue(this.allowedAgentTypes.findByAccessCodeId(created.getId()).isEmpty());
    }

    @Test
    void rejectsUnknownOrDuplicateAgentTypeAssignments() {
        AccessCodeView created = this.service.createAccessCode("dup48", true);

        assertThrows(IllegalArgumentException.class,
                () -> this.service.replaceAllowedAgentTypes(created.getId(), List.of("missing.type")));
        assertThrows(IllegalArgumentException.class,
                () -> this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                        CORE_SOCIAL_CONTEXT,
                        CORE_SOCIAL_CONTEXT)));
    }

    @Test
    void listsAgentsAssociatedWithAccessCode() {
        AccessCodeView created = this.service.createAccessCode("agt48", true);
        AccessCode accessCode = this.accessCodes.findById(created.getId()).orElseThrow();
        Agent agent = this.agents.save(new Agent("Scoped Agent", "Visible through access code",
                new State("start", new NoOpPolicy(), List.of())));
        this.accessCodeAgents.save(new AccessCodeAgent(accessCode, agent));

        List<AgentInfoView> visibleAgents = this.service.listAgents(created.getId()).orElseThrow();

        assertEquals(1, visibleAgents.size());
        assertEquals(agent.getId(), visibleAgents.get(0).getID());
        assertEquals("Scoped Agent", visibleAgents.get(0).getName());
        assertTrue(this.service.listAgents(java.util.UUID.randomUUID()).isEmpty());
    }

    private static List<String> packagePath(List<AdminAgentTypeView> agentTypes, String key) {
        return agentTypes.stream()
                .filter(agentType -> key.equals(agentType.getKey()))
                .findFirst()
                .map(AdminAgentTypeView::getPackagePath)
                .orElseThrow();
    }

}
