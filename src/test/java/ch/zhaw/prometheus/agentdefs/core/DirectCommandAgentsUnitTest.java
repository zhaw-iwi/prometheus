package ch.zhaw.prometheus.agentdefs.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.behaviour.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.spi.*;

class DirectCommandAgentsUnitTest {
    static Agent create(String type) {
        return switch (type) {
            case "coordination" -> new CoordinationAgent().createAgent();
            case "generic" -> new GenericMultimodalBehaviour().createAgent();
            case "social" -> new GenericMultimodalBehaviourAtSocialSpace().createAgent(false);
            default -> new LiveMultimodal().createAgent();
        };
    }
    static String output(String type, String intent, String kind, String value, boolean silent) {
        var root = new JsonObject(); var command = new JsonObject();
        command.addProperty("intent", intent); command.addProperty("kind", kind); command.addProperty("value", value); command.addProperty("silent", silent);
        root.add("command", command);
        if (!type.equals("live")) { root.addProperty("operation", "KEEP"); root.add(type.equals("coordination") ? "agreement" : "task", JsonNull.INSTANCE); root.add("reply", new JsonObject()); }
        return root.toString();
    }
    static Event turn(Agent agent, Event input, PolicyRuntime runtime) {
        var response = agent.acknowledge(input, runtime);
        // Both browser and native ingress request generation when acknowledgement has no response.
        return response == null && runtime.taskContinuation().getAsBoolean() ? agent.generate(runtime) : response;
    }
    @ParameterizedTest @ValueSource(strings = {"coordination", "generic", "social", "live"})
    void singleInferencePublishesValidatedCommandAndRepeatBypassesCadence(String type) {
        var agent = create(type); var gateway = mock(LanguageModelGateway.class);
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway);
        when(gateway.infer(any())).thenAnswer(c -> ((InferenceRequest)c.getArgument(0)).purpose() == InferencePurpose.DECISION
                ? "false" : output(type, "ACTION", "GESTURE", "OPEN_QUESTION", false));
        var request = Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show me a question gesture");
        var first = turn(agent, request, runtime);
        assertNotNull(first); assertEquals("OPEN_QUESTION", BehaviourPlan.fromJson(first.getPayload()).getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        verify(gateway, times(1)).infer(argThat(r -> r.purpose() != InferencePurpose.DECISION));
        var repeat = turn(agent, Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show the question gesture again"), runtime);
        assertEquals("OPEN_QUESTION", BehaviourPlan.fromJson(repeat.getPayload()).getNonVerbal().getAsJsonObject().get("gesture").getAsString());
        verify(gateway, times(2)).infer(argThat(r -> r.purpose() != InferencePurpose.DECISION));
        assertFalse(agent.getStorage().containsKey("task.spec")); assertFalse(agent.getStorage().containsKey("coordination.agreement"));
    }
    @ParameterizedTest @ValueSource(strings = {"coordination", "generic", "social", "live"})
    void silentSignsStayCanonicalAndNativeOwnershipNeverCreatesSpeech(String type) {
        var agent = create(type); var gateway = mock(LanguageModelGateway.class);
        when(gateway.infer(any())).thenAnswer(c -> ((InferenceRequest)c.getArgument(0)).purpose() == InferencePurpose.DECISION
                ? "false" : output(type, "ACTION", "HAND_SIGN", "scissors", true));
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
                .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch()));
        var event = turn(agent, Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Show scissors silently"), runtime);
        var plan = BehaviourPlan.fromJson(event.getPayload()); assertNull(plan.getSpeech());
        assertEquals("scissor", plan.getMotion().getAsJsonObject().get("handSign").getAsString());
        assertFalse(event.getPayload().contains("command"));
    }
    @ParameterizedTest @ValueSource(strings = {"coordination", "generic", "social", "live"})
    void ownershipLossDuringInferenceCannotPublishACommand(String type) {
        var agent = create(type); var gateway = mock(LanguageModelGateway.class); var allowed = new AtomicBoolean(true);
        when(gateway.infer(any())).thenAnswer(c -> {
            if (((InferenceRequest)c.getArgument(0)).purpose() == InferencePurpose.DECISION) return "false";
            allowed.set(false); return output(type, "ACTION", "HAND_SIGN", "rock", false);
        });
        var runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
                .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), agent.executionEpoch())).withTaskContinuation(allowed::get);
        assertNull(turn(agent, Event.observation(Event.TYPE_USER_UTTERANCE, "user", "Make rock"), runtime));
        assertTrue(agent.getEventHistory().toList().stream().noneMatch(e -> Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN.equals(e.getType())));
    }
    @ParameterizedTest @ValueSource(strings = {"Don't make a rock sign", "What does 'make a rock sign' mean?", "If I asked for a question gesture, what would happen?"})
    void nonCommandInterpretationPreservesReadyProposalAndDoesNotProduceTheMentionedAction(String text) {
        var fixture = new CoordinationAgentUnitTest(); fixture.propose(fixture.sensor(false), fixture.text);
        var draft = fixture.storage().get("coordination.draft").deepCopy();
        var interpreted = JsonParser.parseString(fixture.update("KEEP", null, "Understood; this does not request a demonstration.")).getAsJsonObject();
        interpreted.add("command", JsonNull.INSTANCE);
        when(fixture.gateway.infer(any())).thenReturn(interpreted.toString());
        var result = fixture.say(text, fixture.text); var plan = BehaviourPlan.fromJson(result.getPayload());
        assertNull(plan.getMotion()); assertNull(plan.getNonVerbal());
        assertEquals(draft, fixture.storage().get("coordination.draft"));
        assertFalse(fixture.storage().containsKey(DirectCommands.LAST));
    }
    @Test void conditionalInterpretationStillProposesAnInactiveTaskWithoutAnImmediateDemonstration() {
        var fixture = new CoordinationAgentUnitTest();
        var interpreted = JsonParser.parseString(fixture.update("PROPOSE", fixture.sensor(false), "Shall we start?")).getAsJsonObject();
        interpreted.add("command", JsonNull.INSTANCE);
        when(fixture.gateway.infer(any())).thenReturn(interpreted.toString());
        var response = fixture.say("Whenever I show rock, offer one idea", fixture.text);
        assertNotNull(fixture.storage().get("coordination.draft"));
        assertFalse(fixture.storage().containsKey("coordination.agreement"));
        assertFalse(fixture.storage().containsKey(DirectCommands.LAST));
        assertNull(BehaviourPlan.fromJson(response.getPayload()).getMotion());
    }
}
