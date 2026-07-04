package ch.zhaw.prometheus.model.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ch.zhaw.prometheus.model.event.Event;

class PromptEventContentAdapterUnitTest {
    private final FaceEmotionPromptEventContentAdapter faceEmotionAdapter = new FaceEmotionPromptEventContentAdapter();
    private final BehaviourPlanPromptEventContentAdapter behaviourPlanAdapter = new BehaviourPlanPromptEventContentAdapter();
    private final SocialSituationChangePromptEventContentAdapter socialChangeAdapter =
            new SocialSituationChangePromptEventContentAdapter();
    private final SocialContextPromptEventContentAdapter socialContextAdapter =
            new SocialContextPromptEventContentAdapter();
    private final WeatherPromptEventContentAdapter weatherAdapter = new WeatherPromptEventContentAdapter();

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

    @Test
    void mapsSocialSituationChangeToReadablePromptContent() {
        Event change = Event.observation(Event.TYPE_SOCIAL_SITUATION_CHANGE, Event.ACTOR_SYSTEM,
                "{\"changeType\":\"arrival\",\"previousHumanCount\":0,\"currentHumanCount\":1,"
                        + "\"currentLargestGroupSize\":1,\"confidence\":0.83,"
                        + "\"reason\":\"human count increased from 0 to 1\"}");

        assertEquals(
                "Social situation change: arrival (humans 0 -> 1, largest group 1, confidence 0.83). Reason: human count increased from 0 to 1",
                this.socialChangeAdapter.toPromptContent(change));
    }

    @Test
    void mapsInvalidSocialSituationChangeConservatively() {
        Event change = Event.observation(Event.TYPE_SOCIAL_SITUATION_CHANGE, Event.ACTOR_SYSTEM, "{invalid-json");

        assertEquals("Social situation changed.", this.socialChangeAdapter.toPromptContent(change));
    }

    @Test
    void mapsSocialContextToReadablePromptContent() {
        Event context = Event.observation(Event.TYPE_SOCIAL_CONTEXT, Event.ACTOR_USER,
                "{\"schemaVersion\":1,\"humanCount\":3,\"groupCount\":1,\"singletonCount\":1,"
                        + "\"largestGroupSize\":2,\"groups\":[{\"memberIds\":[1,2],\"size\":2},"
                        + "{\"memberIds\":[3],\"size\":1}],\"people\":["
                        + "{\"id\":1,\"detectionConfidence\":0.92,"
                        + "\"movement\":{\"state\":\"moving\",\"confidence\":0.72},"
                        + "\"attention\":{\"state\":\"attending\",\"confidence\":0.76,"
                        + "\"personVisible\":true,\"faceVisible\":true,\"nearFrontal\":true,"
                        + "\"centered\":true,\"frontalCentered\":true}},"
                        + "{\"id\":2,\"detectionConfidence\":0.86,"
                        + "\"movement\":{\"state\":\"approaching\",\"confidence\":0.81},"
                        + "\"attention\":{\"state\":\"not_attending\",\"confidence\":0.44,"
                        + "\"personVisible\":true,\"faceVisible\":true,\"nearFrontal\":true,"
                        + "\"centered\":false,\"frontalCentered\":false}}]}");

        assertEquals(
                "Social context: 3 people visible; 1 group, largest 2, singletons 1. Groups: size 2 (members 1, 2); size 1 (members 3). People: person 1 detection 0.92, movement moving 0.72, attention attending 0.76 (person visible, face likely, centered/frontal); person 2 detection 0.86, movement approaching 0.81, attention not attending 0.44 (person visible, face likely, near frontal)",
                this.socialContextAdapter.toPromptContent(context));
    }

    @Test
    void mapsInvalidSocialContextConservatively() {
        Event context = Event.observation(Event.TYPE_SOCIAL_CONTEXT, Event.ACTOR_USER, "{invalid-json");

        assertEquals("Social context observed.", this.socialContextAdapter.toPromptContent(context));
    }

    @Test
    void mapsCurrentWeatherToReadablePromptContent() {
        Event weather = Event.observation(Event.TYPE_WEATHER_CURRENT, Event.ACTOR_SYSTEM,
                "{\"location_label\":\"Zurich, Switzerland\",\"condition\":\"rain\",\"intensity\":\"light\","
                        + "\"wind\":\"windy\",\"temperature_c\":18.4,\"cloud_cover\":72,"
                        + "\"observed_at\":\"2026-06-21T10:00\"}");

        assertEquals(
                "Current weather for Zurich, Switzerland: rain, light intensity, wind is windy, 18.4 C, cloud cover 72%. Observed at 2026-06-21T10:00",
                this.weatherAdapter.toPromptContent(weather));
    }

    @Test
    void mapsWeatherForecastToReadablePromptContent() {
        Event weather = Event.observation(Event.TYPE_WEATHER_FORECAST, Event.ACTOR_SYSTEM,
                "{\"location_label\":\"Lugano, Switzerland\",\"days\":["
                        + "{\"date\":\"2026-06-21\",\"condition\":\"clear\",\"intensity\":\"none\","
                        + "\"temperature_min_c\":20,\"temperature_max_c\":29},"
                        + "{\"date\":\"2026-06-22\",\"condition\":\"storm\",\"intensity\":\"heavy\","
                        + "\"temperature_min_c\":18,\"temperature_max_c\":24}]}");

        assertEquals(
                "Weather forecast for Lugano, Switzerland: 2026-06-21 clear, 20-29 C; 2026-06-22 storm (heavy), 18-24 C",
                this.weatherAdapter.toPromptContent(weather));
    }

    @Test
    void mapsInvalidWeatherPayloadConservatively() {
        Event weather = Event.observation(Event.TYPE_WEATHER_CURRENT, Event.ACTOR_SYSTEM, "{invalid-json");

        assertEquals("Weather observation received.", this.weatherAdapter.toPromptContent(weather));
    }
}


