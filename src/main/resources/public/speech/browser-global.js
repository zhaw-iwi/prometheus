import { turnTimings, timedAudioBlob } from "../performance/timings.js";
globalThis.PrometheusTimings = turnTimings;
globalThis.PrometheusTimedAudioBlob = timedAudioBlob;
import { BehaviourSpeechPlaybackQueue, OutputLease } from "./playback.js";
import { createSpeechAudio } from "./progressive.js";

globalThis.PrometheusSpeechPlayback = Object.freeze({
  BehaviourSpeechPlaybackQueue,
  OutputLease,
  createSpeechAudio,
});
globalThis.dispatchEvent?.(new Event("prometheus-speech-playback-ready"));
