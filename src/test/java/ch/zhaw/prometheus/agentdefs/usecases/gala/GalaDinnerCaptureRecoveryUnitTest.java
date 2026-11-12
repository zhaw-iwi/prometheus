package ch.zhaw.prometheus.agentdefs.usecases.gala;

import static ch.zhaw.prometheus.agentdefs.usecases.gala.GalaDinnerMemory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.zhaw.prometheus.agentdefs.usecases.GalaDinnerReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGames;
import ch.zhaw.prometheus.agentdefs.usecases.SwissRoboticsDayReceptionMultiStateGamesEnglish;
import ch.zhaw.prometheus.model.Agent;
import ch.zhaw.prometheus.model.Storage;
import ch.zhaw.prometheus.model.behaviour.BehaviourPlan;
import ch.zhaw.prometheus.model.event.Event;
import ch.zhaw.prometheus.model.policy.ExternalSpeech;
import ch.zhaw.prometheus.model.policy.PolicyRuntime;
import ch.zhaw.prometheus.model.policy.PromptMessageAssembler;
import ch.zhaw.prometheus.spi.LanguageModelGateway;

class GalaDinnerCaptureRecoveryUnitTest {
    @ParameterizedTest @ValueSource(strings = {"gala", "swiss-de", "swiss-en"})
    void startingDuringDepartureGracePublishesTheCaptureNeededToCompletePreparation(String variant) {
        var f = new Fixture(variant);
        f.group(0);
        var opening = display(f.say("Schere Stein Papier bitte"));
        String capture = opening.get("captureId").getAsString();
        assertEquals("RPS_SETUP", mode(f.storage));
        assertTrue(opening.get("awaitingUserSign").getAsBoolean());
        assertEquals(capture, display(f.group(1)).get("captureId").getAsString());
        for (String sign : List.of("rock", "scissor", "paper")) assertNotNull(f.hand(sign, capture));
        assertEquals("RPS", mode(f.storage));
        assertEquals("INTRO", text(f.storage, GalaDinnerRps.PHASE, ""));
        assertTrue(f.storage.get(RPS_PREPARED).getAsBoolean());
        verify(f.gateway, times(1)).infer(any());
    }

    @Test void recoveredPreparationRepublishesTheSameCaptureAndProgressOnceWithoutRepeatingSpeech() {
        var f = new Fixture("gala");
        String capture = display(f.say("Schere Stein Papier bitte")).get("captureId").getAsString();
        f.hand("rock", capture);
        String started = text(f.storage, STARTED, ""), occasion = text(f.storage, CONTINUATION_ID, "");
        f.group(0);
        var recovered = plan(f.group(1));
        var display = recovered.getDisplay().getAsJsonObject();
        assertEquals(capture, display.get("captureId").getAsString());
        assertEquals("[\"rock\"]", display.get("recognisedSigns").toString());
        assertEquals("[\"paper\",\"scissor\"]", display.get("remainingSigns").toString());
        assertEquals(started, text(f.storage, STARTED, ""));
        assertEquals(occasion, text(f.storage, CONTINUATION_ID, ""));
        assertNull(recovered.getSpeech()); assertNull(recovered.getMotion());
        assertNull(f.group(1), "Ordinary presence refresh must not republish capture");
        assertNotNull(f.hand("paper", capture)); assertNotNull(f.hand("scissor", capture));
        assertEquals("RPS", mode(f.storage));
        verify(f.gateway, times(1)).infer(any());
    }

    @Test void recoveredPlayingCaptureKeepsTheRoundAndCannotRepeatTheRobotHandMotion() {
        var f = new Fixture("gala"); f.prepare();
        assertNotNull(GalaRpsTestFixture.countdownPlayed(f.agent, f.runtime));
        String capture = text(f.storage, CAPTURE_ID, ""), started = text(f.storage, STARTED, "");
        String sign = text(f.storage, SIGN, ""), round = text(f.storage, GalaDinnerRounds.ID, "");
        f.group(0);
        var recovered = plan(f.group(1));
        assertEquals(capture, recovered.getDisplay().getAsJsonObject().get("captureId").getAsString());
        assertEquals(started, text(f.storage, STARTED, ""));
        assertEquals(sign, text(f.storage, SIGN, ""));
        assertEquals(round, text(f.storage, GalaDinnerRounds.ID, ""));
        assertEquals("WAITING", text(f.storage, GalaDinnerRps.PHASE, ""));
        assertNull(recovered.getMotion()); assertNull(recovered.getSpeech());
        assertNotNull(f.hand("paper", capture));
        assertEquals("RECEPTION", mode(f.storage));
    }

