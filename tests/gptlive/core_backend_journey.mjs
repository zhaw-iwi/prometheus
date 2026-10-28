// Paid real-app acceptance. Test pacing is not a production playback-completion signal.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
const [permission, output] = process.argv.slice(2);
const selection = process.argv.find(v => v.startsWith('--agent='))?.split('=')[1];
const definitions = {coordination: 'core.coordination', live: 'core.live_multimodal', face: 'core.facial_expression_sensitivity',
  social: 'core.social_context_sensitivity', space: 'core.generic_multimodal_behaviour_at_social_space',
  task: 'core.generic_multimodal_behaviour', 'space-task': 'core.generic_multimodal_behaviour_at_social_space', rps: 'core.rock_scissor_paper',
  'user-guess': 'core.role_clarification_guessing_game', 'agent-guess': 'core.role_clarification_guessing_game', 'care-guess': 'usecases.healthcare.guessing_game_user_guess'};
const taskJourney = selection === 'task' || selection === 'space-task';
const coordinationJourney = selection === 'coordination';
const coordinationScenario = process.argv.find(v => v.startsWith('--scenario='))?.split('=')[1] || 'baseline';
// A direct question or an explicit acceptance instruction tells the visitor what to do; "ready" alone does not.
const acceptanceQuestion = /\b(?:shall|would you like|want|wanna|accept|start|begin|ready|go ahead|proceed|try it)\b[^?]*\?|\bsay\s+["']?start["']?\s+to\s+accept\b/i;
const rpsJourney = selection === 'rps';
const guessingJourney = selection?.endsWith('-guess');
const gameJourney = rpsJourney || guessingJourney;
const ambientSensing = !gameJourney && !(coordinationJourney && coordinationScenario !== 'baseline');
const connectionOnly = !rpsJourney && !coordinationJourney;
const base = process.env.PROMETHEUS_BASE_URL;
if (permission !== '--allow-paid-provider' || !output || !definitions[selection] || !base
    || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname))
  throw Error('Use run_acceptance.py --paid-core live with its disposable local database.');
const key = process.env.OPENAI_API_KEY, token = process.env.PROMETHEUS_ADMIN_TOKEN;
if (!key || !token) throw Error('Missing provider/admin environment credentials');
const report = {startedAt: new Date().toISOString(), selection, passed: false, turns: [],
  limitations: 'Synthetic audio/sensors; captions and received media do not prove physical audibility or movement.'};
const clips = {info: 'Hello Valerian. What is the SIRA Lab?',
  sense: selection === 'face' ? 'How does my facial expression look?' : 'How many people can you currently see?',
  ...(selection === 'live' ? {gesture: 'Please show me the paper hand sign.'} : {}),
  ...(selection === 'face' ? {correction: 'I am not happy. Please stop commenting on my face.'} : {})};
