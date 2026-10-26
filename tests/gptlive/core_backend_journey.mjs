// Paid real-app acceptance. Test pacing is not a production playback-completion signal.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
const [permission, output] = process.argv.slice(2);
const selection = process.argv.find(v => v.startsWith('--agent='))?.split('=')[1];
const definitions = {live: 'core.live_multimodal', face: 'core.facial_expression_sensitivity',
  social: 'core.social_context_sensitivity', space: 'core.generic_multimodal_behaviour_at_social_space',
  task: 'core.generic_multimodal_behaviour', 'space-task': 'core.generic_multimodal_behaviour_at_social_space'};
const taskJourney = selection === 'task' || selection === 'space-task';
const connectionOnly = true;
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
const audio = {};
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
try {
  const admin = {'X-Prometheus-Admin-Token': token};
  const code = ('G' + Math.random().toString(36).slice(2, 6)).toUpperCase();
  const access = await api('/admin/access-codes', 'POST', {code, enabled: true}, admin);
  await api(`/admin/access-codes/${access.id}/agent-types`, 'PUT', {agentTypeKeys: [definitions[selection]]}, admin);
  scoped = {'X-Prometheus-Access-Code': code};
  const agent = await api('/demo/agents', 'POST', {agentDefinitionKey: definitions[selection]});
  agentPath = '/demo/agents/' + agent.id; report.agentId = agent.id;
  await freshSense();
  let heartbeatBusy = false;
  heartbeat = setInterval(async () => {
    if (heartbeatBusy) return;
    heartbeatBusy = true;
    try {
      await freshSense();
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
  await page.waitForFunction(() => logs.some(e => e.type === 'session.started'), {}, {timeout: 20000});
  await api(`${agentPath}/live/sessions/${handle}/input?muted=false`, 'POST');
  await page.evaluate(handle => startPlaybackPoll(handle), handle);
  // LiveMultimodal intentionally has no unsolicited opening. Start with a user turn.
  const initial = await api(agentPath + '/state');
  for (const [name, expected] of [['info', /SIRA|social|responsib|research|agent/i], ['sense', selection === 'face' ? /neutral|calm|relax/i : /one|1|single/i]]) {
    const index = await page.evaluate(() => logs.length);
    await say(name);
    const text = await settled(index, expected);
    if (name === 'info') assert.doesNotMatch(text, /Service Innovation and Robotics/i, 'Invented SIRA expansion');
    report.turns.push({name, text});
    assert.deepEqual(await api(agentPath + '/state'), initial, 'Ordinary requests must retain the interaction state');
  }
  if (taskJourney) {
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
  if (!taskJourney) assert.deepEqual(await api(agentPath + '/state'), initial);
  report.status = await api(`${agentPath}/live/sessions/${handle}`);
  assert.equal(report.status.state, 'attached');
  report.passed = true;
} catch (error) { report.error = error.message; process.exitCode = 1; }
finally {
  clearInterval(heartbeat);
  if (agentPath) report.finalStorage = await storage().catch(error => ({error: error.message}));
  if (page) report.finalPlayback = await page.evaluate(() => {
    const p = window.playback, g = p?.gate, a = p?.audio || window.player;
    return {cue: g?.cue, last: g?.last, voice: g?.voice, quiet: g?.quiet, received: g?.received,
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
