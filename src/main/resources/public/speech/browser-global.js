import { turnTimings, timedAudioBlob } from "../performance/timings.js";
globalThis.PrometheusTimings = turnTimings;
globalThis.PrometheusTimedAudioBlob = timedAudioBlob;
import { BehaviourSpeechPlaybackQueue, OutputLease } from "./playback.js";

globalThis.PrometheusSpeechPlayback = Object.freeze({
  BehaviourSpeechPlaybackQueue,
  OutputLease,
});
globalThis.dispatchEvent?.(new Event("prometheus-speech-playback-ready"));
