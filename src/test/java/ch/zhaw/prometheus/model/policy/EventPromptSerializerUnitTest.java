package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;

class EventPromptSerializerUnitTest {

    @Test
    void mapsFaceEmotionWithConfidence() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"happy\",\"confidence\":0.83,\"valence\":0.7}");

        assertEquals("User facial emotion: happy (confidence 0.83)", EventPromptSerializer.toPromptContent(faceEmotion));
    }

    @Test
    void mapsFaceEmotionWithoutConfidence() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{\"emotion\":\"sad\"}");

        assertEquals("User facial emotion: sad", EventPromptSerializer.toPromptContent(faceEmotion));
    }

    @Test
    void mapsFaceEmotionInvalidPayloadConservatively() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{invalid-json");

        assertEquals("User facial emotion observed.", EventPromptSerializer.toPromptContent(faceEmotion));
    }

    @Test
    void mapsBehaviourPlanSpeechFirstAndFallsBackToPayload() {
        Event validPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"payload-speech\"}");
        Event invalidPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{invalid-json");

        assertEquals("payload-speech", EventPromptSerializer.toPromptContent(validPlan));
        assertEquals("{invalid-json", EventPromptSerializer.toPromptContent(invalidPlan));
    }
}