if (taskJourney) Object.assign(clips, {
  proposal: "Please propose, but don't start, a task that waits for one rock hand sign, then shows paper silently once and finishes.",
  accept: 'Yes, please.', acknowledge: 'Okay.'
});
if (coordinationJourney && coordinationScenario !== 'baseline') {
  for (const name of Object.keys(clips)) delete clips[name];
  Object.assign(clips, {
  proposal: "Propose one dry joke on a fresh rock after acceptance, first checking rock recognition; I show signs and judge whether I like your joke, with one action only in this session using default settings.",
  accept: 'Start the saved proposal now.',
  acknowledge: 'Okay.'
  });
  if (coordinationScenario === 'revision') clips.revision = 'Make only the response a dry one-liner, keeping every other term unchanged.';
  if (coordinationScenario === 'capture') Object.assign(clips, {fragment: 'A dry', ending: 'one-liner, keeping every other term unchanged.'});
  if (coordinationScenario === 'delivery') Object.assign(clips, {back: 'Return to the initial conversation state.', status: 'Which state are we in?'});
}
const audio = {};
if (rpsJourney) {
  for (const key of Object.keys(clips)) delete clips[key];
  Object.assign(clips, {ready: 'I am ready to play rock scissors paper.', replay: 'Yes, another round please.'});
}
if (guessingJourney) {
  for (const key of Object.keys(clips)) delete clips[key];
  Object.assign(clips, selection === 'agent-guess' ? {
    choose: 'I will choose a secret item. You ask me yes or no questions and try to guess it. Start now.',
    hint: 'The animal I chose has four legs and barks. Please make a guess now.', solved: 'Yes, it is a dog. You guessed correctly.',
    replay: 'Let us play another round with the same roles.'
  } : {
    choose: selection === 'care-guess' ? 'Yes, I want to play. You choose an item and I ask questions.' : 'You choose a secret item. I ask yes or no questions and try to guess it. Start now.',
    binary: 'Is it alive?', unknown: 'Does the item you picked have a blue colour?', unclear: 'Is it bigger?', giveup: 'I give up. Please tell me the answer.',
    replay: 'Let us play another round with the same roles.', solved: 'Is it a dog?'
  });
}
const cache = 'target/core-provider-audio'; fs.mkdirSync(cache, {recursive: true});
for (const [name, input] of Object.entries(clips)) {
  const body = JSON.stringify({model: 'gpt-4o-mini-tts', voice: 'alloy', input, response_format: 'pcm'});
  const file = path.join(cache, createHash('sha256').update(body).digest('hex') + '.pcm');
  if (!fs.existsSync(file)) {
    const response = await fetch('https://api.openai.com/v1/audio/speech', {method: 'POST',
      headers: {Authorization: `Bearer ${key}`, 'Content-Type': 'application/json'}, body, signal: AbortSignal.timeout(60000)});
    if (!response.ok) throw Error(`Synthetic speech HTTP ${response.status}`);
    fs.writeFileSync(file, Buffer.from(await response.arrayBuffer()));
  }
  audio[name] = [...fs.readFileSync(file)];
}
if (coordinationScenario === 'capture') audio.revision = [...audio.fragment, ...new Uint8Array(24000 * 2 * 1.2), ...audio.ending];
const browser = await chromium.launch({headless: true, args: ['--autoplay-policy=no-user-gesture-required']});
let page, agentPath, handle, heartbeat, scoped;
let sensing = selection === 'face' ? {type: 'obs.emotion.face', data: {emotion: 'neutral', confidence: .95, facePresent: true}}
  : {type: selection === 'social' ? 'obs.social.context' : 'obs.social.grouping', data: {humanCount: 1, groupCount: 0, largestGroupSize: 1}};
