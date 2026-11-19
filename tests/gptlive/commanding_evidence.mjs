// Offline evidence analysis. Early native replies are real onsets, not missing data.
export function armingReady(memory, now) {
  const receipt = memory['task.response'], occasion = memory['core.task.response'];
  const after = Date.parse(memory['task.after']);
  return receipt?.purpose === 'ARMING' && receipt.status === 'CAPTURED'
    && typeof receipt.id === 'string' && receipt.id === occasion?.id
    && receipt.session === occasion.session && receipt.epoch === occasion.epoch
    && Number.isFinite(after) && now > after;
}

export function speechLatency(events, input) {
  const voices = events.filter(e => e.type === 'output_voice' && e.at >= input.startMs);
  const first = voices[0];
  const nearEnd = voices.find(e => e.at >= input.speechEndMs - 500);
  return {
    speechEndToOutputMs: first ? first.at - input.speechEndMs : null,
    legacyNearEndOnsetMs: nearEnd ? nearEnd.at - input.speechEndMs : null
  };
}

export function conversationLatency(turns) {
  if (turns.length !== 3 || turns.map(t => t.name).join(',') !== 'hello,activities,planning')
    throw Error('Three matched ordinary turns are required');
  const samples = turns.map(t => ({name: t.name, ...speechLatency(t.events, t.input)}));
  if (samples.some(t => !Number.isFinite(t.speechEndToOutputMs))) throw Error('Missing observed native audio');
  const values = samples.map(t => t.speechEndToOutputMs).sort((a, b) => a - b);
  return {method: 'first_native_audio_after_input_start', n: 3, medianMs: values[1], maxMs: values[2], minMs: values[0], p95: null, samples};
}

export function compareConversations(baseline, coordination) {
  for (const name of ['hello', 'activities', 'planning']) {
    if (baseline.stimulus.clips[name].sha256 !== coordination.stimulus.clips[name].sha256)
      throw Error('Unmatched visitor audio');
  }
  if (JSON.stringify(baseline.plan.models) !== JSON.stringify(coordination.plan.models))
    throw Error('Unmatched provider models');
  const base = conversationLatency(baseline.turns.slice(0, 3)), measured = conversationLatency(coordination.turns.slice(0, 3));
  return {baseline: base, coordination: measured,
    medianDifferenceMs: measured.medianMs - base.medianMs,
    maximumDifferenceMs: measured.maxMs - base.maxMs,
    passed: measured.medianMs <= base.medianMs + 1000 && measured.maxMs <= base.maxMs + 2000};
}
