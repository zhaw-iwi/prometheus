import { turnTimings } from "../performance/timings.js";
globalThis.PrometheusTimings = turnTimings;
import { BehaviourSpeechPlaybackQueue, OutputLease } from "./playback.js";
import { createSpeechAudio } from "./progressive.js";
import { preparePcmSpeech } from "./pcm.js";

globalThis.PrometheusSpeechPlayback = Object.freeze({
  BehaviourSpeechPlaybackQueue,
  OutputLease,
  createSpeechAudio,
  preparePcmSpeech,
});
globalThis.dispatchEvent?.(new Event("prometheus-speech-playback-ready"));
