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
    public String complete(List<PromptMessage> m) {
      boolean nv = m.size() == 2 && m.get(1).getContent().startsWith("Assistant speech:");
      calls.add(nv ? "N" : "S");
      return nv ? (invalidNonverbal ? "invalid" : "{\"gesture\":\"NONE\"}") : "A short example response.";
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
      assertEquals(def instanceof TalkToMe ? 0 : 2, g.calls.size(), def.key() + " startup");
      g.reset(); Event event = a.acknowledge(input(Event.TYPE_USER_UTTERANCE), rt);
      if (event == null) a.generate(rt);
      int expected = def instanceof TalkToMe ? 0 : def instanceof RockScissorPaper ? 5 : def instanceof RoleClarificationGuessingGame ? 6 : 4;
      assertEquals(expected, g.calls.size(), def.key());
    }
  }
  @Test void closingSensoryFallbackAndDeterministicPaths() {
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_USER_UTTERANCE, true, "DXS", true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_USER_UTTERANCE, true, "DDXS", false, true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_SOCIAL_SITUATION_CHANGE, false, "DSN", true);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_SOCIAL_SITUATION_CHANGE, false, "D", false);
    check(new SingleStateSmartGoalCoaching(), Event.TYPE_WEATHER_CURRENT, false, "");
    check(new FacialExpressionSensitivity(), Event.TYPE_FACE_EMOTION, false, "SN");
    check(new SocialContextSensitivity(), Event.TYPE_SOCIAL_CONTEXT, false, "SN");
    check(new RockScissorPaper(), Event.TYPE_HAND_SIGN, false, "");
    Gateway g = new Gateway(); PolicyRuntime rt = new PolicyRuntime(new PromptMessageAssembler(), g);
    Agent a = new SingleStateSmartGoalCoaching().createAgent(); a.start(rt); g.reset(); g.invalidNonverbal = true;
    a.acknowledge(input(Event.TYPE_USER_UTTERANCE), rt); a.generate(rt);
    assertEquals("DDSNN", String.join("", g.calls));
  }
  static void check(AgentDefinition def, String type, boolean fallback, String expected, Boolean... decisions) {
    Gateway g = new Gateway(); PolicyRuntime rt = new PolicyRuntime(new PromptMessageAssembler(), g);
    Agent a = def.createAgent(); a.start(rt); g.reset(decisions);
    Event event = a.acknowledge(input(type), rt);
    if (event == null && fallback) a.generate(rt);
    assertEquals(expected, String.join("", g.calls), def.key() + " " + type);
  }
}
