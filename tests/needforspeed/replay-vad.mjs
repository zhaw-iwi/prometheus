import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { LocalVadSegmenter } from "../../src/main/resources/public/transcription/local-vad.js";

// Offline segmentation evidence only: no ASR, microphone, room or provider timing.
function wav(buffer) {
  if (buffer.toString("ascii", 0, 4) !== "RIFF") throw new Error("Expected PCM WAV");
  let rate, channels, bits, data;
  for (let i = 12; i + 8 <= buffer.length;) {
    const size = buffer.readUInt32LE(i + 4), kind = buffer.toString("ascii", i, i + 4);
    if (kind === "fmt ") {
      if (buffer.readUInt16LE(i + 8) !== 1) throw new Error("Expected PCM WAV");
      channels = buffer.readUInt16LE(i + 10); rate = buffer.readUInt32LE(i + 12); bits = buffer.readUInt16LE(i + 22);
    }
    if (kind === "data") data = buffer.subarray(i + 8, i + 8 + size);
    i += 8 + size + (size % 2);
  }
  if (channels !== 1 || bits !== 16 || !rate || !data) throw new Error("Expected mono 16-bit WAV");
  return { rate, samples: Float64Array.from({ length: data.length / 2 }, (_, i) => data.readInt16LE(i * 2) / 32768) };
}

function replay({ samples, rate }, silenceDurationSeconds, noise = false) {
  const commits = [];
  const vad = new LocalVadSegmenter({ silenceDurationSeconds, onCommit: event => commits.push(event) });
  let seed = 17;
  // Match the 50ms sampler and use its preceding 1024 waveform samples.
  for (let at = 0; at < samples.length / rate * 1000 + 2500; at += 50) {
    const end = Math.round(at * rate / 1000);
    let sum = 0;
    for (let i = end - 1024; i < end; i++) {
      seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
      const level = (samples[i] || 0) + (noise ? (seed / 4294967296 - 0.5) * 0.002 : 0);
      sum += level * level;
    }
    vad.observe(Math.sqrt(sum / 1024), at);
  }
  return commits;
}

export async function evaluatePauseCorpus() {
  const base = new URL("../fixtures/needforspeed/", import.meta.url);
  const labels = JSON.parse(await readFile(new URL("pauses-v1.json", base), "utf8"));
  const cases = [];
  for (const label of labels.cases) {
    const audio = wav(await readFile(new URL(label.file, base)));
    cases.push({ name: label.file, audio, expected: label.expectedNaturalTurns });
    if (label.file === "pause-short.wav") {
      cases.push({ name: "short + deterministic low-level noise", audio, expected: 1, noise: true });
      const gap = Math.round(audio.rate * 2.5), samples = new Float64Array(audio.samples.length * 2 + gap);
      samples.set(audio.samples); samples.set(audio.samples, audio.samples.length + gap);
      cases.push({ name: "two sequential synthetic utterances, 2.5s gap", audio: { ...audio, samples }, expected: 2 });
    }
  }
  return cases.flatMap(c => [0.8, 1.5].map(silence => {
    const commits = replay(c.audio, silence, c.noise);
    return { case: c.name, silenceSeconds: silence, expectedTurns: c.expected, commits: commits.length,
      extraSegments: Math.max(0, commits.length - c.expected), missingSegments: Math.max(0, c.expected - commits.length),
      completionDelayMs: commits.map(c => c.observedAtMs - c.lastVoiceAtMs), transcriptErrors: "NOT RUN" };
  }));
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  const report = { fixtureRevision: "nfs-pauses-v1", method: "50ms RMS sampler, 1024 PCM samples at fixture rate; no ASR", results: await evaluatePauseCorpus() };
  const json = JSON.stringify(report, null, 2) + "\n";
  if (process.argv[2]) await writeFile(process.argv[2], json);
  else process.stdout.write(json);
}
