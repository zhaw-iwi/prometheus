package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;

class PromptEventContentAdapterUnitTest {
    private final FaceEmotionPromptEventContentAdapter faceEmotionAdapter = new FaceEmotionPromptEventContentAdapter();
    private final BehaviourPlanPromptEventContentAdapter behaviourPlanAdapter = new BehaviourPlanPromptEventContentAdapter();

    @Test
    void mapsFaceEmotionWithConfidence() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"emotion\":\"happy\",\"confidence\":0.83,\"valence\":0.7}");

        assertEquals("User facial emotion: happy (confidence 0.83)", faceEmotionAdapter.toPromptContent(faceEmotion));
    }

    @Test
    void mapsFaceEmotionWithoutConfidence() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{\"emotion\":\"sad\"}");

        assertEquals("User facial emotion: sad", faceEmotionAdapter.toPromptContent(faceEmotion));
    }

    @Test
    void mapsFaceEmotionInvalidPayloadConservatively() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER, "{invalid-json");

        assertEquals("User facial emotion observed.", faceEmotionAdapter.toPromptContent(faceEmotion));
    }

    @Test
    void mapsFaceEmotionWithExplicitUserName() {
        Event faceEmotion = Event.observation(Event.TYPE_FACE_EMOTION, Event.ACTOR_USER,
                "{\"userName\":\"Alice\",\"emotion\":\"happy\",\"confidence\":0.83}");

        assertEquals("User Alice facial emotion: happy (confidence 0.83)", faceEmotionAdapter.toPromptContent(faceEmotion));
    }

    @Test
    void mapsBehaviourPlanSpeechOnlyAndSuppressesNonSpeechPayload() {
        Event validPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":\"payload-speech\"}");
        Event omittedSpeechPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT,
                "{\"speech\":null,\"nonVerbal\":{\"gesture\":\"EXPLAIN\"}}");
        Event invalidPlan = Event.response(Event.TYPE_ASSISTANT_BEHAVIOUR_PLAN, Event.ACTOR_ASSISTANT, "{invalid-json");

        assertEquals("payload-speech", behaviourPlanAdapter.toPromptContent(validPlan));
        assertEquals("", behaviourPlanAdapter.toPromptContent(omittedSpeechPlan));
        assertEquals("", behaviourPlanAdapter.toPromptContent(invalidPlan));
    }
}


