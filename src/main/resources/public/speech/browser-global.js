import { BehaviourSpeechPlaybackQueue, OutputLease } from "./playback.js";

globalThis.PrometheusSpeechPlayback = Object.freeze({
  BehaviourSpeechPlaybackQueue,
  OutputLease,
});
globalThis.dispatchEvent?.(new Event("prometheus-speech-playback-ready"));
