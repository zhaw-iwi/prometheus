package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.event.EventHistory;
import ch.zhaw.prometheus.model.policy.NoOpPolicy;
import ch.zhaw.prometheus.model.policy.Policy;

@SpringBootTest
class ConversationalStateMachineSmokeTest {

    @Test
    void conversationalStateMachineUsesPoliciesAndSharedEvents() {
        Policy statePolicy = new FixedPolicy("Hello from start", "Response from state");
        State finalState = new TestFinalState("final", new FixedPolicy("Final response", "Final response"));

        Policy decisionPolicy = new AssertingDecisionPolicy("start");
        Decision decision = new TestDecision(decisionPolicy);
        Action action = new RecordingAction(new NoOpPolicy());

        Transition transition = new Transition(List.of(decision), List.of(action), finalState);
        State startState = new State("start", statePolicy, List.of(transition));
        Agent agent = new Agent("Test Agent", "Conversation smoke test", startState);
        var runtime = TestPolicyRuntime.runtime();

        Event userEvent = Event.observation(Event.TYPE_USER_UTTERANCE, Event.ACTOR_USER, "Hi there");
        Event response = agent.acknowledge(userEvent, runtime);

        assertNotNull(response);
        assertTrue(response.getPayload().contains("\"speech\":\"Final response\""));
        assertFalse(agent.isActive());

        List<Event> sharedEvents = agent.getEventHistory().toList();
        assertEquals(2, sharedEvents.size());
        assertEquals(Event.TYPE_USER_UTTERANCE, sharedEvents.get(0).getType());
        assertEquals(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, sharedEvents.get(1).getType());

        assertTrue(((RecordingAction) action).wasExecuted());
    }

    private static class FixedPolicy extends Policy {
        private final String startResponse;
        private final String response;

        FixedPolicy(String startResponse, String response) {
            this.startResponse = startResponse;
            this.response = response;
        }

        @Override
        public BehaviourPlan onStart(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return startResponse == null ? null : BehaviourPlan.speechOnly(startResponse);
        }

        @Override
        public BehaviourPlan onRespond(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return response == null ? null : BehaviourPlan.speechOnly(response);
        }

        @Override
        public String summarise(State state, EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler, ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return "";
        }

        @Override
        public String describe() {
            return "fixed-policy";
        }

        @Override
        public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
                ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return true;
        }

        @Override
        public JsonElement extract(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
                ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            return new JsonPrimitive("value");
        }
    }

    private static class AssertingDecisionPolicy extends FixedPolicy {
        private final String expectedStateName;

        AssertingDecisionPolicy(String expectedStateName) {
            super(null, null);
            this.expectedStateName = expectedStateName;
        }

        @Override
        public boolean decide(EventHistory events, ch.zhaw.prometheus.model.policy.PromptMessageAssembler assembler,
                ch.zhaw.prometheus.spi.LanguageModelGateway languageModelGateway) {
            assertTrue(events.toList().stream()
                    .allMatch(event -> !event.getStatePath().isEmpty()
                            && expectedStateName.equals(event.getStatePath().get(event.getStatePath().size() - 1))));
            return true;
        }
    }

    private static class TestDecision extends Decision {
        TestDecision(Policy policy) {
            super(policy);
        }
    }

    private static class RecordingAction extends Action {
        private boolean executed;

        RecordingAction(Policy policy) {
            super(policy);
            this.executed = false;
        }

        @Override
        public void execute(EventHistory eventHistory, ch.zhaw.prometheus.model.policy.PolicyRuntime runtime) {
            this.executed = true;
        }

        boolean wasExecuted() {
            return executed;
        }
    }

    private static class TestFinalState extends State {
        TestFinalState(String name, Policy policy) {
            super(name, policy, List.of());
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}

