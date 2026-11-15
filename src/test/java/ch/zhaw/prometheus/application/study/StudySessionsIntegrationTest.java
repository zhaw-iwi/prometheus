package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ch.zhaw.prometheus.application.AgentApplicationService;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.controllers.StudyController;
import static ch.zhaw.prometheus.application.study.StudyAttempt.*;

@SpringBootTest(properties={"prometheus.runtime.tick.enabled=false","prometheus.admin.token=study-test-admin"})
@AutoConfigureMockMvc
class StudySessionsIntegrationTest {
    @Autowired StudySessions sessions;
    @Autowired StudyAttemptRepository attempts;
    @Autowired StudyFormRepository forms;
    @Autowired StudyMaterials materials;
    @Autowired AgentRepository agents;
    @Autowired MockMvc http;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoSpyBean AgentApplicationService agentService;
    StudySessions.Invitation invite() { return sessions.invite(Mode.SYNTHETIC,"guess",null); }
    Map<String,String> pre() {
        var answers=new HashMap<String,String>();
        materials.manifest().pre().forEach(i -> answers.put(i.id(),i.kind().equals("rating7")?"4":"None"));
        return answers;
    }
    void prepare(StudySessions.Invitation invite) {
        sessions.consent(invite.token(),StudySessions.SYNTHETIC_CONSENT);
        sessions.form(invite.token(),"pre",pre());
        sessions.form(invite.token(),"intention",Map.of("intention","I want a short game"));
    }
    UUID agentId(StudySessions.Invitation invite) { return attempts.findById(invite.session().id()).orElseThrow().agentId; }
    @Test void consentAndFormsAreDurableAndPrivate() {
        var first=invite(); var second=invite(); prepare(first);
        assertEquals(Stage.COORDINATION,sessions.form(first.token(),"private",Map.of("private","secret-umbrella")).stage());
        assertEquals(Stage.INFORMATION,sessions.get(second.token()).stage());
        var reloaded=attempts.findById(first.session().id()).orElseThrow();
        assertEquals(first.session().scenario(),reloaded.scenario);
        assertNotEquals(first.token(),reloaded.tokenHash);
        assertEquals(3,forms.findByAttemptIdOrderByCreatedAt(reloaded.id).size());
        assertTrue(forms.findByAttemptIdAndKind(reloaded.id,"private").orElseThrow().answers.contains("secret-umbrella"));
        var agent=agents.findById(reloaded.agentId).orElseThrow();
        assertTrue(agent.getEventHistory().toList().stream().noneMatch(e -> e.getPayload()!=null && e.getPayload().contains("secret-umbrella")));
        assertFalse(agent.getCurrentState().getTotalPolicy().contains("secret-umbrella"));
        assertFalse(sessions.get(first.token()).toString().contains("secret-umbrella"));
        verifyNoInteractions(gateway);
    }
    @Test void simultaneousFinalFormsProvisionExactlyOneAgent() throws Exception {
        var invitation=invite(); prepare(invitation); long before=agents.count();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var barrier=new CyclicBarrier(2);
            Callable<StudySessions.View> action=() -> { barrier.await(); return sessions.form(invitation.token(),"private",Map.of("private","secret")); };
            var a=pool.submit(action); var b=pool.submit(action);
            assertEquals(a.get(30,TimeUnit.SECONDS).id(),b.get(30,TimeUnit.SECONDS).id());
        }
        assertEquals(before+1,agents.count());
        assertNotNull(agentId(invitation));
        assertEquals(3,forms.findByAttemptIdOrderByCreatedAt(invitation.session().id()).size());
        assertThrows(ResponseStatusException.class,() -> sessions.form(invitation.token(),"private",Map.of("private","different")));
    }
    @Test void provisioningFailureRollsBackAgentFormAndStage() {
        var invitation=invite(); prepare(invitation); long before=agents.count();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Injected after agent save"); })
                .when(agentService).persistCreatedAgent(any());
        assertThrows(IllegalStateException.class,() -> sessions.form(invitation.token(),"private",Map.of("private","secret")));
        assertEquals(before,agents.count()); assertNull(agentId(invitation));
        assertEquals(Stage.PRIVATE,sessions.get(invitation.token()).stage());
        assertTrue(forms.findByAttemptIdAndKind(invitation.session().id(),"private").isEmpty());
    }
    @Test void revokedWithdrawnAndReplacedAttemptsCannotAdvance() {
        var withdrawn=invite(); sessions.withdraw(withdrawn.token());
        assertThrows(ResponseStatusException.class,() -> sessions.consent(withdrawn.token(),StudySessions.SYNTHETIC_CONSENT));
        var revoked=invite(); sessions.revoke(revoked.session().id());
        assertThrows(ResponseStatusException.class,() -> sessions.form(revoked.token(),"pre",pre()));
        var original=invite(); var replacement=sessions.invite(Mode.SYNTHETIC,"guess",original.session().id());
        assertNotEquals(original.session().id(),replacement.session().id());
        assertEquals(original.session().id(),replacement.session().predecessorId());
        assertEquals(Stage.REPLACED,sessions.get(original.token()).stage());
        assertThrows(ResponseStatusException.class,() -> sessions.invite(Mode.SYNTHETIC,"guess",original.session().id()));
    }
    @Test void humanCollectionAndInvalidFormsFailClosed() {
        assertThrows(ResponseStatusException.class,() -> sessions.invite(Mode.PILOT,"guess",null));
        assertThrows(ResponseStatusException.class,() -> sessions.invite(Mode.MAIN,"context",null));
        var invitation=invite();
        assertThrows(ResponseStatusException.class,() -> sessions.form(invitation.token(),"pre",pre()));
        assertThrows(ResponseStatusException.class,() -> sessions.consent(invitation.token(),"wrong"));
        sessions.consent(invitation.token(),StudySessions.SYNTHETIC_CONSENT);
        var answers=pre(); answers.put("unexpected","private");
        assertThrows(ResponseStatusException.class,() -> sessions.form(invitation.token(),"pre",answers));
        assertEquals(Stage.PRE,sessions.get(invitation.token()).stage());
    }
    @Test void httpCredentialsCannotCrossScopesOrSkipProcedure() throws Exception {
        var first=invite(); var second=invite();
        http.perform(get("/api/study/session")).andExpect(status().isUnauthorized());
        http.perform(get("/api/study/session").header(StudyController.TOKEN_HEADER,first.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.session().id().toString()))
                .andExpect(jsonPath("$.tokenHash").doesNotExist()).andExpect(jsonPath("$.agentId").doesNotExist())
                .andExpect(header().string("Cache-Control","no-store"));
        http.perform(post("/api/study/forms/private").header(StudyController.TOKEN_HEADER,first.token())
                .contentType("application/json").content("{\"private\":\"secret\"}")).andExpect(status().isConflict());
        http.perform(get("/admin/study/attempts/"+second.session().id()).header(StudyController.TOKEN_HEADER,first.token()))
                .andExpect(status().isUnauthorized());
        http.perform(post("/admin/study/attempts").contentType("application/json").content("{\"mode\":\"SYNTHETIC\",\"scenario\":\"guess\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(Stage.INFORMATION,sessions.get(second.token()).stage());
    }
    @Test void legacyRoutesCannotReadOrMutateStudyAgents() throws Exception {
        var invitation=invite(); prepare(invitation); sessions.form(invitation.token(),"private",Map.of("private","secret"));
        UUID agent=agentId(invitation);
        for (String route:List.of("/"+agent+"/storage","/"+agent+"/eventhistory","/agent/"+agent,
                "/agent/"+agent+"/eventhistory","/demo/agents/"+agent+"/info","/"+agent+"/prompt"))
            http.perform(get(route).header(StudyController.TOKEN_HEADER,invitation.token())).andExpect(status().isNotFound());
        http.perform(post("/"+agent+"/acknowledge").contentType("application/json")
                .content("{\"type\":\"obs.user_utterance\",\"payload\":\"Start now\"}")).andExpect(status().isNotFound());
        for (String list:List.of("/agent","/agent/eventhistory")) {
            String body=http.perform(get(list)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains(agent.toString()));
        }
        verifyNoInteractions(gateway);
    }
}
