import { LiveTranscriptionClient } from "./client.js";
import { ScopedTranscriptIngress } from "./ingress.js";
import { TranscriptionEventRuntime, OrderedTranscriptAssembler } from "./events.js";
import { BrowserLocalVad, LocalVadSegmenter } from "./local-vad.js";
import { MicrophoneLease, TranscriptionMedia } from "./media.js";
import { TranscriptionSettingsPanel } from "./settings-panel.js";
import { TranscriptionPreferences, buildAudioConstraints, captureSummary } from "./settings.js";
import { TranscriptionTransport } from "./transport.js";

globalThis.PrometheusTranscription = Object.freeze({
  LiveTranscriptionClient,
  ScopedTranscriptIngress,
  TranscriptionEventRuntime,
  OrderedTranscriptAssembler,
  BrowserLocalVad,
  LocalVadSegmenter,
  MicrophoneLease,
  TranscriptionMedia,
  TranscriptionSettingsPanel,
  TranscriptionPreferences,
  TranscriptionTransport,
  buildAudioConstraints,
  captureSummary,
});
globalThis.dispatchEvent?.(new Event("prometheus-transcription-ready"));
