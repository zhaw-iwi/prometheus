package ch.zhaw.prometheus.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import ch.zhaw.prometheus.model.commons.actions.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.model.snapshot.ObservationSnapshot;
import ch.zhaw.prometheus.spi.*;

class BackgroundActionPreparationUnitTest {
    @Test void defaultsAndPersistedLegacyDependenciesAreExplicit() throws Exception {
        Storage storage = new Storage();
        var outcome = new StaticExtractionAction("extract", storage, "outcome");
        var choice = new StaticExtractionAction("extract", storage, "choice");
        assertEquals(Action.ExecutionMode.BACKGROUND, choice.getExecutionMode());
        assertEquals(Action.ExecutionMode.BLOCKING, choice.blocking().getExecutionMode());
        var mode = Action.class.getDeclaredField("executionMode"); mode.setAccessible(true);
        mode.set(outcome, null); mode.set(choice, null);
        assertEquals(Action.ExecutionMode.BACKGROUND, outcome.getExecutionMode());
        assertEquals(Action.ExecutionMode.BLOCKING, choice.getExecutionMode());
        var summary = new StaticSummarisationAction("summarize", storage, "arbitrary-key"); mode.set(summary, null);
        assertEquals(Action.ExecutionMode.BACKGROUND, summary.getExecutionMode());
        assertEquals(Action.ExecutionMode.BLOCKING, new ch.zhaw.prometheus.model.rps.RpsSelectAgentSignAction(storage).getExecutionMode());
        assertEquals(Action.ExecutionMode.BLOCKING, new ch.zhaw.prometheus.model.rps.RpsEvaluateRoundAction(storage).getExecutionMode());
    }

    @Test void preparedExtractionFreezesResolvedStoragePromptsAndSelectedEvents() {
        Storage storage = new Storage(); storage.put("options", JsonParser.parseString("[\"old-option\"]"));
        var action = new DynamicExtractionAction("Select from ${options}", storage, "options", "choice");
        var history = new EventHistory(); history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "old-event"));
        var gateway = mock(LanguageModelGateway.class);
        when(gateway.infer(any())).thenAnswer(invocation -> {
            InferenceRequest request = invocation.getArgument(0);
            String messages = request.messages().toString();
            assertEquals(InferencePurpose.EXTRACTION, request.purpose());
            assertTrue(messages.contains("old-option")); assertTrue(messages.contains("old-event"));
            assertFalse(messages.contains("new-option")); assertFalse(messages.contains("new-event"));
            return "\"selected\"";
        });
        var prepared = action.prepare(history, ObservationSnapshot.empty(), new PolicyRuntime(new PromptMessageAssembler(), gateway));
        storage.put("options", JsonParser.parseString("[\"new-option\"]"));
        history.reset(); history.appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "new-event"));
        action.setPolicy(new PromptPolicy("replacement", null, null));
        assertEquals(Map.of("choice", "\"selected\""), prepared.work().compute(gateway));
        assertFalse(storage.containsKey("choice"));
    }

    @Test void transitionDispatchesBackgroundWorkButCompletesExplicitDependenciesInline() {
        var storage = new Storage(); var gateway = mock(LanguageModelGateway.class);
        when(gateway.extract(any())).thenReturn(new JsonPrimitive("chosen"));
        var queued = new ArrayList<PreparedAction>();
        PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
                .withActionExecution((action, events, snapshot, rt) -> queued.add(action.prepare(events, snapshot, rt)));
        var state = new State("state", new NoOpPolicy(), List.of()); state.setEventHistory(new EventHistory());
        state.getSharedEventHistory().appendEvent(Event.observation(Event.TYPE_USER_UTTERANCE, "user", "finish").withStatePath("state"));
        var transition = new Transition(List.of(), List.of(
                new StaticExtractionAction("summary", storage, "outcome"),
                new StaticExtractionAction("choice", storage, "choice").blocking()), new Final("done"));
        transition.action(state, runtime);
        assertEquals(1, queued.size()); assertFalse(storage.containsKey("outcome"));
        assertEquals("chosen", storage.get("choice").getAsString());
        verify(gateway, times(1)).extract(any()); verify(gateway, never()).infer(any());
    }

    @Test void deterministicPreparedRemovalHasNoWorkerEntityMutation() {
        Storage storage = new Storage(); storage.put("topics", JsonParser.parseString("[\"one\",\"two\"]"));
        storage.put("choice", new JsonPrimitive("one"));
        var action = new DynamicRemoveTopicAction("", storage, "topics", "choice");
        var prepared = action.prepare(new EventHistory(), ObservationSnapshot.empty(), TestPolicyRuntime.runtime());
        storage.put("choice", new JsonPrimitive("two"));
        assertEquals(Map.of("topics", "[\"two\"]"), prepared.work().compute(new NoOpLanguageModelGateway()));
        assertEquals(2, storage.get("topics").getAsJsonArray().size());
    }
}
