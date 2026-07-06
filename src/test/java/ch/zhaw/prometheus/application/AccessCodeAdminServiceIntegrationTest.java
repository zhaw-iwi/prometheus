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
import ch.zhaw.prometheus.controllers.views.AccessCodePresetEntryView;
import ch.zhaw.prometheus.controllers.views.AccessCodePresetView;
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

        assertTrue(keys.contains("basic.single_state_micro_coaching"));
        assertTrue(keys.contains("tdsr.core.de.rock_scissor_paper"));
        assertTrue(keys.contains("tdsr.core.de.tour_conversation"));
        assertTrue(keys.contains("tdsr.core.de.tour_conversation_social_context"));
        assertTrue(keys.contains("tdsr.core.fr.tour_conversation"));
        assertTrue(keys.contains("tdsr.core.it.rock_scissor_paper"));
        assertTrue(keys.contains("tdsr.core.en.tour_conversation_social_context"));
        assertTrue(keys.contains("tdsr.shhd.de.epfl_active"));
        assertTrue(keys.contains("tdsr.shhd.en.epfl_active"));
        assertTrue(keys.contains("tdsr.shhd.it.supsi_active"));
        assertTrue(keys.contains("tdsr.shhd.fr.interviewing_people"));
        assertTrue(keys.contains("tdsr.shhd.babylon.unis_student"));
        assertTrue(keys.contains("tdsr.davos.therapy_appointment_reminder_intro"));
        assertTrue(keys.contains("tdsr.davos.smart_goal_coaching"));
        assertTrue(keys.contains("tdsr.davos.summit_hotel_conversation"));
        assertTrue(keys.contains("tdsr.lab.social_context_sensitivity"));
        assertTrue(keys.contains("tdsr.lab.facial_expression_sensitivity"));
        assertTrue(keys.contains("tdsr.lab.rock_scissor_paper"));
        assertTrue(keys.contains("tdsr.lab.role_clarification_guessing_game"));
        assertTrue(keys.contains("tdsr.lab.multimodal_behaviour"));
        assertTrue(keys.contains("tdsr.migros.appenzell_general"));
        assertTrue(keys.contains("tdsr.migros.appenzell_scene_2_menu_planner"));
        assertTrue(keys.contains("tdsr.migros.appenzell_scene_3_checkout_reflection"));
        assertEquals(keys.size(), new java.util.HashSet<>(keys).size());
        assertEquals(List.of("basic"), packagePath(agentTypes, "basic.single_state_micro_coaching"));
        assertEquals(List.of("elderlycare"), packagePath(agentTypes, "elderlycare.smart_goal_coaching"));
        assertEquals(List.of("tdsr", "core", "de"),
                packagePath(agentTypes, "tdsr.core.de.rock_scissor_paper"));
        assertEquals(List.of("tdsr", "core", "babylon"),
                packagePath(agentTypes, "tdsr.core.babylon.tour_conversation"));
        assertEquals(List.of("tdsr", "shhd", "de"),
                packagePath(agentTypes, "tdsr.shhd.de.epfl_active"));
        assertEquals(List.of("tdsr", "shhd", "babylon"),
                packagePath(agentTypes, "tdsr.shhd.babylon.unis_student"));
        assertEquals(List.of("tdsr", "davos"),
                packagePath(agentTypes, "tdsr.davos.therapy_appointment_reminder_intro"));
        assertEquals(List.of("tdsr", "davos"),
                packagePath(agentTypes, "tdsr.davos.smart_goal_coaching"));
        assertEquals(List.of("tdsr", "davos"),
                packagePath(agentTypes, "tdsr.davos.summit_hotel_conversation"));
        assertEquals(List.of("tdsr", "lab"),
                packagePath(agentTypes, "tdsr.lab.social_context_sensitivity"));
        assertEquals(List.of("tdsr", "lab"),
                packagePath(agentTypes, "tdsr.lab.facial_expression_sensitivity"));
        assertEquals(List.of("tdsr", "lab"),
                packagePath(agentTypes, "tdsr.lab.rock_scissor_paper"));
        assertEquals(List.of("tdsr", "lab"),
                packagePath(agentTypes, "tdsr.lab.role_clarification_guessing_game"));
        assertEquals(List.of("tdsr", "lab"),
                packagePath(agentTypes, "tdsr.lab.multimodal_behaviour"));
        assertEquals(List.of("tdsr", "migros"),
                packagePath(agentTypes, "tdsr.migros.appenzell_general"));
        assertEquals(List.of("tdsr", "migros"),
                packagePath(agentTypes, "tdsr.migros.appenzell_scene_2_menu_planner"));
        assertEquals(List.of("tdsr", "migros"),
                packagePath(agentTypes, "tdsr.migros.appenzell_scene_3_checkout_reflection"));
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
    void listsShhdAccessCodePresetWithExplicitAssignments() {
        AccessCodePresetView preset = this.service.listAccessCodePresets().stream()
                .filter(candidate -> "shhd_scene_agents".equals(candidate.getKey()))
                .findFirst()
                .orElseThrow();

        assertEquals("SHHD scene access codes", preset.getDisplayName());
        assertEquals(List.of("shhde", "shhen", "shhfr", "shhit", "shhba"), preset.getEntries().stream()
                .map(AccessCodePresetEntryView::getCode)
                .toList());
        assertEquals(List.of(
                "tdsr.shhd.de.epfl_active",
                "tdsr.shhd.de.furka",
                "tdsr.shhd.de.interviewing_people",
                "tdsr.shhd.de.supsi_active",
                "tdsr.shhd.de.unis_student"), presetEntry(preset, "shhde").getAgentTypeKeys());
        assertEquals(List.of(
                "tdsr.shhd.babylon.epfl_active",
                "tdsr.shhd.babylon.furka",
                "tdsr.shhd.babylon.interviewing_people",
                "tdsr.shhd.babylon.supsi_active",
                "tdsr.shhd.babylon.unis_student"), presetEntry(preset, "shhba").getAgentTypeKeys());
    }

    @Test
    void appliesAccessCodePresetWithReviewedAssignments() {
        List<AccessCodeView> created = this.service.applyAccessCodePreset("shhd_scene_agents", List.of(
                new AccessCodePresetEntrySpec("shhde", List.of(
                        "tdsr.shhd.de.epfl_active",
                        "tdsr.shhd.de.furka",
                        "tdsr.shhd.de.interviewing_people",
                        "tdsr.shhd.de.supsi_active")),
                new AccessCodePresetEntrySpec("shhen", List.of(
                        "tdsr.shhd.en.epfl_active",
                        "tdsr.shhd.en.furka",
                        "tdsr.shhd.en.interviewing_people",
                        "tdsr.shhd.en.supsi_active",
                        "tdsr.shhd.en.unis_student")),
                new AccessCodePresetEntrySpec("shhfr", List.of(
                        "tdsr.shhd.fr.epfl_active",
                        "tdsr.shhd.fr.furka",
                        "tdsr.shhd.fr.interviewing_people",
                        "tdsr.shhd.fr.supsi_active",
                        "tdsr.shhd.fr.unis_student")),
                new AccessCodePresetEntrySpec("shhit", List.of(
                        "tdsr.shhd.it.epfl_active",
                        "tdsr.shhd.it.furka",
                        "tdsr.shhd.it.interviewing_people",
                        "tdsr.shhd.it.supsi_active",
                        "tdsr.shhd.it.unis_student")),
                new AccessCodePresetEntrySpec("shhba", List.of(
                        "tdsr.shhd.babylon.epfl_active",
                        "tdsr.shhd.babylon.furka",
                        "tdsr.shhd.babylon.interviewing_people",
                        "tdsr.shhd.babylon.supsi_active",
                        "tdsr.shhd.babylon.unis_student")))).orElseThrow();

        assertEquals(5, created.size());
        assertEquals(5, this.accessCodes.findAll().size());
        assertTrue(accessCode(created, "shhde").isEnabled());
        assertEquals(List.of(
                "tdsr.shhd.de.epfl_active",
                "tdsr.shhd.de.furka",
                "tdsr.shhd.de.interviewing_people",
                "tdsr.shhd.de.supsi_active"), accessCode(created, "shhde").getAllowedAgentTypeKeys());
        assertEquals(24, this.allowedAgentTypes.findAll().size());
    }

    @Test
    void rejectsConflictingOrEditedAccessCodePresetRequests() {
        this.service.createAccessCode("shhde", true);

        assertThrows(DuplicateAccessCodeException.class, () -> this.service.applyAccessCodePreset("shhd_scene_agents",
                this.service.listAccessCodePresets().get(0).getEntries().stream()
                        .map(entry -> new AccessCodePresetEntrySpec(entry.getCode(), entry.getAgentTypeKeys()))
                        .toList()));
        assertThrows(IllegalArgumentException.class, () -> this.service.applyAccessCodePreset("shhd_scene_agents",
                List.of(new AccessCodePresetEntrySpec("shhde", List.of("basic.single_state_micro_coaching")))));
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
                "tdsr.core.de.rock_scissor_paper",
                "tdsr.core.de.guessing_game_with_gestures")).orElseThrow();

        assertEquals(List.of("tdsr.core.de.guessing_game_with_gestures", "tdsr.core.de.rock_scissor_paper"),
                assigned.getAllowedAgentTypeKeys());
        assertEquals(2, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView added = this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                "tdsr.core.de.guessing_game_with_gestures",
                "tdsr.core.de.rock_scissor_paper",
                "tdsr.core.de.tour_conversation")).orElseThrow();

        assertEquals(List.of(
                "tdsr.core.de.guessing_game_with_gestures",
                "tdsr.core.de.rock_scissor_paper",
                "tdsr.core.de.tour_conversation"), added.getAllowedAgentTypeKeys());
        assertEquals(3, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView removed = this.service.replaceAllowedAgentTypes(created.getId(), List.of(
                "tdsr.core.de.guessing_game_with_gestures",
                "tdsr.core.de.tour_conversation")).orElseThrow();

        assertEquals(List.of("tdsr.core.de.guessing_game_with_gestures", "tdsr.core.de.tour_conversation"),
                removed.getAllowedAgentTypeKeys());
        assertEquals(2, this.allowedAgentTypes.findByAccessCodeId(created.getId()).size());

        AccessCodeView replaced = this.service.replaceAllowedAgentTypes(created.getId(),
                List.of("basic.single_state_micro_coaching")).orElseThrow();

        assertEquals(List.of("basic.single_state_micro_coaching"), replaced.getAllowedAgentTypeKeys());
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
                        "basic.single_state_micro_coaching",
                        "basic.single_state_micro_coaching")));
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

    private static AccessCodePresetEntryView presetEntry(AccessCodePresetView preset, String code) {
        return preset.getEntries().stream()
                .filter(entry -> code.equals(entry.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private static AccessCodeView accessCode(List<AccessCodeView> accessCodes, String code) {
        return accessCodes.stream()
                .filter(accessCode -> code.equals(accessCode.getCode()))
                .findFirst()
                .orElseThrow();
    }
}