    @ParameterizedTest @ValueSource(strings = {"paused", "countdown", "closed"})
    void presenceRecoveryCannotReopenPausedUnreleasedOrEndedCaptures(String state) {
        var f = new Fixture("gala");
        if (state.equals("countdown")) f.prepare();
        else f.say("Schere Stein Papier bitte");
        if (state.equals("paused")) {
            when(f.gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("CHECK_EXIT", ""));
            f.say("Was ist das IWI?"); assertTrue(f.storage.containsKey(EXIT_REQUEST));
        }
        f.group(0);
        if (state.equals("closed")) f.agent.acknowledge(
                new Event(GalaDinnerPolicy.LIVE_ENDED, "system", Event.KIND_SYSTEM, "closed"), f.runtime);
        assertNull(f.group(1));
        if (!state.equals("paused")) assertFalse(f.storage.containsKey(CAPTURE_ID));
    }

    @Test void stalePresenceCannotRepublishCaptureOrResolveAbsence() {
        var f = new Fixture("gala"); f.say("Schere Stein Papier bitte"); f.group(0);
        var stale = time(f.storage, SEEN).minusMillis(1);
        assertNull(f.group(1, stale)); assertTrue(f.storage.containsKey(ABSENT_SINCE));
        assertNotNull(f.group(1)); assertFalse(f.storage.containsKey(ABSENT_SINCE));
    }

    @Test void aGameRequestAfterDepartureGraceCannotCreateAnUnannouncedCapture() {
        var f = new Fixture("gala"); f.group(0);
        put(f.storage, ABSENT_SINCE, Instant.now().minusMillis(DEPARTURE_GRACE_MS + 1).toString());
        assertNull(f.say("Schere Stein Papier bitte"));
        assertEquals("RECEPTION", mode(f.storage));
        assertFalse(f.storage.containsKey(CAPTURE_ID));
        verifyNoInteractions(f.gateway);
    }

    private static BehaviourPlan plan(Event event) {
        assertNotNull(event);
        return BehaviourPlan.fromJson(event.getPayload());
    }
    private static JsonObject display(Event event) { return plan(event).getDisplay().getAsJsonObject(); }

    private static final class Fixture {
        final LanguageModelGateway gateway = mock(LanguageModelGateway.class);
        final PolicyRuntime runtime = new PolicyRuntime(new PromptMessageAssembler(), gateway)
                .withExternalSpeech(new ExternalSpeech(UUID.randomUUID(), UUID.randomUUID()));
        final Agent agent;
        final Storage storage;
        Fixture(String variant) {
            agent = switch (variant) {
                case "swiss-de" -> new SwissRoboticsDayReceptionMultiStateGames().createAgent();
                case "swiss-en" -> new SwissRoboticsDayReceptionMultiStateGamesEnglish().createAgent();
                default -> new GalaDinnerReceptionMultiStateGames().createAgent();
            };
            storage = ((GalaDinnerPolicy) agent.getCurrentState().ownPolicy()).storage();
            put(storage, COUNT, "1"); put(storage, SEEN, Instant.now().minusSeconds(1).toString());
            when(gateway.infer(any())).thenReturn(GalaDinnerReceptionUnitTest.answer("START_RPS", ""));
        }
        Event say(String speech) { return agent.acknowledge(Event.observation(Event.TYPE_USER_UTTERANCE, "user", speech), runtime); }
        Event group(int count) { return group(count, Instant.now()); }
        Event group(int count, Instant at) {
            return agent.acknowledge(Event.observation(Event.TYPE_SOCIAL_GROUPING, "sensor",
                    "{\"humanCount\":" + count + ",\"ts\":\"" + at + "\"}"), runtime);
        }
        Event hand(String sign, String capture) {
            var data = new JsonObject(); data.addProperty("sign", sign); data.addProperty("confidence", .64);
            data.addProperty("source", "valerian.hand.camera"); data.addProperty("stabilityFrames", 3);
            data.addProperty("captureId", capture); data.addProperty("ts", Instant.now().toString());
            return agent.acknowledge(Event.observation(Event.TYPE_HAND_SIGN, "sensor", data.toString()), runtime);
        }
        void prepare() {
            String capture = display(say("Schere Stein Papier bitte")).get("captureId").getAsString();
            for (String sign : List.of("rock", "paper", "scissor")) hand(sign, capture);
        }
    }
}
