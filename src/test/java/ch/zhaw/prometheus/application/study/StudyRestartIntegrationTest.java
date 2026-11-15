package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import ch.zhaw.prometheus.PrometheusApplication;

/** Two independent application contexts using the runner's owned MySQL schema. */
class StudyRestartIntegrationTest {
    @Test void restartPreservesAssignmentFormsAndAgentIdentity() {
        String database=System.getenv("SPRING_DATASOURCE_URL");
        assertNotNull(database,"Use the isolated acceptance runner");
        assertTrue(database.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1):[0-9]+/prometheus_gptlive_[a-z0-9]+"));
        String token; UUID id; UUID agent;
        var request=new StudyTurns.Request(UUID.randomUUID(),0,"What is our current state?");
        UUID responseId;
        try (var first=new SpringApplicationBuilder(PrometheusApplication.class).web(WebApplicationType.NONE).run()) {
            var sessions=first.getBean(StudySessions.class);
            var invitation=sessions.invite(StudyAttempt.Mode.SYNTHETIC,"signs",null);
            token=invitation.token(); id=invitation.session().id();
            sessions.consent(token,StudySessions.SYNTHETIC_CONSENT);
            var answers=new HashMap<String,String>();
            first.getBean(StudyMaterials.class).manifest().pre().forEach(i -> answers.put(i.id(),i.kind().equals("rating7")?"4":"None"));
            sessions.form(token,"pre",answers);
            sessions.form(token,"intention",Map.of("intention","Negotiate simple signs"));
            sessions.form(token,"private",Map.of("private","Open palm means wait"));
            agent=first.getBean(StudyAttemptRepository.class).findById(id).orElseThrow().agentId;
            var result=first.getBean(StudyTurns.class).submit(token,request);
            assertEquals(StudyTurn.Status.COMMITTED,result.status()); responseId=result.responseEventId();
        }
        try (var restarted=new SpringApplicationBuilder(PrometheusApplication.class).web(WebApplicationType.NONE).run()) {
            var session=restarted.getBean(StudySessions.class).get(token);
            assertEquals(id,session.id()); assertEquals("signs",session.scenario());
            assertEquals(StudyAttempt.Stage.COORDINATION,session.stage());
            assertEquals(agent,restarted.getBean(StudyAttemptRepository.class).findById(id).orElseThrow().agentId);
            assertEquals(3,restarted.getBean(StudyFormRepository.class).findByAttemptIdOrderByCreatedAt(id).size());
            var repeated=restarted.getBean(StudyTurns.class).submit(token,request);
            assertEquals(StudyTurn.Status.COMMITTED,repeated.status()); assertEquals(responseId,repeated.responseEventId());
            assertEquals(1,restarted.getBean(StudyTurnRepository.class).findByAttemptIdOrderByReceivedAt(id).size());
        }
    }
}