async function freshSense() { return observe(sensing.type, {...sensing.data, ts: new Date().toISOString()}); }
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
async function api(route, method = 'GET', body, headers = scoped) {
  const response = await fetch(base + route, {method, headers: {...headers, 'Content-Type': 'application/json'},
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(45000)});
  if (!response.ok) throw Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
async function observe(type, payload) {
  return api(agentPath + '/acknowledge', 'POST', {type, actor: 'sensor', kind: 'observation', payload: JSON.stringify(payload)});
}
async function storage() {
  return Object.fromEntries((await api(agentPath + '/storage')).map(entry => [entry.key, JSON.parse(entry.value)]));
}
async function until(label, action, timeout = 40000) {
  const start = Date.now();
  while (Date.now() - start < timeout) { const result = await action(); if (result) return result; await wait(500); }
  throw Error(`Timed out: ${label}`);
}
async function say(name) {
  console.log(`Visitor: ${name}`);
  await page.evaluate(async ({bytes, name}) => {
    const view = new DataView(Uint8Array.from(bytes).buffer), samples = new Float32Array(bytes.length / 2);
    for (let i = 0; i < samples.length; i++) samples[i] = view.getInt16(i * 2, true) / 32768;
    const buffer = ac.createBuffer(1, samples.length, 24000); buffer.copyToChannel(samples, 0);
    const source = ac.createBufferSource(); source.buffer = buffer; source.connect(destination);
    mark('trial.input', {name});
    await new Promise(resolve => {source.onended = resolve; source.start();});
  }, {bytes: audio[name], name});
}
async function captionsFrom(index) {
  return page.evaluate(index => {
    let text = '', previous;
    for (const event of logs.slice(index).filter(e => e.type === 'session.output_transcript.delta')) {
      // A display-only separator avoids joining separate utterances into e.g. "PersonNein".
      // Raw events remain intact; this is not a production turn/playback boundary.
      if (previous && Number.isFinite(event.start_ms) && Number.isFinite(previous.end_ms)
          && event.start_ms - previous.end_ms >= 800) text += '\n';
      text += event.delta || ''; previous = event;
    }
    return text;
  }, index);
}
async function settled(index, expected) {
  await until('assistant output' + (expected ? ' matching ' + expected : ''), async () => {
    const status = await api(`${agentPath}/live/sessions/${handle}`);
    assert.equal(status.state, 'attached', 'Live closed: ' + status.reason);
    const text = (await captionsFrom(index)).trim(); return text && (!expected || expected.test(text));
  });
  // This is a test pacing heuristic, not a production audio-completion contract.
  await page.waitForFunction(() => performance.now() - logs.findLast(e => e.type === 'session.output_transcript.delta').at > 3500,
    {}, {timeout: 30000});
  const status = await api(`${agentPath}/live/sessions/${handle}`);
  assert.equal(status.state, 'attached', 'Live closed: ' + status.reason);
  return captionsFrom(index);
}
async function coordinationRevision() {
  let index;
  await until('first task completed', async () => (await storage())['task.phase'] === 'COMPLETED');
  const original = (await storage())['coordination.agreement'];
  index = await page.evaluate(() => logs.length); await say('revision');
  const revised = await until('revised draft', async () => (await storage())['coordination.draft']);
  const protectedTerms = agreement => {
    const copy = structuredClone(agreement); delete copy.goal; delete copy.agentRole; delete copy.questions;
    delete copy.reactions.goal; copy.reactions.rules.forEach(rule => delete rule.action); return copy;
  };
  assert.ok(revised.reactions); assert.deepEqual(protectedTerms(revised), protectedTerms(original));
  report.revision = await settled(index, /one.liner|dry|rock|check/i);
  if (coordinationScenario === 'capture') {
    const history = await api(agentPath + '/live/history');
    report.captureHistory = history;
    const inputs = history.filter(e => e.type === 'obs.user_utterance' && /dry|liner/i.test(e.payload));
    assert.equal(inputs.length, 1, 'Paused request must be admitted once');
    assert.match(inputs[0].payload, /dry.*one.liner/i);
  }
  assert.equal(Object.keys((await storage())['coordination.evidence']).length, 0);
  index = await page.evaluate(() => logs.length);
  await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
  report.reprepared = await settled(index, acceptanceQuestion);
  await until('new acceptance question captured', async () => (await storage())['core.task.spokenPrompt']);
  index = await page.evaluate(() => logs.length); await say('accept');
  await until('revised activation captured', async () => (await storage())['task.response']?.status === 'CAPTURED');
  report.reactivation = await settled(index, /wait|rock|active|armed/i);
  assert.equal((await storage())['task.actions'], 0);
  await wait(3500); index = await page.evaluate(() => logs.length);
  await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
  await until('revised response authorized', async () => (await storage())['task.actions'] === 1);
  report.revisedContribution = await settled(index);
  report.revisedAgreement = (await storage())['coordination.agreement'];
}
const joke = 'I told my computer I needed a break, and it froze.';
const deliveredJoke = /computer.*(?:break|brake).*froze/is;
async function noTaskSpeech(index, label, duration = 5000) {
  await wait(duration);
  const words = await captionsFrom(index);
  assert.ok(!deliveredJoke.test(words), label + ': premature or duplicate joke: ' + words);
  (report.negativeWindows ??= []).push({label, duration, captions: words});
}
async function coordinationDelivery() {
  let index = await page.evaluate(() => logs.length);
  await noTaskSpeech(index, 'before acceptance');
  index = await page.evaluate(() => logs.length); await say('accept');
  await until('armed response captured', async () => (await storage())['task.response']?.status === 'CAPTURED');
  report.activation = await settled(index, /wait|armed|active/i);
  assert.equal((await storage())['task.actions'], 0);
  await noTaskSpeech(index, 'armed before fresh cue');
  index = await page.evaluate(() => logs.length);
  await observe('obs.hand.sign', {sign:'rock', confidence:1, ts:new Date().toISOString()});
  report.deliveryPending = await storage();
  assert.equal(report.deliveryPending['task.phase'], 'FINISHING');
  assert.equal(report.deliveryPending['task.response']?.status, 'PENDING');
  report.contribution = await settled(index, deliveredJoke);
  await until('associated final playback confirmed', async () => (await storage())['task.phase'] === 'COMPLETED');
  report.deliveryCompleted = (await storage())['coordination.delivery'];
  assert.equal(report.deliveryCompleted.status, 'PLAYBACK_CONFIRMED');
  assert.ok(await page.evaluate(() => logs.some(e => e.type === 'trial.playback_ack' && e.accepted === true)));
  assert.equal((await storage())['task.actions'], 1);
  const normalized = report.contribution.toLowerCase();
  assert.equal(normalized.split('froze').length - 1, 1, 'Deliver the agreed joke once');
  index = await page.evaluate(() => logs.length); await say('acknowledge');
  await noTaskSpeech(index, 'after acknowledgement and context refresh', 8000);
  index = await page.evaluate(() => logs.length); await say('back');
  await until('return committed', async () => !(await storage())['task.spec']);
  report.returned = await settled(index, /conversation|beginning|initial/i);
  assert.equal((await storage())['task.phase'], 'CONFIGURATION');
  index = await page.evaluate(() => logs.length); await say('status');
  report.stateAnswer = await settled(index, /conversation/i);
  assert.equal((await storage())['task.phase'], 'CONFIGURATION');
}
try {
  const admin = {'X-Prometheus-Admin-Token': token};
  const code = ('G' + Math.random().toString(36).slice(2, 6)).toUpperCase();
  const access = await api('/admin/access-codes', 'POST', {code, enabled: true}, admin);
  await api(`/admin/access-codes/${access.id}/agent-types`, 'PUT', {agentTypeKeys: [definitions[selection]]}, admin);
  scoped = {'X-Prometheus-Access-Code': code};
  const agent = await api('/demo/agents', 'POST', {agentDefinitionKey: definitions[selection]});
  agentPath = '/demo/agents/' + agent.id; report.agentId = agent.id;
  if (coordinationJourney && coordinationScenario === 'delivery') {
    await api(agentPath + '/acknowledge', 'POST', {type:'obs.user_utterance', actor:'user', kind:'observation',
      payload:`Propose but do not start: after acceptance, wait for a fresh rock then tell exactly this one-line joke once: ${joke} I show the sign and judge the joke; success means I accept it. One action in this session, standard recognition settings. No preparation checks.`});
    assert.deepEqual((await storage())['coordination.draft'].questions, [], 'Complete supplied terms need no additional preference question');
    assert.equal((await storage())['coordination.draft'].checks.length, 0);
  }
  if (coordinationJourney && ['revision', 'capture'].includes(coordinationScenario)) {
    const text = input => api(agentPath + '/acknowledge', 'POST', {type:'obs.user_utterance', actor:'user', kind:'observation', payload:input});
    await text('Propose one short joke on rock after acceptance, first verifying rock recognition. I show the sign and judge the joke. One action in this session, standard settings. Do not start yet.');
    let memory = await storage();
    assert.equal(memory['coordination.draft'].checks.length, 1);
    assert.deepEqual(memory['coordination.draft'].questions, []);
    await observe('obs.hand.sign', {sign:'rock', confidence:1, ts:new Date().toISOString()});
    await text('Start the saved proposal now.');
    await wait(3500);
    await observe('obs.hand.sign', {sign:'rock', confidence:1, ts:new Date().toISOString()});
    memory = await storage(); assert.equal(memory['task.phase'], 'COMPLETED');
    assert.equal(memory['task.actions'], 1);
    report.textSetup = {agreement:memory['coordination.agreement'], outcome:memory['coordination.outcome']};
  }
  if (ambientSensing) await freshSense();
  let heartbeatBusy = false;
  heartbeat = setInterval(async () => {
    if (heartbeatBusy) return;
    heartbeatBusy = true;
    try {
      if (ambientSensing) await freshSense();
      if (handle) await api(`${agentPath}/live/sessions/${handle}`);
    } catch (error) { report.heartbeatError = error.message; }
    finally { heartbeatBusy = false; }
  }, 5000);
  page = await browser.newPage(); await page.goto(base + '/valerian/');
  const offer = await page.evaluate(async ({agentPath, code, connectionOnly}) => {
    window.logs = []; window.mark = (type, fields = {}) => logs.push({type, ...fields, at: performance.now()});
    window.ac = new AudioContext({sampleRate: 24000}); await ac.resume();
    window.destination = ac.createMediaStreamDestination();
    const oscillator = ac.createOscillator(), gain = ac.createGain(); gain.gain.value = 0;
    oscillator.connect(gain).connect(destination); oscillator.start();
    window.pc = new RTCPeerConnection(); destination.stream.getTracks().forEach(track => pc.addTrack(track, destination.stream));
    const player = document.createElement('audio'); player.autoplay = true; document.body.append(player);
    window.player = player;
    const LivePlaybackObserver = connectionOnly ? null : (await import('/live/playback-cue.js')).LivePlaybackObserver;
    window.playback = LivePlaybackObserver && new LivePlaybackObserver(player, async cue => {
      const response = await fetch(`${agentPath}/live/sessions/${window.liveHandle}/playback-cues/${cue.id}`, {
        method: 'POST', headers: {'Content-Type': 'application/json', 'X-Prometheus-Access-Code': code},
        body: JSON.stringify({segmentId: cue.segmentId})
      });
      mark('trial.playback_ack', {ok: response.ok, accepted: response.ok && await response.json()});
    }, value => mark('trial.playback_diagnostic', value));
    window.startPlaybackPoll = handle => {
      window.liveHandle = handle; let busy = false;
      window.playbackTimer = setInterval(async () => {
        if (busy) return; busy = true;
        try {
          const response = await fetch(`${agentPath}/live/sessions/${handle}/updates`, {headers: {'X-Prometheus-Access-Code': code}});
          if (response.ok) {const update = await response.json(); playback?.update(update.playbackCue);}
        } finally {busy = false;}
      }, 1000);
    };
    pc.ontrack = event => {player.srcObject = event.streams[0]; playback?.stream(player.srcObject); player.play().catch(() => {});};
    window.dc = pc.createDataChannel('oai-events'); dc.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.type === 'session.input_transcript.delta') playback?.interrupt();
      if (!message.type.includes('audio.')) mark(message.type, message.type === 'session.started' ? {} : message);
    };
    await pc.setLocalDescription(await pc.createOffer());
    await new Promise(resolve => {
      if (pc.iceGatheringState === 'complete') resolve();
      else {pc.onicegatheringstatechange = () => {if (pc.iceGatheringState === 'complete') resolve();}; setTimeout(resolve, 2000);}
    });
    return pc.localDescription.sdp;
  }, {agentPath, code, connectionOnly});
  const session = await api(agentPath + '/live/sessions', 'POST', {sdp: offer, voice: 'willow'}); handle = session.handle;
  await page.evaluate(sdp => pc.setRemoteDescription({type: 'answer', sdp}), session.sdp);
  await page.waitForFunction(coordination => coordination
    ? pc.connectionState === 'connected' && dc.readyState === 'open'
    : logs.some(e => e.type === 'session.started'), coordinationJourney, {timeout: 20000});
  if (coordinationJourney) await until('provider session started', async () =>
    (await api(`${agentPath}/live/sessions/${handle}`)).recent?.some(e => e.type === 'session.started'));
  await api(`${agentPath}/live/sessions/${handle}/input?muted=false`, 'POST');
  await page.evaluate(handle => startPlaybackPoll(handle), handle);
  if (coordinationJourney) {
    await until('input unmuted', async () => (await api(`${agentPath}/live/sessions/${handle}`)).recent?.some(e => e.type === 'session.input_audio.unmuted'));
    await until('initial context delivered', async () => (await api(`${agentPath}/live/sessions/${handle}`)).context?.state === 'ready');
    await wait(1000); // Test startup pacing; not a production capture/delivery signal.
  }
  // LiveMultimodal intentionally has no unsolicited opening. Start with a user turn.
  const initial = await api(agentPath + '/state');
  for (const [name, expected] of (gameJourney || coordinationJourney && coordinationScenario !== 'baseline' ? [] : [['info', /SIRA|social|responsib|research|agent/i], ['sense', selection === 'face' ? /neutral|calm|relax/i : /one|1|single/i]])) {
    const index = await page.evaluate(() => logs.length);
    await say(name);
    const text = await settled(index, expected);
    if (name === 'info') assert.doesNotMatch(text, /Service Innovation and Robotics/i, 'Invented SIRA expansion');
    report.turns.push({name, text});
    assert.deepEqual(await api(agentPath + '/state'), initial, 'Ordinary requests must retain the interaction state');
  }
  if (guessingJourney) {
    const human = selection !== 'agent-guess';
    async function turn(name, expected) {
      const index = await page.evaluate(() => logs.length); await say(name);
      try { const text = await settled(index, expected); report.turns.push({name, text}); return text; }
      catch (error) { report.turns.push({name, text: await captionsFrom(index), failed: true}); throw error; }
    }
    const opening = await turn('choose', human ? /chosen|choose|ask|question|ready/i : /ready|think|chosen|question|guess/i);
    await until('active selected role', async () => (await storage())['guessing_round']?.phase === 'ACTIVE');
    const first = (await storage())['guessing_round'];
    assert.equal(first.role, human ? 'USER_GUESSES' : 'AGENT_GUESSES');
    if (human) {
      assert.equal(first.secret.name, 'teacup'); assert.doesNotMatch(opening, /\b(?:tea\s*cup|cup)\b/i, 'Opening leaked the secret');
      assert.doesNotMatch(opening, /you (?:think of|choose|pick)|I(?: will|['’]ll) (?:ask|guess)/i, 'Reversed guessing roles');
      const answer = await turn('binary', /\bno\b|not alive|not living/i); assert.doesNotMatch(answer, /\b(?:tea\s*cup|cup)\b/i);
      const unknown = await turn('unknown', /not (?:specified|known|defined)|haven['’]t.{0,40}(?:specified|assigned|chosen|colo)|don['’]t (?:know|have)|can['’]t (?:tell|say)|unspecified|no (?:particular|specific|fixed) colo/i);
      assert.doesNotMatch(unknown, /not blue|yes.{0,10}blue/i, 'Unspecified colour must not become a positive or negative fact');
      assert.doesNotMatch(unknown, /rephrase|ask.{0,20}yes.{0,10}no/i, 'Clear unknown fact must not be blamed on question wording');
      await turn('unclear', /than what|compared|bigger than|compare/i);
      assert.equal((await storage())['guessing_round'].id, first.id);
      await turn('giveup', /\b(?:tea\s*cup|cup)\b/i);
      await until('persisted reveal', async () => (await storage())['guessing_round']?.phase === 'REVEALED');
    } else {
      assert.equal(first.secret, undefined);
      await turn('hint', /dog/i);
      const confirmation = await turn('solved', /dog|got it|guessed|correct|nice|well played|lucky|leave it|another round/i);
      assert.doesNotMatch(confirmation, /wrong guess|not (?:a dog|correct|right)/i, 'Must not contradict the human confirmation');
      await until('human confirms guess', async () => (await storage())['guessing_round']?.phase === 'SOLVED');
      assert.equal((await storage())['guessing_round'].secret, undefined);
    }
    const replay = await turn('replay', human ? /chosen|question|ask|ready/i : /ready|choose|think|question|is (?:it|your)|does (?:it|your)|can (?:it|your)/i);
    assert.doesNotMatch(replay, /still (?:on|playing|guessing).{0,35}(?:one|item|secret)|I (?:don't|do not) have a new secret/i,
      'A confirmed fresh round must not be described as the old item or the agent choosing the human secret');
    await until('fresh round', async () => {const r = (await storage())['guessing_round']; return r?.phase === 'ACTIVE' && r.id !== first.id;});
    if (human) {
      assert.equal((await storage())['guessing_round'].secret.name, 'dog');
      await turn('solved', /yes|correct|dog/i);
      await until('correct guess persisted', async () => (await storage())['guessing_round']?.phase === 'SOLVED');
    } else assert.equal((await storage())['guessing_round'].secret, undefined);
    report.history = await api(agentPath + '/eventhistory');
  } else if (rpsJourney) {
    let previous;
    for (const name of ['ready', 'replay']) {
      let index = await page.evaluate(() => logs.length); await say(name);
      const round = await until('fresh round open after playback', async () => {
        const r = (await storage())['core.rps.round'];
        return r?.phase === 'WAITING' && r.id !== previous && r;
      });
      const preparation = await settled(index, /one.{0,20}two.{0,20}three|1.{0,20}2.{0,20}3/is);
      report.turns.push({name, text: preparation, round});
      assert.doesNotMatch(preparation, /I (?:go|chose|choose|picked|pick|have|am|['’]m).{0,15}(?:rock|paper|scissor)/i,
        'Live must not invent or announce a selected sign before the result');
      assert.equal((preparation.match(/one[\s,!.—-]+two[\s,!.—-]+three|1[\s,!.—-]+2[\s,!.—-]+3/gi) || []).length, 1,
        'The round must have one countdown');
      if (previous) {
        await observe('obs.hand.sign', {captureId: previous, sign: 'rock', confidence: 1, ts: new Date().toISOString()});
        assert.equal((await storage())['core.rps.round'].phase, 'WAITING', 'Old capture must not finish a replay');
      }
      index = await page.evaluate(() => logs.length);
      await observe('obs.hand.sign', {captureId: round.id, sign: 'rock', confidence: 1, ts: new Date().toISOString()});
      await until('deterministic round result', async () => (await storage())['core.rps.round']?.phase === 'RESULT');
      const memory = await storage(); const result = memory['rps_last_round'];
      assert.ok(result, 'Missing persisted result');
      const expected = result.winner === 'draw' ? /draw|tie/i : result.winner === 'user' ? /you win|you won|your win|you beat/i : /I win|I won|my win|I beat/i;
      const text = await settled(index, expected);
      assert.doesNotMatch(text, /\bStein\b|\bSchere\b|\bPapier\b/i, 'Legacy German diagnostic labels must not leak into English speech');
      report.turns.push({name: name + '-result', result, text});
      assert.equal(memory['core.rps.completed'], previous ? 2 : 1);
      previous = round.id;
    }
    report.history = await api(agentPath + '/eventhistory');
    assert.equal(report.history.filter(e => e.type === 'resp.behaviour_plan' && JSON.parse(e.payload).motion?.handSign).length, 2);
  } else if (taskJourney) {
    let index = await page.evaluate(() => logs.length); await say('proposal');
    await until('persisted draft', async () => (await storage())['task.draft']);
    report.proposal = await settled(index, /start|activate|ready|shall|begin/i);
    await until('completed proposal invitation', async () => (await storage())['core.task.spokenPrompt']);
    let memory = await storage(); assert.equal(memory['task.phase'], 'CONFIGURATION');
    assert.equal(memory['task.draft'].firstAction, 'ON_CUE'); assert.equal(memory['task.draft'].maxActions, 1);
    index = await page.evaluate(() => logs.length); await say('accept');
    await until('confirmed activation response', async () => (await storage())['task.response']?.status === 'CAPTURED');
    report.responseAssociation = (await storage())['task.response'];
    report.activation = await settled(index, /wait|ready|armed|active|rock/i);
    assert.doesNotMatch(report.activation, /activation.{0,20}pending|not (yet )?active|want.{0,20}start|shall.{0,20}start/i,
      'Live contradicted confirmed activation or asked for redundant acceptance');
    memory = await storage(); assert.equal(memory['task.phase'], 'RUNNING'); assert.equal(memory['task.actions'], 0);
    if (selection === 'space-task') {
      sensing.data.humanCount = 2; await freshSense(); await wait(2200); await freshSense();
      memory = await storage(); assert.equal(memory['task.phase'], 'RUNNING'); assert.equal(memory['task.actions'], 0);
    }
    await wait(3500); index = await page.evaluate(() => logs.length);
    await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
    await until('completed task', async () => (await storage())['task.phase'] === 'COMPLETED');
    report.completion = await settled(index, /complet|finish|done|paper/i);
    assert.doesNotMatch(report.completion, /hey there.{0,15}welcome|hello.{0,15}welcome|try a quick demo/i,
      'A task result must not be interrupted by an obsolete arrival greeting');
    report.history = await api(agentPath + '/eventhistory');
    assert.ok(report.history.filter(e => e.type === 'resp.behaviour_plan').some(e => JSON.parse(e.payload).motion?.handSign === 'paper'));
    assert.equal((await storage())['task.actions'], 1);
    await say('acknowledge'); await wait(4000);
    assert.equal((await storage())['task.phase'], 'COMPLETED'); assert.equal((await storage())['task.actions'], 1);
    report.finalState = await api(agentPath + '/state');
  } else if (coordinationJourney) {
    if (coordinationScenario === 'delivery') {
      await coordinationDelivery();
    } else if (['revision', 'capture'].includes(coordinationScenario)) {
      await coordinationRevision();
    } else if (coordinationScenario === 'recognition') {
      let index = await page.evaluate(() => logs.length); await say('proposal');
      await until('preparation draft', async () => (await storage())['coordination.draft']?.checks?.length === 1);
      report.proposal = await settled(index, /rock|recogn|check|prepar/i);
      let memory = await storage();
      assert.deepEqual(memory['coordination.draft'].questions, [], 'The self-contained request should not need technical tuning questions');
      assert.equal(memory['coordination.draft'].reactions.firstAction, 'ON_CUE');
      assert.equal(memory['coordination.draft'].reactions.maxActions, 1);
      assert.equal(memory['coordination.agreement'], undefined);
      index = await page.evaluate(() => logs.length);
      await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
      report.prepared = await settled(index, acceptanceQuestion);
      await until('completed acceptance question', async () => (await storage())['core.task.spokenPrompt']);
      assert.match(report.proposal + '\n' + report.prepared, /new|fresh|after.{0,20}(accept|start)/i,
        'The current proposal/preparation dialogue must explain that a fresh cue follows acceptance');
      assert.equal((await storage())['coordination.agreement'], undefined);
      // Repeated demonstration is still not consent.
      await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
      await wait(2000); assert.equal((await storage())['coordination.agreement'], undefined);
      index = await page.evaluate(() => logs.length); await say('accept');
      await until('captured activation', async () => (await storage())['task.response']?.status === 'CAPTURED');
      report.activation = await settled(index, /wait|armed|active|fresh/i);
      assert.doesNotMatch(report.activation, /shall.{0,20}start|want.{0,20}start|not (yet )?active/i);
      memory = await storage(); assert.equal(memory['task.phase'], 'RUNNING'); assert.equal(memory['task.actions'], 0);
      report.beforeCue = await captionsFrom(0);
      await wait(3500); index = await page.evaluate(() => logs.length);
      await observe('obs.hand.sign', {sign: 'rock', confidence: 1, ts: new Date().toISOString()});
      await until('one authorized contribution', async () => (await storage())['task.actions'] === 1);
      report.contribution = await settled(index);
      assert.equal((await storage())['task.actions'], 1);

    } else {
      assert.equal(coordinationScenario, 'baseline', 'Scenario is not implemented yet; do not report partial acceptance');
      assert.equal((await storage())['coordination.agreement'], undefined);
    }
    report.finalState = await api(agentPath + '/state');
    report.history = await api(agentPath + '/eventhistory');
  } else if (selection === 'live') {
    // Gesture-only requests intentionally require no spoken acknowledgement.
    await say('gesture');
    await until('persisted paper hand-sign plan', async () => {
      const history = await api(agentPath + '/eventhistory');
      const plans = history.filter(event => event.type === 'resp.behaviour_plan').map(event => JSON.parse(event.payload));
      return plans.some(plan => plan.motion?.handSign === 'paper') && (report.history = history);
    });
  } else {
    await wait(selection === 'space' ? 16000 : 9000);
    const index = await page.evaluate(() => logs.length);
    if (selection === 'face') sensing.data.emotion = 'happy'; else sensing.data.humanCount = 2;
    await freshSense(); await wait(selection === 'space' ? 2200 : 1200); await freshSense();
    report.proactive = await settled(index, selection === 'face' ? /happy|happier|smil|cheer|bright/i : /two|2|second|another|one more|pair|duo|joined|company/i);
    assert.ok((await storage())['sensing.remarks.pending'], 'No persisted speaking occasion');
    if (selection === 'face') {
      await say('correction');
      await until('durable correction', async () => (await storage())['sensing.remarks.correction'] === true);
      assert.equal((await storage())['sensing.remarks.pending'], undefined, 'Correction must cancel old comment');
    }
  }
  if (!taskJourney && !gameJourney && !(coordinationJourney && coordinationScenario !== 'baseline')) assert.deepEqual(await api(agentPath + '/state'), initial);
  report.status = await api(`${agentPath}/live/sessions/${handle}`);
  assert.equal(report.status.state, 'attached');
  report.passed = true;
} catch (error) { report.error = error.message; process.exitCode = 1; }
finally {
  clearInterval(heartbeat);
  if (agentPath) report.finalStorage = await storage().catch(error => ({error: error.message}));
  if (page) report.finalPlayback = await page.evaluate(() => {
    const p = window.playback, g = p?.gate, a = p?.audio || window.player;
    return {connection: window.pc?.connectionState, channel: window.dc?.readyState,
      cue: g?.cue, last: g?.last, voice: g?.voice, quiet: g?.quiet, received: g?.received,
      done: g ? [...g.done] : [], mediaTime: a?.currentTime, paused: a?.paused, muted: a?.muted,
      context: p?.context?.state, hidden: document.hidden};
  }).catch(() => null);
  if (page) await page.evaluate(() => {clearInterval(window.playbackTimer); window.playback?.close();}).catch(() => {});
  if (handle) {
    report.finalStatus = await api(`${agentPath}/live/sessions/${handle}`).catch(error => ({error: error.message}));
    report.ledger = await api(`${agentPath}/live/transcripts?sessionId=${handle}`).catch(error => ({error: error.message}));
    report.conversation = await api(agentPath + '/live/history').catch(error => ({error: error.message}));
    await api(`${agentPath}/live/sessions/${handle}`, 'DELETE').catch(() => {});
  }
  if (page) report.events = await page.evaluate(() => logs).catch(() => []);
  await browser.close();
  fs.mkdirSync(path.dirname(output), {recursive: true}); fs.writeFileSync(output, JSON.stringify(report, null, 2));
  console.log(report.passed ? 'Core Live journey passed.' : `Journey failed: ${report.error}`);
}
