package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.zhaw.prometheus.application.*;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.repositories.AccessCodeRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

/** Opt-in measurement, not a capacity pass. Only the isolated runner can enable it. */
@EnabledIfEnvironmentVariable(named="PROMETHEUS_CLASSROOM_CAPACITY", matches="true")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL", matches="jdbc:mysql://(127\\.0\\.0\\.1|localhost|\\[::1\\]):[0-9]+/prometheus_gptlive_[0-9a-f]{10}")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "prometheus.runtime.tick.enabled=false", "prometheus.live.enabled=false", "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.maximum-pool-size=10", "spring.datasource.hikari.minimum-idle=1",
        "prometheus.sse.heartbeat.delay-ms=1000", "logging.level.org.hibernate.SQL=OFF",
        "logging.level.ch.zhaw.prometheus.model=OFF"})
class ClassroomCapacityIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired AccessCodeAdminService admin;
    @Autowired AccessCodeRepository codes;
    @Autowired HikariDataSource pool;
    @Autowired AgentApplicationService runtime;
    @MockitoBean LanguageModelGateway language;
    record Group(String code, UUID access, UUID id) { String path() { return "/demo/agents/" + id; } }
    @FunctionalInterface interface Work { void run(Group group) throws Exception; }
    final AtomicInteger requests = new AtomicInteger();
    final AtomicInteger streamOpens = new AtomicInteger();
    final Map<String, Queue<Long>> timings = new ConcurrentHashMap<>();
    final Map<String, Long> questions = new LinkedHashMap<>();

    @Test @Timeout(180)
    void sixScopedClientsMeasureARealMinuteWithoutHoldingStreamingConnections() throws Exception {
        var groups = new ArrayList<Group>(); var streams = new ArrayList<SseConnectionPoolIntegrationTest.Stream>();
        var cursors = new HashMap<UUID,String>();
        var report = new LinkedHashMap<String,Object>(); boolean functionalComplete = false;
        long started = System.nanoTime(), activeMillis = 0; int overruns = 0, streamsAfterMinute = 0;
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                long before = questions();
                for (int i=0; i<6; i++) {
                    String code = "Q" + UUID.randomUUID().toString().substring(0,4);
                    var access = admin.createAccessCode(code, true);
                    admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
                    assertEquals(200, request(client, "POST", "/demo/session", code, Map.of("accessCode",code)).statusCode());
                    var created = request(client, "POST", "/demo/agents", code, Map.of("agentDefinitionKey", CoordinationAgent.KEY));
                    assertEquals(201, created.statusCode(), created.body());
                    groups.add(new Group(code, access.getId(), UUID.fromString(json.readTree(created.body()).get("id").asText())));
                }
                for (Group group : groups) {
                    var behaviour = open(client, group, "/behaviour/stream", null); streams.add(behaviour);
                    cursors.put(group.id(), behaviour.next("behaviour-replay").id());
                    var monitor = open(client, group, "/monitor/stream", null); streams.add(monitor); monitor.next("snapshot");
                }
                questions.put("loginCreateAndStreamSetup", delta(before));
                before=questions(); Thread.sleep(5000); idle(); questions.put("fiveSecondWarmIdle", delta(before));
                before=questions();
                var proposal = json.createObjectNode();
                proposal.put("goal","Offer two short greetings on scissors"); proposal.put("humanRole","Supply deliberate signs and judge the response");
                proposal.put("agentRole","Offer one greeting per matching sign"); proposal.put("success","The observer reports useful output");
                proposal.putArray("questions"); proposal.putArray("checks"); proposal.put("dialogueLimit",2);
                var task=proposal.putObject("reactions"); task.put("goal",proposal.get("goal").asText());
                task.put("maxActions",2); task.put("sessionBound",true); task.put("firstAction","ON_CUE");
                var rule=task.putArray("rules").addObject(); rule.put("eventType","obs.hand.sign"); rule.put("field","sign"); rule.put("operator","eq");
                rule.put("value","scissor"); rule.put("action","Give one short greeting"); rule.put("effect","ACT");
                rule.put("minConfidence",0); rule.put("samples",1); rule.put("cooldownSeconds",3);
                var update=json.createObjectNode(); update.put("operation","PROPOSE"); update.set("agreement",proposal); update.putObject("reply").put("speech","Review this bounded proposal.");
                doReturn(update.toString()).when(language).infer(any());
                for (Group group:groups) { utterance(client,group,"Propose this bounded cue activity"); utterance(client,group,"Start"); }
                questions.put("proposalAndAcceptanceBurst",delta(before));
                doReturn("{\"speech\":\"Welcome to the demonstration.\"}").when(language).infer(any());
                before=questions(); long window=System.nanoTime();
                for (int slot=0; slot<12; slot++) {
                    sleepUntil(window + TimeUnit.SECONDS.toNanos(slot*5L));
                    final int interval=slot; long round=System.nanoTime();
                    parallel(executor,groups,group -> {
                        String timestamp=Instant.now().toString();
                        var batch=List.of(event("obs.human.presence",Map.of("humanCount",1,"avgDetectionConfidence",.9,"ts",timestamp)),
                                event("obs.social.grouping",Map.of("humanCount",1,"groupCount",0,"singletonCount",1,"largestGroupSize",1,"ts",timestamp)),
                                event("obs.social.context",Map.of("humanCount",1,"people",List.of(),"ts",timestamp)));
                        var batchResponse=request(client,"POST",group.path()+"/observations",group.code(),batch);
                        assertEquals(200,batchResponse.statusCode());
                        var results=json.readTree(batchResponse.body()); assertEquals(3,results.size());
                        for(var result:results) assertEquals(200,result.get("status").asInt());
                        observation(client,group,"obs.emotion.face",Map.of("emotion","neutral","confidence",.9,"ts",timestamp));
                        observation(client,group,"obs.hand.sign",Map.of("sign","paper","confidence",.9,"ts",Instant.now().toString()));
                        observation(client,group,"obs.hand.sign",Map.of("sign",interval==3||interval==8?"scissor":"rock","confidence",.9,"ts",Instant.now().toString()));
                        if(interval==1) utterance(client,group,"Help me");
                        if(interval==6) utterance(client,group,"What hand sign do you see?");
                    });
                    if(System.nanoTime()-round>TimeUnit.SECONDS.toNanos(5)) ++overruns;
                }
                sleepUntil(window+TimeUnit.SECONDS.toNanos(60)); idle();
                activeMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-window);
                streamsAfterMinute=(int)streams.stream().filter(stream -> stream.reader.isAlive()).count();
                assertEquals(12,streamsAfterMinute,"All twelve original SSE readers must remain alive throughout the minute");
                questions.put("activeMinute",delta(before));
                before=questions(); parallel(executor,groups,group -> runtime.tick(group.id())); idle();
                questions.put("controlledSixAgentTickProbe",delta(before));
                before=questions();
                doThrow(new IllegalStateException("Synthetic provider failure")).when(language).infer(any());
                parallel(executor,groups,group -> {
                    utterance(client,group,"Explain your capabilities");
                    for(String path:List.of("/state","/storage","/eventhistory"))
                        assertEquals(200,request(client,"GET",group.path()+path,group.code(),null).statusCode());
                });
                for(var stream:streams) stream.close(); streams.clear();
                for(Group group:groups) {
                    var behaviour=open(client,group,"/behaviour/stream",cursors.get(group.id())); streams.add(behaviour);
                    assertNotEquals(cursors.get(group.id()),behaviour.next("behaviour-replay").id());
                    var monitor=open(client,group,"/monitor/stream",null); streams.add(monitor); monitor.next("snapshot");
                    var storage=json.readTree(request(client,"GET",group.path()+"/storage",group.code(),null).body());
                    assertTrue(storage.toString().contains("LIMIT_REACHED"));
                    for(var entry:storage) if(entry.get("key").asText().equals("task.actions")) assertEquals("2",entry.get("value").asText());
                }
                assertEquals(404,request(client,"GET",groups.getFirst().path()+"/eventhistory",groups.getLast().code(),null).statusCode());
                idle(); questions.put("recoveryReloadReconnectBurst",delta(before)); functionalComplete=true;
            } finally {
                long before=questions();
                for(var stream:streams) stream.close();
                for(Group group:groups) { assertEquals(204,request(client,"DELETE",group.path(),group.code(),null).statusCode()); codes.deleteById(group.access()); }
                idle(); questions.put("cleanup",delta(before));
                long projected=questions.getOrDefault("activeMinute",0L)*60;
                long fixed=questions.getOrDefault("loginCreateAndStreamSetup",0L)+questions.getOrDefault("proposalAndAcceptanceBurst",0L)
                        +questions.getOrDefault("recoveryReloadReconnectBurst",0L)+questions.getOrDefault("cleanup",0L);
                report.put("schema",pool.getJdbcUrl().substring(pool.getJdbcUrl().lastIndexOf('/')+1));
                report.put("clients",6); report.put("streamsConfirmedAfterActiveMinute",streamsAfterMinute); report.put("functionalAcceptance",functionalComplete);
                report.put("questionsByPhase",questions); report.put("activeWallMillis",activeMillis); report.put("slotOverruns",overruns);
                report.put("projectedActiveQuestionsPerHour",projected); report.put("fixedSetupRecoveryCleanupQuestions",fixed);
                report.put("projectedHourIncludingOneFixedBurst",projected+fixed); report.put("quotaPerHour",36000);
                report.put("twentyPercentHeadroomBudget",28800); report.put("numericHeadroomMet",projected+fixed<=28800);
                report.put("classroomReady",false); report.put("sustainedClassroomAcceptance","NOT RUN: one minute cannot establish a 60–70 minute class");
                report.put("scope","Real six-client scoped HTTP, 12 SSE streams, ten JDBC connections; per client 12 social batches, 12 face, 24 hand samples, 2 questions and 2 matching task cues. Offline providers. MySQL GLOBAL Questions includes direct JDBC/transaction work on this exclusively owned server.");
                report.put("exclusions",List.of("real OpenAI/Live receipts and media", "physical detector accuracy and jitter", "long production histories", "automatic scheduled ticks (configured off; separate controlled probe)", "60–70 minute sustained workload"));
                report.put("nonStreamingHttpRequests",requests.get()); report.put("streamOpenAttempts",streamOpens.get()); report.put("requestLatencyMs",latencies());
                report.put("elapsedMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
                Files.writeString(Path.of("target/classroom-capacity-report.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
                System.out.println("CLASSROOM_CAPACITY numericHeadroomMet="+report.get("numericHeadroomMet")+" classroomReady=false projected="+(projected+fixed));
            }
        }
    }
    long questions() throws Exception {
        try(var connection=pool.getConnection(); var statement=connection.createStatement(); var result=statement.executeQuery("SHOW GLOBAL STATUS LIKE 'Questions'")) { assertTrue(result.next()); return result.getLong(2); }
    }
    long delta(long before) throws Exception { return questions()-before-1; }
    void idle() throws Exception {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections()); assertEquals(0,pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
        }); Thread.sleep(100);
    }
    void sleepUntil(long nanos) throws InterruptedException { long remaining=nanos-System.nanoTime(); if(remaining>0) TimeUnit.NANOSECONDS.sleep(remaining); }
    void parallel(ExecutorService executor,List<Group> groups,Work work) throws Exception {
        var pending=new ArrayList<Future<?>>();
        for(Group group:groups) pending.add(executor.submit(() -> { work.run(group); return null; }));
        for(var future:pending) future.get(40,TimeUnit.SECONDS);
    }
    Object event(String type,Object payload) throws Exception { return Map.of("type",type,"actor","camera","kind","observation","payload",json.writeValueAsString(payload)); }
    void observation(HttpClient client,Group group,String type,Object payload) throws Exception {
        assertEquals(200,request(client,"POST",group.path()+"/acknowledge",group.code(),event(type,payload)).statusCode());
    }
    void utterance(HttpClient client,Group group,String words) throws Exception {
        assertEquals(200,request(client,"POST",group.path()+"/acknowledge",group.code(),Map.of("type","obs.user_utterance","actor","user","kind","observation","payload",words)).statusCode());
    }
    HttpRequest.Builder builder(String path,String code) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10)).header(ScopedDemoController.ACCESS_CODE_HEADER,code);
    }
    HttpResponse<String> request(HttpClient client,String method,String path,String code,Object body) throws Exception {
        assertTrue(requests.incrementAndGet()<=1000,"Finite classroom request budget"); long before=System.nanoTime();
        try { return client.send(builder(path,code).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString()); }
        finally { timings.computeIfAbsent(method+" "+path.replaceFirst("/demo/agents/[^/]+","/agent"),ignored -> new ConcurrentLinkedQueue<>()).add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)); }
    }
    SseConnectionPoolIntegrationTest.Stream open(HttpClient client,Group group,String suffix,String cursor) throws Exception {
        streamOpens.incrementAndGet();
        var request=builder(group.path()+suffix,group.code()).GET(); if(cursor!=null) request.header("Last-Event-ID",cursor);
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofInputStream()); assertEquals(200,response.statusCode());
        return new SseConnectionPoolIntegrationTest.Stream(response.body());
    }
    Object latencies() {
        var result=new TreeMap<String,Object>(); timings.forEach((path,values) -> {
            var sorted=new ArrayList<>(values); Collections.sort(sorted);
            result.put(path,Map.of("count",sorted.size(),"p95",sorted.get(Math.max(0,(int)Math.ceil(sorted.size()*.95)-1)),"max",sorted.getLast()));
        }); return result;
    }
}
