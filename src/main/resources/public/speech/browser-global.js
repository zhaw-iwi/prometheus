import { turnTimings } from "../performance/timings.js";
globalThis.PrometheusTimings = turnTimings;
import { BehaviourSpeechPlaybackQueue, OutputLease } from "./playback.js";
import { createSpeechAudio } from "./progressive.js";

globalThis.PrometheusSpeechPlayback = Object.freeze({
  BehaviourSpeechPlaybackQueue,
  OutputLease,
  createSpeechAudio,
});
globalThis.dispatchEvent?.(new Event("prometheus-speech-playback-ready"));
