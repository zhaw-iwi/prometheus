package ch.zhaw.prometheus.performance;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import ch.zhaw.prometheus.spi.*;
import ch.zhaw.prometheus.model.*;
import ch.zhaw.prometheus.model.event.*;
import ch.zhaw.prometheus.model.policy.*;
import ch.zhaw.prometheus.agentdefs.*;
import ch.zhaw.prometheus.agentdefs.core.*;
import ch.zhaw.prometheus.agentdefs.usecases.healthcare.*;

class CatalogInferenceCountUnitTest {
  static class Gateway implements LanguageModelGateway {
    List<String> calls = new ArrayList<>();
    Deque<Boolean> decisions = new ArrayDeque<>();
    boolean invalidNonverbal;
    public GuardInferenceOptions guardInferenceOptions() { return new GuardInferenceOptions(GuardInferenceOptions.Strategy.COMBINED, 16, 65536); }
    public String complete(List<PromptMessage> m) { calls.add("S"); return "A short example response."; }
    public String infer(InferenceRequest request) {
      if (request.output() != InferenceRequest.Output.JSON_OBJECT) return LanguageModelGateway.super.infer(request);
      if (request.purpose() == InferencePurpose.DECISION) {
        calls.add("G"); JsonObject result = new JsonObject();
        for (String id : request.schema().getAsJsonObject("properties").keySet()) result.addProperty(id, !decisions.isEmpty() && decisions.removeFirst());
        return result.toString();
      }
      calls.add("B");
      return invalidNonverbal ? "invalid" : "{\"speech\":\"A short example response.\",\"nonVerbal\":{\"gesture\":\"NONE\"}}";
    }
    public boolean decide(List<PromptMessage> m) { calls.add("D"); return !decisions.isEmpty() && decisions.removeFirst(); }
    public JsonElement extract(List<PromptMessage> m) { calls.add("X"); return new JsonObject(); }
    public JsonElement summarise(List<PromptMessage> m) { calls.add("SUM"); return new JsonObject(); }
    public String summariseOffline(List<PromptMessage> m) { calls.add("SUM"); return "summary"; }
    void reset(Boolean... values) { calls.clear(); decisions.clear(); decisions.addAll(Arrays.asList(values)); }
  }
  static Event input(String type) { return Event.observation(type, Event.ACTOR_USER, type.equals(Event.TYPE_USER_UTTERANCE) ? "Example user turn" : "{\"sign\":\"rock\"}"); }
  @Test void ordinaryCatalogPaths() {
    List<AgentDefinition> defs = List.of(new FacialExpressionSensitivity(), new MultimodalBehaviour(), new RockScissorPaper(), new RoleClarificationGuessingGame(), new SocialContextSensitivity(), new TalkToMe(), new SingleStateGuessingGame(), new SingleStateGuessingGameUserGuess(), new SingleStateHealthcareConversation(), new SingleStateSmartGoalCoaching(), new SingleStateTherapyAppointmentReminder(), new TwoStateTherapyAppointmentReminder());
    for (AgentDefinition def : defs) {
      Gateway g = new Gateway(); PolicyRuntime rt = new PolicyRuntime(new PromptMessageAssembler(), g);
      Agent a = def.createAgent(); a.start(rt);
      assertEquals(def instanceof TalkToMe ? 0 : 1, g.calls.size(), def.key() + " startup");
      g.reset(); Event event = a.acknowledge(input(Event.TYPE_USER_UTTERANCE), rt);
      if (event == null) a.generate(rt);
      int expected = def instanceof TalkToMe ? 0 : 2;
      assertEquals(expected, g.calls.size(), def.key());
    }
  }
  @Test void closingSensoryFallbackAndDeterministicPaths() {
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_USER_UTTERANCE, true, "GXS", true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_USER_UTTERANCE, true, "GXS", false, true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_SOCIAL_SITUATION_CHANGE, false, "DB", true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_SOCIAL_SITUATION_CHANGE, false, "D", false);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_WEATHER_CURRENT, false, "");
    check(new FacialExpressionSensitivity(), Event.TYPE_FACE_EMOTION, false, "B");
    check(new SocialContextSensitivity(), Event.TYPE_SOCIAL_CONTEXT, false, "B");
    check(new RockScissorPaper(), Event.TYPE_HAND_SIGN, false, "");
    Gateway g = new Gateway(); PolicyRuntime rt = new PolicyRuntime(new PromptMessageAssembler(), g);
    Agent a = new SingleStateSmartGoalCoaching().createAgent(); a.start(rt); g.reset(); g.invalidNonverbal = true;
    a.acknowledge(input(Event.TYPE_USER_UTTERANCE), rt);
    assertThrows(IllegalStateException.class, () -> a.generate(rt));
    assertEquals("GB", String.join("", g.calls));
  }
  static void check(AgentDefinition def, String type, boolean fallback, String expected, Boolean... decisions) {
    Gateway g = new Gateway(); PolicyRuntime rt = new PolicyRuntime(new PromptMessageAssembler(), g);
    Agent a = def.createAgent(); a.start(rt); g.reset(decisions);
    Event event = a.acknowledge(input(type), rt);
    if (event == null && fallback) a.generate(rt);
    assertEquals(expected, String.join("", g.calls), def.key() + " " + type);
  }
}
