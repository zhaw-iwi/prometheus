package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ch.zhaw.prometheus.agentdefs.AgentDefinitionRegistry;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.agentdefs.core.CoordinationPolicy;
import ch.zhaw.prometheus.controllers.views.EventRequest;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.coordination.CoordinationMemory;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.OutputProfile;
import ch.zhaw.prometheus.model.task.*;
import ch.zhaw.prometheus.repositories.AgentRepository;
import ch.zhaw.prometheus.spi.LanguageModelGateway;
import ch.zhaw.prometheus.spi.SpeechSynthesisGateway;

/** Run only with the disposable local MySQL runner documented in README. */
@SpringBootTest(properties = {"prometheus.runtime.tick.enabled=false"})
class CoordinationAgentIntegrationTest {
    @Autowired AgentDefinitionRegistry registry;
    @Autowired AgentRepository agents;
    @Autowired AccessCodeAdminService admin;
    @Autowired ScopedDemoService demo;
    @Autowired GenericTaskSessionLifecycleService lifecycle;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LiveTranscriptIngressService ingress;
    @Autowired ExternalSpeechOwnership ownership;
    @Autowired LiveAgentContextService liveContexts;
    @Autowired LiveContextBridgeService liveBridge;
    @Autowired ScopedBehaviourSpeechService canonicalSpeech;
    @MockitoBean LanguageModelGateway gateway;
    @MockitoBean SpeechSynthesisGateway speech;
    static final String AGREEMENT = """
            {"goal":"Draft an invitation", "humanRole":"Supply the details", "agentRole":"Edit only those details",
            "success":"The organiser accepts the invitation", "questions":[], "checks":[], "dialogueLimit":4, "reactions":null}
            """;
    record Scope(String code, UUID id) {}
    Scope create() {
        String code = UUID.randomUUID().toString().substring(0, 5);
        var access = admin.createAccessCode(code, true);
        admin.replaceAllowedAgentTypes(access.getId(), List.of(CoordinationAgent.KEY));
        return new Scope(code, demo.createAgent(code, CoordinationAgent.KEY).getID());
    }
    void turn(Scope scope, String op, String agreement, String utterance) {
        when(gateway.infer(any())).thenReturn("{\"operation\":\"" + op + "\",\"agreement\":" + agreement + ",\"reply\":{\"speech\":\"Shall we start now?\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, utterance), OutputProfile.FULL_PLAN).orElseThrow();
    }
    Storage memory(Scope scope) {
        var agent = agents.findById(scope.id()).orElseThrow();
        assertInstanceOf(CoordinationPolicy.class, agent.getCurrentState().ownPolicy());
        return ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage();
    }
    @Test void legacyScissorsAgreementAndHistorySurviveReloadUnchanged() throws Exception {
        var scope = create(); JsonObject proposal;
        String instructions = liveContexts.snapshot(scope.code(), scope.id()).orElseThrow().instructions();
        int bytes = instructions.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        assertTrue(bytes <= 16000, "Keep the existing Live instruction byte ceiling");
        System.out.println("Hand baseline Live instruction characters=" + instructions.length() + ", bytes=" + bytes);
        try (var input = getClass().getResourceAsStream("/hand-interaction/legacy-agreement.json")) {
            proposal = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        turn(scope, "PROPOSE", proposal.toString(), "Propose one acknowledgement on scissors");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        turn(scope, "START", "null", "Start now");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.AGREEMENT));
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("scissor", memory(scope).get(TaskMemory.SPEC).getAsJsonObject()
                .getAsJsonArray("rules").get(0).getAsJsonObject().get("value").getAsString());
        var before = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        var after = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        assertEquals(before.stream().map(Event::getId).toList(), after.stream().map(Event::getId).toList());
        assertEquals(before.stream().map(Event::getPayload).toList(), after.stream().map(Event::getPayload).toList());
        assertEquals(before.stream().map(Event::sourcePosition).toList(), after.stream().map(Event::sourcePosition).toList());
        System.out.println("HI-01 legacy scissors: persisted events=" + after.size() + ", actions=0");
    }
    @Test void inputSequenceProgressReloadsAndRestartRequiresANewWholeSequence() throws Exception {
        var scope=create();var proposal=fixtures.behaviour.HandSequenceFixture.agreement(fixtures.behaviour.HandSequenceFixture.counts("right",1,2,3));
        turn(scope,"PROPOSE",proposal.toString(),"Propose one acknowledgement after one then two then three");
        sequenceFrame(scope,1,1,1);Thread.sleep(325);sequenceFrame(scope,1,2,1);
        assertEquals(1,HandSequenceMemory.progress(memory(scope)).getAsJsonObject("prep-0").get("index").getAsInt());
        sequenceFrame(scope,2,1,2);assertEquals("restart_required",HandSequenceMemory.progress(memory(scope)).getAsJsonObject("prep-0").get("status").getAsString());
        turn(scope,"KEEP","null","Restart preparation");assertTrue(HandSequenceMemory.progress(memory(scope)).isEmpty());
        for(int i=0;i<6;i++){Thread.sleep(325);sequenceFrame(scope,1+i/2,i+2,2);}
        assertEquals("READY",CoordinationMemory.stage(memory(scope)));turn(scope,"START","null","Start now");assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long delay=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();if(delay>=0)Thread.sleep(delay+25);
        sequenceFrame(scope,1,8,2);Thread.sleep(325);sequenceFrame(scope,1,9,2);assertEquals(1,HandSequenceMemory.progress(memory(scope)).getAsJsonObject("run-0").get("index").getAsInt());
        turn(scope,"PAUSE","null","Pause");turn(scope,"RESUME","null","Resume");assertTrue(HandSequenceMemory.progress(memory(scope)).isEmpty());
        delay=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();if(delay>=0)Thread.sleep(delay+25);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"Sequence complete.\"}");clearInvocations(gateway);
        for(int i=0;i<6;i++){Thread.sleep(325);sequenceFrame(scope,1+i/2,i+10,2);assertEquals(i==5?1:0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());}
        Thread.sleep(325);sequenceFrame(scope,3,16,2);verify(gateway,times(1)).infer(any());assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));
        turn(scope,"PROPOSE",proposal.toString(),"Propose the same sequence again");assertTrue(HandSequenceMemory.progress(memory(scope)).keySet().stream().noneMatch(k->k.startsWith("prep-")));assertEquals("PREPARATION",CoordinationMemory.stage(memory(scope)));
        turn(scope,"STOP","null","Stop");assertEquals("REFLECTION",CoordinationMemory.stage(memory(scope)));demo.reset(scope.code(),scope.id());assertTrue(HandSequenceMemory.progress(memory(scope)).isEmpty());
    }
    private void sequenceFrame(Scope scope,int count,int ordinal,int revision){
        var p=fixtures.behaviour.HandSequenceFixture.pose("frame-"+revision+"-"+ordinal,count,false,Instant.now());p.addProperty("captureIndex",ordinal);p.getAsJsonObject("frame").addProperty("revision",revision);
        demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_HAND_POSE,"camera",Event.KIND_OBSERVATION,p.toString()),OutputProfile.FULL_PLAN).orElseThrow();
    }
    @Test void namedSignPreparationAndFreshCuePersistWithoutReusingReadiness() throws Exception {
        var scope = create(); var proposal = fixtures.behaviour.NamedHandSignFixture.agreement();
        proposal.getAsJsonObject("reactions").getAsJsonArray("rules").get(0).getAsJsonObject().addProperty("action", "Show peace silently with both hands");
        turn(scope, "PROPOSE", proposal.toString(), "Prepare a right-hand thumbs up, then acknowledge its next accepted cue once");
        assertEquals("PREPARATION", CoordinationMemory.stage(memory(scope)));
        java.util.function.BiConsumer<String,String> observe = (id, sign) -> demo.acknowledge(scope.code(), scope.id(),
                new EventRequest(Event.TYPE_HAND_SIGNS, "camera", Event.KIND_OBSERVATION,
                        fixtures.behaviour.NamedHandSignFixture.observation(id, sign, "right", Instant.now()).toString()), OutputProfile.FULL_PLAN).orElseThrow();
        observe.accept("wrong", "peace"); assertEquals(0, CoordinationMemory.evidence(memory(scope)).size());
        observe.accept("preparation", "thumbs_up"); assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
        turn(scope, "START", "null", "Start now"); assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long wait = java.time.Duration.between(Instant.now(), Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(wait < 5000); if (wait >= 0) Thread.sleep(wait + 25);
        observe.accept("preparation", "thumbs_up"); assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var motion = new JsonObject(); motion.add("handAction", ch.zhaw.prometheus.model.hand.HandActions.named("peace", "both_hands"));
        var output = new JsonObject(); output.add("motion", motion); when(gateway.infer(any())).thenReturn(output.toString());
        observe.accept("fresh-cue", "thumbs_up"); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        observe.accept("fresh-cue", "thumbs_up"); assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var plans = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream()
                .filter(e -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                .map(e -> ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(e.getPayload()))
                .filter(p -> p.getMotion() != null).toList();
        assertEquals(1, plans.size()); assertEquals(motion, plans.getFirst().getMotion()); assertNull(plans.getFirst().getSpeech());
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(proposal, memory(scope).get(CoordinationMemory.AGREEMENT));
        assertEquals(2, memory(scope).get(ch.zhaw.prometheus.model.hand.HandEvidence.CONSUMED).getAsJsonArray().size());
    }
    @Test void composedPosePreparationReloadAndDirectOutputPreserveTheAcceptedAgreement() throws Exception {
        var scope=create();var proposal=fixtures.behaviour.ComposedHandFixture.agreement();
        turn(scope,"PROPOSE",proposal.toString(),"Prepare a custom two-hand pattern, then show its response once");
        var left=fixtures.behaviour.ComposedHandFixture.hand("left","left",0,0,.5,1,0);
        var right=fixtures.behaviour.ComposedHandFixture.hand("right","right",1,1,1,.5,1);
        java.util.function.BiConsumer<String,JsonObject[]> observe=(id,hands)->demo.acknowledge(scope.code(),scope.id(),
                new EventRequest(Event.TYPE_HAND_POSE,"camera",Event.KIND_OBSERVATION,
                        fixtures.behaviour.ComposedHandFixture.observation(id,Instant.now(),hands).toString()),OutputProfile.FULL_PLAN).orElseThrow();
        observe.accept("missing",new JsonObject[]{left});assertEquals("PREPARATION",CoordinationMemory.stage(memory(scope)));
        observe.accept("ready",new JsonObject[]{right,left});assertEquals("READY",CoordinationMemory.stage(memory(scope)));
        assertEquals(proposal,memory(scope).get(CoordinationMemory.DRAFT));
        turn(scope,"START","null","Start now");assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var pose=fixtures.behaviour.ComposedHandFixture.output();
        when(gateway.infer(any())).thenReturn("{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{},\"command\":{\"intent\":\"ACTION\",\"kind\":\"HAND_POSE\",\"silent\":true,\"value\":"+pose+"}}");
        demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,"Show this unrelated custom pose silently"),OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long wait=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(wait<5000);if(wait>=0)Thread.sleep(wait+25);
        observe.accept("ready",new JsonObject[]{left,right});assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var motion=new JsonObject();motion.add("handAction",ch.zhaw.prometheus.model.hand.HandActions.pose(pose));
        var output=new JsonObject();output.add("motion",motion);when(gateway.infer(any())).thenReturn(output.toString());
        observe.accept("fresh",new JsonObject[]{left,right});assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        observe.accept("fresh",new JsonObject[]{left,right});assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));
        var plans=agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream()
                .filter(e->Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                .map(e->ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(e.getPayload()))
                .filter(p->p.getMotion()!=null).toList();
        assertEquals(2,plans.size());for(var plan:plans){assertEquals(motion,plan.getMotion());assertNull(plan.getSpeech());}
    }
    @Test void relationalPosePreparationReloadAndDirectOutputPreserveTheAcceptedAgreement() throws Exception {
        var scope=create();var proposal=fixtures.behaviour.RelationalHandFixture.agreement();
        turn(scope,"PROPOSE",proposal.toString(),"Prepare a custom two-hand pattern, then show its response once");
        var left=fixtures.behaviour.ComposedHandFixture.hand("left","left",0,0,.5,1,0);
        var right=fixtures.behaviour.ComposedHandFixture.hand("right","right",1,1,1,.5,1);
        java.util.function.BiConsumer<String,JsonObject[]> observe=(id,hands)->demo.acknowledge(scope.code(),scope.id(),
                new EventRequest(Event.TYPE_HAND_POSE,"camera",Event.KIND_OBSERVATION,
                        fixtures.behaviour.ComposedHandFixture.observation(id,Instant.now(),hands).toString()),OutputProfile.FULL_PLAN).orElseThrow();
        left.add("geometry",fixtures.behaviour.RelationalHandFixture.geometry());
        observe.accept("missing",new JsonObject[]{left});assertEquals("PREPARATION",CoordinationMemory.stage(memory(scope)));
        observe.accept("ready",new JsonObject[]{right,left});assertEquals("READY",CoordinationMemory.stage(memory(scope)));
        assertEquals(proposal,memory(scope).get(CoordinationMemory.DRAFT));
        turn(scope,"START","null","Start now");assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var pose=fixtures.behaviour.RelationalHandFixture.output();
        when(gateway.infer(any())).thenReturn("{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{},\"command\":{\"intent\":\"ACTION\",\"kind\":\"HAND_POSE\",\"silent\":true,\"value\":"+pose+"}}");
        demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,"Show this unrelated custom pose silently"),OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long wait=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(wait<5000);if(wait>=0)Thread.sleep(wait+25);
        observe.accept("ready",new JsonObject[]{left,right});assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var motion=new JsonObject();motion.add("handAction",ch.zhaw.prometheus.model.hand.HandActions.pose(pose));
        var output=new JsonObject();output.add("motion",motion);when(gateway.infer(any())).thenReturn(output.toString());
        observe.accept("fresh",new JsonObject[]{left,right});assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        observe.accept("fresh",new JsonObject[]{left,right});assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));
        var plans=agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream()
                .filter(e->Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                .map(e->ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(e.getPayload()))
                .filter(p->p.getMotion()!=null).toList();
        assertEquals(2,plans.size());for(var plan:plans){assertEquals(motion,plan.getMotion());assertNull(plan.getSpeech());}
    }
    @Test void pointingFrameBindingReloadAndDirectOutputPreserveTheAgreement() throws Exception {
        var scope=create();var proposal=fixtures.behaviour.PointingFixture.agreement();
        java.util.function.Consumer<JsonObject> observe=data->demo.acknowledge(scope.code(),scope.id(),
                new EventRequest(Event.TYPE_HAND_POINTING,"camera",Event.KIND_OBSERVATION,data.toString()),OutputProfile.FULL_PLAN).orElseThrow();
        observe.accept(fixtures.behaviour.PointingFixture.observation("frame","left",Instant.now()));
        turn(scope,"PROPOSE",proposal.toString(),"Facing the camera, prepare my image-right point then point to your right once");
        assertEquals(proposal,memory(scope).get(CoordinationMemory.DRAFT));
        observe.accept(fixtures.behaviour.PointingFixture.observation("wrong","left",Instant.now()));
        assertEquals("PREPARATION",CoordinationMemory.stage(memory(scope)));
        observe.accept(fixtures.behaviour.PointingFixture.observation("prepare","right",Instant.now()));
        assertEquals("READY",CoordinationMemory.stage(memory(scope)));turn(scope,"START","null","Start now");
        long wait=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(wait<5000);if(wait>=0)Thread.sleep(wait+25);
        var changed=fixtures.behaviour.PointingFixture.observation("changed","right",Instant.now());changed.getAsJsonObject("frame").addProperty("revision",2);
        observe.accept(changed);assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        observe.accept(fixtures.behaviour.PointingFixture.observation("prepare","right",Instant.now()));assertEquals(0,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var motion=new JsonObject();motion.add("handAction",ch.zhaw.prometheus.model.hand.HandActions.pose(fixtures.behaviour.PointingFixture.action("right")));
        var output=new JsonObject();output.add("motion",motion);when(gateway.infer(any())).thenReturn(output.toString());
        observe.accept(fixtures.behaviour.PointingFixture.observation("fresh","right",Instant.now()));
        assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var direct=new JsonObject();direct.addProperty("operation","KEEP");direct.add("agreement",com.google.gson.JsonNull.INSTANCE);direct.add("reply",new JsonObject());
        var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","POINT");command.addProperty("silent",true);command.add("value",fixtures.behaviour.PointingFixture.action("left"));direct.add("command",command);
        when(gateway.infer(any())).thenReturn(direct.toString());
        demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,"Point to your left silently"),OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("POINT",memory(scope).get("command.lastResult").getAsJsonObject().get("kind").getAsString());
        var plans=agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream().filter(e->Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType()))
                .map(e->ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(e.getPayload())).filter(plan->plan.getMotion()!=null).toList();
        assertEquals(2,plans.size());assertTrue(plans.stream().allMatch(plan->plan.getSpeech()==null));
    }
    @Test void configuredTargetsStayScopedPersistAndInvalidateWithoutRedirectingAcceptedTerms() throws Exception {
        var scope=create();var other=create();
        java.util.function.Consumer<JsonObject> observe=data->demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_HAND_POINTING,"camera",Event.KIND_OBSERVATION,data.toString()),OutputProfile.FULL_PLAN).orElseThrow();
        observe.accept(fixtures.behaviour.PointingFixture.observation("initial","right",Instant.now()));
        var request=fixtures.behaviour.HandSceneFixture.request(0);
        assertTrue(demo.configureHandScene(other.code(),scope.id(),request.toString()).isEmpty());
        var scene=JsonParser.parseString(demo.configureHandScene(scope.code(),scope.id(),request.toString()).orElseThrow()).getAsJsonObject();
        assertEquals(scene,JsonParser.parseString(demo.handScene(scope.code(),scope.id()).orElseThrow()));
        var target=scene.getAsJsonArray("targets").get(0).getAsJsonObject();var proposal=fixtures.behaviour.HandSceneFixture.agreement(target);
        turn(scope,"PROPOSE",proposal.toString(),"When I point at the registered display, explain it once");assertEquals(proposal,memory(scope).get(CoordinationMemory.DRAFT));
        var fabricated=fixtures.behaviour.PointingFixture.observation("false-selection","left",Instant.now());
        var claim=new JsonObject();claim.addProperty("status","resolved");claim.add("target",ch.zhaw.prometheus.model.hand.HandScene.ref(target).json());fabricated.add("resolution",claim);observe.accept(fabricated);
        assertEquals("PREPARATION",CoordinationMemory.stage(memory(scope)),"Client-supplied selection must be recomputed");
        observe.accept(fixtures.behaviour.PointingFixture.observation("prepare-target","right",Instant.now()));assertEquals("READY",CoordinationMemory.stage(memory(scope)));turn(scope,"START","null","Start now");
        long wait=java.time.Duration.between(Instant.now(),Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();assertTrue(wait<5000);if(wait>=0)Thread.sleep(wait+25);
        when(gateway.infer(any())).thenReturn("{\"speech\":\"The registered display shows the current slide.\"}");observe.accept(fixtures.behaviour.PointingFixture.observation("cue-target","right",Instant.now()));
        assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));
        var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","POINT");command.addProperty("silent",true);command.add("value",fixtures.behaviour.HandSceneFixture.action(target));
        var direct=new JsonObject();direct.addProperty("operation","KEEP");direct.add("agreement",JsonNull.INSTANCE);direct.add("reply",new JsonObject());direct.add("command",command);when(gateway.infer(any())).thenReturn(direct.toString());
        var say=new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,"Point at the registered display silently");demo.acknowledge(scope.code(),scope.id(),say,OutputProfile.FULL_PLAN).orElseThrow();
        var receipt=memory(scope).get("command.lastResult").getAsJsonObject();assertEquals("ACCEPTED",receipt.get("status").getAsString());assertTrue(receipt.getAsJsonObject("handAction").getAsJsonObject("action").has("resolved"));
        var deleted=fixtures.behaviour.HandSceneFixture.request(1);deleted.add("targets",new JsonArray());demo.configureHandScene(scope.code(),scope.id(),deleted.toString()).orElseThrow();
        demo.acknowledge(scope.code(),scope.id(),say,OutputProfile.FULL_PLAN).orElseThrow();assertEquals("CLARIFY",memory(scope).get("command.lastResult").getAsJsonObject().get("status").getAsString());
        assertEquals(proposal,memory(scope).get(CoordinationMemory.AGREEMENT));assertEquals(1,memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertThrows(IllegalArgumentException.class,()->demo.configureHandScene(scope.code(),scope.id(),request.toString()));
    }
    @Test void detectedTargetsPersistWithoutRevivingExpiredLabelsAndDelayedInferenceCannotPoint() throws Exception {
        var scope=create();var other=create();
        java.util.function.Consumer<JsonObject> observe=data->demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_SCENE_TARGETS,"camera",Event.KIND_OBSERVATION,data.toString()),OutputProfile.FULL_PLAN).orElseThrow();
        var scene=JsonParser.parseString(demo.configureHandScene(scope.code(),scope.id(),fixtures.behaviour.ObservedTargetsFixture.virtualRequest(0).toString()).orElseThrow()).getAsJsonObject();
        var snapshot=fixtures.behaviour.ObservedTargetsFixture.cup(Instant.now());observe.accept(snapshot);
        var stored=memory(scope).get(ch.zhaw.prometheus.model.hand.ObservedTargets.KEY).getAsJsonObject();var target=stored.getAsJsonArray("targets").get(0).getAsJsonObject();
        var binding=fixtures.behaviour.ObservedTargetsFixture.binding(target,scene.getAsJsonArray("targets").get(0).getAsJsonObject());
        assertTrue(demo.bindHandTarget(other.code(),scope.id(),binding.toString()).isEmpty());
        demo.bindHandTarget(scope.code(),scope.id(),binding.toString()).orElseThrow();assertEquals(binding,memory(scope).get(ch.zhaw.prometheus.model.hand.ObservedTargets.BINDINGS).getAsJsonArray().get(0));
        int historySize=agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size();observe.accept(snapshot);assertEquals(historySize,agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size());
        var command=new JsonObject();command.addProperty("intent","ACTION");command.addProperty("kind","POINT");command.addProperty("silent",true);command.add("value",fixtures.behaviour.HandSceneFixture.action(target));
        String answer="{\"operation\":\"KEEP\",\"agreement\":null,\"reply\":{},\"command\":"+command+"}";
        when(gateway.infer(any())).thenAnswer(call->{Thread.sleep(3100);return answer;});
        demo.acknowledge(scope.code(),scope.id(),new EventRequest(Event.TYPE_USER_UTTERANCE,"user",Event.KIND_OBSERVATION,"Point at my cup silently"),OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("CLARIFY",memory(scope).get("command.lastResult").getAsJsonObject().get("status").getAsString());
        assertTrue(ch.zhaw.prometheus.model.hand.ObservedTargets.fresh(memory(scope),Instant.now()).isEmpty());
        observe.accept(fixtures.behaviour.ObservedTargetsFixture.cup(Instant.now()));var revived=memory(scope).get(ch.zhaw.prometheus.model.hand.ObservedTargets.KEY).getAsJsonObject().getAsJsonArray("targets").get(0).getAsJsonObject();
        assertNotEquals(target.get("revision"),revived.get("revision"));assertThrows(IllegalArgumentException.class,()->ch.zhaw.prometheus.model.hand.ObservedTargets.binding(memory(scope),ch.zhaw.prometheus.model.hand.HandScene.ref(revived),Instant.now()));
        assertEquals(binding,memory(scope).get(ch.zhaw.prometheus.model.hand.ObservedTargets.BINDINGS).getAsJsonArray().get(0));
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().stream().filter(e->Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType())).noneMatch(e->e.getPayload().contains("\"resolved\"")));
    }
    @Test void namedIngressRejectsMalformedAndUndeclaredInputBeforePersistingHistory() {
        var scope = create(); int initial = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size();
        assertThrows(IllegalArgumentException.class, () -> demo.acknowledge(scope.code(), scope.id(),
                new EventRequest(Event.TYPE_HAND_SIGNS, "camera", Event.KIND_OBSERVATION, "{}"), OutputProfile.FULL_PLAN));
        String valid = fixtures.behaviour.NamedHandSignFixture.observation("frame", "peace", "left", Instant.now()).toString();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var agent = agents.findById(scope.id()).orElseThrow();
            var profile = JsonParser.parseString(agent.getInteractionProfile().toJson()).getAsJsonObject();
            profile.getAsJsonArray("supportedObservations").remove(new JsonPrimitive(Event.TYPE_HAND_SIGNS));
            agent.setInteractionProfile(ch.zhaw.prometheus.model.interaction.AgentInteractionProfile.fromJson(profile.toString()));
            agents.saveAndFlush(agent);
        });
        assertThrows(IllegalArgumentException.class, () -> demo.acknowledge(scope.code(), scope.id(),
                new EventRequest(Event.TYPE_HAND_SIGNS, "camera", Event.KIND_OBSERVATION, valid), OutputProfile.FULL_PLAN));
        assertEquals(initial, agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size());
    }
    @Test void acceptedPersistedTaskWithdrawsQueuedProposalAndReconnectNeverReplaysIt() throws Exception {
        var scope = create(); var sessionId = UUID.randomUUID();
        var initial = liveContexts.claim(scope.code(), scope.id(), sessionId).orElseThrow();
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(sessionId, initial.epoch());
        var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        var sent = new java.util.concurrent.CopyOnWriteArrayList<ch.zhaw.prometheus.application.live.LiveContextDelivery.Command>();
        var bridge = liveBridge.open(initial, owner, command -> {
            sent.add(command);
            if (command.currentOnly() && entered.getCount() > 0) {
                entered.countDown();
                try { assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
            }
        }, ignored -> {}, reason -> fail(reason));
        try {
            bridge.ready();
            var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
            proposal.add("reactions", JsonParser.parseString("""
                {"goal":"Draft an invitation","maxActions":1,"sessionBound":true,"firstAction":"ON_CUE",
                 "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                 "action":"Offer one invitation idea","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}]}
                """));
            turn(scope, "PROPOSE", proposal.toString(), "Propose one invitation idea on rock, without starting");
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            UUID old = UUID.fromString(memory(scope).get("core.task.response").getAsJsonObject().get("id").getAsString());
            turn(scope, "START", "null", "Start now");
            var reloaded = memory(scope);
            assertEquals("EXECUTION", CoordinationMemory.stage(reloaded));
            assertEquals("0", TaskMemory.text(reloaded, TaskMemory.ACTIONS, "0"));
            assertEquals(proposal, reloaded.get(CoordinationMemory.AGREEMENT));
            UUID current = UUID.fromString(reloaded.get("core.task.response").getAsJsonObject().get("id").getAsString());
            release.countDown();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> sent.stream()
                    .anyMatch(c -> current.equals(c.sourceId()) && c.type().equals("session.commentary.append")));
            assertTrue(sent.stream().noneMatch(c -> old.equals(c.sourceId()) && c.type().equals("session.commentary.append")));
            assertEquals(1, sent.stream().filter(c -> c.content().contains("is withdrawn")).count());
            turn(scope, "STOP", "null", "Stop the task");
            assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
            bridge.close(); ownership.release(scope.id(), sessionId);
            UUID secondId = UUID.randomUUID();
            var reconnect = liveContexts.claim(scope.code(), scope.id(), secondId).orElseThrow();
            var secondOwner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(secondId, reconnect.epoch());
            var delivery = new ch.zhaw.prometheus.application.live.LiveContextDelivery(reconnect); delivery.ready();
            assertTrue(delivery.plan(reconnect, List.of()).commands().isEmpty());
            ownership.release(scope.id(), secondId);
        } finally { release.countDown(); bridge.close(); ownership.release(scope.id(), sessionId); }
    }
    @Test void freshLiveClaimClearsSavedOutputFlagsBeforeProjectingNativeInstructions() {
        var scope = create();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var agent = agents.findById(scope.id()).orElseThrow();
            var storage = ((CoordinationPolicy) agent.getCurrentState().ownPolicy()).storage();
            storage.put("live.speechApproval", new JsonPrimitive(true));
            storage.put("live.committedSpeech", new JsonPrimitive(true));
            agents.saveAndFlush(agent);
        });
        var session = UUID.randomUUID();
        try {
            var context = liveContexts.claim(scope.code(), scope.id(), session).orElseThrow();
            assertFalse(context.instructions().contains("Remain silent. Do not generate replies"));
            var saved = memory(scope);
            assertFalse(saved.containsKey("live.speechApproval"));
            assertFalse(saved.containsKey("live.committedSpeech"));
            turn(scope, "KEEP", "null", "What can you do?");
            assertFalse(memory(scope).containsKey("core.task.response"), "An ordinary reply cannot create a competing narration");
            var events = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
            assertTrue(events.stream().filter(e -> e.speechProvenance() != null).allMatch(e ->
                    ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(e.getPayload()).getSpeech() == null));
        } finally { ownership.release(scope.id(), session); }
    }
    @Test void savedLiveSpeechFlagsDoNotOwnCanonicalOutputAfterSessionRelease() {
        var scope = create();
        var owner = new TransactionTemplate(transactions).execute(status -> {
            var agent = agents.findById(scope.id()).orElseThrow();
            // Reproduce an agent saved by the November committed-output implementation.
            var storage = ((CoordinationPolicy) agent.getCurrentState().ownPolicy()).storage();
            storage.put("live.speechApproval", new JsonPrimitive(true));
            storage.put("live.committedSpeech", new JsonPrimitive(true));
            var external = new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
            agents.saveAndFlush(agent); return external;
        });
        ownership.acquire(scope.id(), owner);
        ownership.release(scope.id(), owner.sessionId());
        // Both November output-selection flags survive reload. Neither is a live ownership grant.
        var saved = memory(scope);
        assertTrue(saved.get("live.speechApproval").getAsBoolean());
        assertTrue(saved.get("live.committedSpeech").getAsBoolean());
        assertFalse(ownership.hasOwner(scope.id()));
        clearInvocations(gateway);
        turn(scope, "PROPOSE", AGREEMENT, "Propose our invitation task");
        turn(scope, "START", "null", "Start the saved proposal now");
        var prompts = org.mockito.ArgumentCaptor.forClass(ch.zhaw.prometheus.spi.InferenceRequest.class);
        verify(gateway, times(2)).infer(prompts.capture());
        assertTrue(prompts.getAllValues().stream().flatMap(request -> request.messages().stream())
                .noneMatch(message -> message.getContent().contains("Remain silent. Do not generate replies")),
                "Live output muting must never instruct canonical backend generation to remain silent");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var event = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast();
        assertNull(event.speechProvenance());
        String words = ch.zhaw.prometheus.model.behaviour.BehaviourPlan.fromJson(event.getPayload()).getSpeech();
        assertEquals("Shall we start now?", words);
        when(speech.synthesize(words, "cedar", 1.2, ch.zhaw.prometheus.spi.SpeechAudioFormat.PCM))
                .thenReturn(new ch.zhaw.prometheus.spi.SpeechAudio(new byte[] {0, 0}, "audio/pcm"));
        assertTrue(canonicalSpeech.synthesize(scope.code(), scope.id(), event.getId(),
                new SpeechSynthesisSettings("cedar", "1.2", "pcm")).isPresent());
        verify(speech).synthesize(words, "cedar", 1.2, ch.zhaw.prometheus.spi.SpeechAudioFormat.PCM);
    }
    @Test void deniedCombinedStartRemainsUnacceptedAfterReload() {
        var scope = create();
        turn(scope, "PROPOSE_AND_START", AGREEMENT, "Propose this invitation task, but do not start it");
        var reloaded = memory(scope);
        assertEquals("CONVERSATION", CoordinationMemory.stage(reloaded));
        assertFalse(reloaded.containsKey(CoordinationMemory.AGREEMENT));
        assertFalse(reloaded.containsKey(CoordinationMemory.RUN));
        assertFalse(reloaded.containsKey(TaskMemory.ACTIONS));
    }
    @Test void supportReloadsWithoutChangingAuthorityAndExistingPauseResumeRemainAvailable() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose our invitation activity");
        var before = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "I don't understand"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
        var history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        assertTrue(history.getLast().getPayload().contains("no further demonstration"));
        turn(scope, "START", "null", "Accept and begin the saved invitation");
        var count = memory(scope).get(TaskMemory.ACTIONS).deepCopy();
        turn(scope, "PAUSE", "null", "Pause");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Help me"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("PAUSED", CoordinationMemory.stage(memory(scope)));
        assertEquals(count, memory(scope).get(TaskMemory.ACTIONS));
        turn(scope, "RESUME", "null", "Resume"); assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(count, memory(scope).get(TaskMemory.ACTIONS));
    }

    @Test void preparedSensorProposalAcceptsContextualGermanReplyAfterReloadWithoutInference() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.getAsJsonArray("checks").add(JsonParser.parseString("""
                {"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock","minConfidence":0}
                """));
        var reactions = JsonParser.parseString("""
                {"goal":"Draft an invitation","maxActions":1,"sessionBound":true,"firstAction":"ON_CUE",
                "rules":[{"eventType":"obs.hand.sign","field":"sign","operator":"eq","value":"rock",
                "action":"Offer one invitation idea","effect":"ACT","minConfidence":0,"samples":1,"cooldownSeconds":3}]}
                """).getAsJsonObject(); proposal.add("reactions", reactions);
        turn(scope, "PROPOSE", proposal.toString(), "Prepare recognition before the agreed cue task");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"rock\",\"confidence\":0.75,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        clearInvocations(gateway);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Bereit"), OutputProfile.FULL_PLAN).orElseThrow();
        verifyNoInteractions(gateway);
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(1, memory(scope).get(CoordinationMemory.ACCEPTED_EVIDENCE).getAsJsonObject().size());
        turn(scope, "REPLACE", AGREEMENT, "Explicitly replace this sensor task with the invitation dialogue");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        turn(scope, "START", "null", "Accept this replacement and begin");
        assertFalse(memory(scope).containsKey(TaskMemory.SPEC));
        assertTrue(memory(scope).get(CoordinationMemory.AGREEMENT).getAsJsonObject().get("reactions").isJsonNull());
    }

    @Test void boundedRiddleDialogueReloadsQuestionFeedbackAndDistinctBudgetOutcome() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.addProperty("goal", "Try one short riddle together"); proposal.addProperty("humanRole", "Answer the question");
        proposal.addProperty("agentRole", "Ask a riddle and give feedback"); proposal.addProperty("dialogueLimit", 2);
        turn(scope, "PROPOSE", proposal.toString(), "Propose a bounded question and feedback exchange");
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("Limit: 2"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"START\",\"agreement\":null,\"reply\":{\"speech\":\"What becomes shorter while it works?\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "Start"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertFalse(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("candle"));
        when(gateway.infer(any())).thenReturn("{\"operation\":\"STEP\",\"agreement\":null,\"reply\":{\"speech\":\"A candle fits that riddle.\"}}");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION, "A candle"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals("LIMIT_REACHED", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        var history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList();
        assertTrue(history.getLast().getPayload().contains("candle"));
        assertTrue(history.stream().anyMatch(e -> Event.TYPE_USER_UTTERANCE.equals(e.getType()) && "A candle".equals(e.getPayload())));
    }

    @Test void reviewedSocialCountAlternativeReloadsAndIgnoresUnrelatedObservations() throws Exception {
        var scope = create(); JsonObject proposal;
        try (var input = getClass().getResourceAsStream("/coordination/social-count-alternative.json")) {
            proposal = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        turn(scope, "PROPOSE", proposal.toString(), "Propose this explicit visible-count alternative for review");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        turn(scope, "START", "null", "Start"); assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var rule = memory(scope).get(TaskMemory.SPEC).getAsJsonObject().getAsJsonArray("rules").get(0).getAsJsonObject();
        assertEquals("humanCount", rule.get("field").getAsString()); assertEquals(1, rule.get("value").getAsInt());
        when(gateway.infer(any())).thenReturn("{\"speech\":\"We can continue another time.\"}");
        for (var observation : List.of(Event.TYPE_SOCIAL_CONTEXT, Event.TYPE_HUMAN_PRESENCE))
            demo.acknowledge(scope.code(), scope.id(), new EventRequest(observation, "camera", Event.KIND_OBSERVATION,
                    "{\"humanCount\":1,\"avgDetectionConfidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals(0, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        long waitMillis = java.time.Duration.between(Instant.now(), Instant.parse(memory(scope).get(TaskMemory.AFTER).getAsString())).toMillis();
        assertTrue(waitMillis < 5000, "Unexpected arming window in the bounded fixture");
        if (waitMillis >= 0) Thread.sleep(waitMillis + 25);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HUMAN_PRESENCE, "camera", Event.KIND_OBSERVATION,
                "{\"humanCount\":2,\"avgDetectionConfidence\":0.9,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
    }

    @Test void currentPerceptionQuestionAndTechnicalFailurePreserveSavedTaskAcrossReload() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose the invitation task");
        var before = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"scissor\",\"confidence\":0.868,\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN).orElseThrow();
        clearInvocations(gateway);
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                "Siehst du mein Handzeichen?"), OutputProfile.FULL_PLAN).orElseThrow();
        verifyNoInteractions(gateway);
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload().contains("scissors"));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
        when(gateway.infer(any())).thenThrow(new IllegalStateException("private provider transport details"));
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user", Event.KIND_OBSERVATION,
                "Explain your capabilities"), OutputProfile.FULL_PLAN).orElseThrow();
        var failure = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().getLast().getPayload();
        assertFalse(failure.contains("private")); assertTrue(failure.contains("cause is not established"));
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertEquals(before, memory(scope).get(CoordinationMemory.DRAFT));
    }

    @Test void rejectedUpdateEvidenceReloadsWithoutChangingPreparationAndClearsOnProgress() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose our invitation activity");
        var draft = memory(scope).get(CoordinationMemory.DRAFT).deepCopy();
        when(gateway.infer(any())).thenReturn("private invalid inference");
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_USER_UTTERANCE, "user",
                Event.KIND_OBSERVATION, "Begin the saved activity"), OutputProfile.FULL_PLAN).orElseThrow();
        var restored = memory(scope);
        var failure = ch.zhaw.prometheus.model.coordination.CoordinationRecovery.current(restored, Instant.now());
        assertNotNull(failure); assertEquals("update_shape", failure.get("category").getAsString());
        assertTrue(failure.has("sourcePosition")); assertFalse(failure.get("mutationCommitted").getAsBoolean());
        assertFalse(failure.toString().contains("private")); assertEquals(draft, restored.get(CoordinationMemory.DRAFT));
        assertEquals("READY", CoordinationMemory.stage(restored));
        turn(scope, "START", "null", "Accept the current proposal and begin");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertFalse(memory(scope).containsKey(ch.zhaw.prometheus.model.coordination.CoordinationRecovery.KEY));
    }
    @Test void rawAndCalibratedObservationsRetainTheirSourceAgesAfterReload() throws Exception {
        JsonObject fixture;
        try (var input = getClass().getResourceAsStream("/coordination/observation-timing.json")) {
            fixture = JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var rawScope = create(); var calibratedScope = create();
        Instant captured = Instant.now().minusMillis(100);
        var original = fixture.getAsJsonObject("observation").deepCopy();
        original.addProperty("ts", captured.plusMillis(fixture.get("browserClockLeadMs").getAsLong()).toString());
        var calibrated = original.deepCopy(); calibrated.addProperty("ts", captured.toString());
        for (var trial : Map.of(rawScope, original, calibratedScope, calibrated).entrySet())
            demo.acknowledge(trial.getKey().code(), trial.getKey().id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera",
                    Event.KIND_OBSERVATION, trial.getValue().toString()), OutputProfile.FULL_PLAN).orElseThrow();
        // Repository methods open separate transactions: these are reloaded events, not managed originals.
        var raw = agents.findById(rawScope.id()).orElseThrow().getEventHistory().toList().stream()
                .filter(e -> Event.TYPE_HAND_SIGN.equals(e.getType())).findFirst().orElseThrow();
        var reloaded = agents.findById(calibratedScope.id()).orElseThrow();
        var corrected = reloaded.getEventHistory().toList().stream()
                .filter(e -> Event.TYPE_HAND_SIGN.equals(e.getType())).findFirst().orElseThrow();
        assertEquals(original.toString(), raw.getPayload());
        assertEquals(calibrated.toString(), corrected.getPayload());
        assertNotNull(corrected.getId());
        assertFalse(TaskMemory.fresh(raw, captured.plusMillis(26)));
        assertTrue(TaskMemory.fresh(corrected, captured.plusMillis(26)));
        for (int ageSeconds : List.of(0, 15)) {
            var projection = new ch.zhaw.prometheus.application.live.LiveContextProjection(
                    new ch.zhaw.prometheus.model.policy.PromptMessageAssembler(),
                    new ch.zhaw.prometheus.application.live.LiveVoicePolicyAdapter(),
                    java.time.Clock.fixed(captured.plusSeconds(ageSeconds), java.time.ZoneOffset.UTC));
            var item = projection.project(reloaded).items().stream().filter(i -> Event.TYPE_HAND_SIGN.equals(i.type())).findFirst().orElseThrow();
            assertEquals(captured, item.observedAt());
            assertEquals(captured.plusSeconds(15), item.expiresAt());
            assertEquals(ageSeconds == 0 ? "fresh" : "expired", item.freshness());
            assertEquals(List.of(corrected.getId()), item.sourceIds());
            assertEquals(ageSeconds == 0, TaskMemory.fresh(corrected, captured.plusSeconds(ageSeconds)));
        }
    }
    @Test void scopedDialogueAgreementReloadsPausesOnLiveCloseAndResetsWithoutLosingPersona() {
        assertTrue(registry.findByKey(CoordinationAgent.KEY).isPresent());
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Please propose an invitation task");
        assertEquals("READY", CoordinationMemory.stage(memory(scope)));
        assertTrue(TaskCueStatus.of(agents.findById(scope.id()).orElseThrow(), Instant.now()).draft());
        turn(scope, "START", "null", "I accept this proposal. Start now");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertFalse(memory(scope).containsKey(TaskMemory.SPEC));
        turn(scope, "STEP", "null", "The audience is students; the visit is Friday at two");
        assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        var session = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var agent = agents.findById(scope.id()).orElseThrow();
            ((TaskPolicy) agent.getCurrentState().ownPolicy()).storage().put(TaskMemory.SESSION, new JsonPrimitive(session.toString()));
            agent.executionEpoch(); agents.saveAndFlush(agent);
        });
        assertEquals(session.toString(), memory(scope).get(TaskMemory.SESSION).getAsString());
        lifecycle.closed(scope.id(), agents.findById(scope.id()).orElseThrow().executionEpoch(), session);
        assertEquals("PAUSED", CoordinationMemory.stage(memory(scope))); assertEquals(2, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        turn(scope, "RESUME", "null", "Resume the task");
        turn(scope, "COMPLETE", "null", "The invitation meets my requirements; finish the task");
        turn(scope, "REFLECT", "null", "Clarifying the roles was useful");
        assertEquals("REFLECTION", CoordinationMemory.stage(memory(scope)));
        assertTrue(memory(scope).get(CoordinationMemory.REVIEW).toString().contains("Clarifying the roles"));
        demo.reset(scope.code(), scope.id());
        assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope))); assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
        assertTrue(agents.findById(scope.id()).orElseThrow().getCurrentState().getTotalPolicy().contains("Laureline"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(doubles = {0, .8})
    void preparationEvidencePersistsAndARevisedDraftRequiresFreshEvidence(double minimum) {
        var scope = create();
        var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.getAsJsonArray("checks").add(JsonParser.parseString("""
                {"eventType":"obs.hand.sign", "field":"sign", "operator":"eq", "value":"rock", "minConfidence":0.8}
                """));
        proposal.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("minConfidence", minimum);
        turn(scope, "PROPOSE", proposal.toString(), "First check that you can recognise rock, then help with the invitation");
        turn(scope, "START", "null", "I have shown it; start now"); assertFalse(memory(scope).containsKey(CoordinationMemory.AGREEMENT));
        demo.acknowledge(scope.code(), scope.id(), new EventRequest(Event.TYPE_HAND_SIGN, "camera", Event.KIND_OBSERVATION,
                "{\"sign\":\"rock\",\"confidence\":" + (minimum == 0 ? .757 : .9) + ",\"ts\":\"" + Instant.now() + "\"}"), OutputProfile.FULL_PLAN);
        assertEquals("READY", CoordinationMemory.stage(memory(scope))); assertEquals(1, CoordinationMemory.evidence(memory(scope)).size());
        proposal.addProperty("agentRole", "Clarify references and edit the supplied details");
        turn(scope, "PROPOSE", proposal.toString(), "Revise the proposal to clarify references");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
        assertEquals("PREPARATION", CoordinationMemory.stage(memory(scope))); assertEquals(0, CoordinationMemory.evidence(memory(scope)).size());
    }
    @Test void returnAndRestartReloadWithHistoryAndEarlierOutcomesPreserved() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose an invitation task");
        turn(scope, "START", "null", "Start now");
        String run = memory(scope).get(CoordinationMemory.RUN).getAsString();
        turn(scope, "COMPLETE", "null", "The invitation is accepted; complete the task");
        int history = agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size();
        turn(scope, "RETURN_TO_CONVERSATION", "null", "Go back to the beginning state");
        assertEquals("CONVERSATION", CoordinationMemory.stage(memory(scope)));
        assertEquals("USER_REPORTED_COMPLETION", memory(scope).get(CoordinationMemory.OUTCOME).getAsString());
        assertTrue(agents.findById(scope.id()).orElseThrow().getEventHistory().toList().size() > history);
        turn(scope, "RESTART", "null", "Restart the same task");
        assertEquals("EXECUTION", CoordinationMemory.stage(memory(scope)));
        assertNotEquals(run, memory(scope).get(CoordinationMemory.RUN).getAsString());
        assertEquals(1, memory(scope).get(TaskMemory.ACTIONS).getAsInt());
        assertEquals(1, memory(scope).get(CoordinationMemory.OUTCOMES).getAsJsonArray().size());
    }
    @Test void validLargeAgreementAndContentRevisionSurviveReload() {
        var scope = create(); var proposal = JsonParser.parseString(AGREEMENT).getAsJsonObject();
        proposal.addProperty("goal", "g".repeat(300)); proposal.addProperty("humanRole", "h".repeat(300));
        proposal.addProperty("agentRole", "a".repeat(300)); proposal.addProperty("success", "s".repeat(300));
        for (int i = 0; i < 4; i++) proposal.getAsJsonArray("questions").add("q".repeat(160));
        for (String sign : List.of("rock", "paper", "scissor")) proposal.getAsJsonArray("checks").add(JsonParser.parseString(
                "{\"eventType\":\"obs.hand.sign\",\"field\":\"sign\",\"operator\":\"eq\",\"value\":\"" + sign + "\",\"minConfidence\":0.8}"));
        assertTrue(proposal.toString().length() > 2048);
        turn(scope, "PROPOSE", proposal.toString(), "Propose these detailed terms");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
        proposal.addProperty("agentRole", "Keep the same terms and use a concise response");
        turn(scope, "PROPOSE", proposal.toString(), "Make the response concise");
        assertEquals(proposal, memory(scope).get(CoordinationMemory.DRAFT));
    }
    @Test void twoReceiptsCommitOneRevisionAndDuplicatesCannotRepeatIt() {
        var scope = create(); turn(scope, "PROPOSE", AGREEMENT, "Propose an invitation task");
        var agent = agents.findById(scope.id()).orElseThrow();
        var owner = new ch.zhaw.prometheus.model.policy.ExternalSpeech(UUID.randomUUID(), agent.executionEpoch());
        agents.saveAndFlush(agent); ownership.acquire(scope.id(), owner);
        var changed = JsonParser.parseString(AGREEMENT).getAsJsonObject(); changed.addProperty("agentRole", "Use a dry one-liner");
        when(gateway.infer(any())).thenReturn("{\"operation\":\"PROPOSE\",\"agreement\":" + changed + ",\"reply\":{\"speech\":\"Propose concise wording\"}}");
        clearInvocations(gateway);
        long now = Instant.now().toEpochMilli();
        var first = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("dry", ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Speaker.USER, 0L, 200L, now, 1, "A dry");
        var last = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Fragment("line", first.speaker(), 1400L, 1800L, now + 1400, 2, " one-liner");
        assertTrue(ingress.receipt(scope.id(), owner, first)); assertTrue(ingress.receipt(scope.id(), owner, last));
        assertFalse(ingress.receipt(scope.id(), owner, first));
        var segment = new ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Segment(UUID.randomUUID(), first.speaker(), List.of(first, last),
                ch.zhaw.prometheus.application.live.LiveTranscriptSegmenter.Closure.COMPLETE, "observed_silence", now, now + 1400);
        var committed = ingress.commit(scope.id(), owner, segment, List.of("Coordination awaiting acceptance")).orElseThrow();
        assertEquals(List.of("dry", "line"), committed.receiptIds()); assertEquals("COMPLETE", committed.status());
        assertEquals(changed, memory(scope).get(CoordinationMemory.DRAFT));
        assertEquals(committed.eventId(), ingress.commit(scope.id(), owner, segment, List.of()).orElseThrow().eventId());
        verify(gateway, times(1)).infer(any()); ownership.release(scope.id(), owner.sessionId());
    }
}
