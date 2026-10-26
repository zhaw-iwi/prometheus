// Paid real-app acceptance. Test pacing is not a production playback-completion signal.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
const [permission, output] = process.argv.slice(2);
const selection = process.argv.find(v => v.startsWith('--agent='))?.split('=')[1];
const connectionOnly = true;
const base = process.env.PROMETHEUS_BASE_URL;
if (permission !== '--allow-paid-provider' || !output || selection !== 'live' || !base
    || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname))
  throw Error('Use run_acceptance.py --paid-core live with its disposable local database.');
const key = process.env.OPENAI_API_KEY, token = process.env.PROMETHEUS_ADMIN_TOKEN;
if (!key || !token) throw Error('Missing provider/admin environment credentials');
const report = {startedAt: new Date().toISOString(), selection, passed: false, turns: [],
  limitations: 'Synthetic audio/sensors; captions and received media do not prove physical audibility or movement.'};
const clips = {info: 'Hello Valerian. What is the SIRA Lab?',
  sense: 'How many people can you currently see?', gesture: 'Please show me the paper hand sign.'};
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
  await api(`/admin/access-codes/${access.id}/agent-types`, 'PUT', {agentTypeKeys: ['core.live_multimodal']}, admin);
  scoped = {'X-Prometheus-Access-Code': code};
  const agent = await api('/demo/agents', 'POST', {agentDefinitionKey: 'core.live_multimodal'});
  agentPath = '/demo/agents/' + agent.id; report.agentId = agent.id;
  await observe('obs.social.grouping', {humanCount: 1, ts: new Date().toISOString()});
  let heartbeatBusy = false;
  heartbeat = setInterval(async () => {
    if (heartbeatBusy) return;
    heartbeatBusy = true;
    try {
      await observe('obs.social.grouping', {humanCount: 1, ts: new Date().toISOString()});
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
  for (const [name, expected] of [['info', /SIRA|social|responsib|research|agent/i], ['sense', /one|1|single/i]]) {
    const index = await page.evaluate(() => logs.length);
    await say(name);
    const text = await settled(index, expected);
    report.turns.push({name, text});
    assert.deepEqual(await api(agentPath + '/state'), initial, 'Ordinary requests must retain the interaction state');
  }
  // The existing voice contract explicitly stays silent for gesture-only requests.
  // Require a persisted executable plan, not a redundant spoken acknowledgement.
  await say('gesture');
  await until('persisted paper hand-sign plan', async () => {
    const history = await api(agentPath + '/eventhistory');
    const plans = history.filter(event => event.type === 'resp.behaviour_plan')
      .map(event => JSON.parse(event.payload));
    return plans.some(plan => plan.motion?.handSign === 'paper') && (report.history = history);
  });
  assert.deepEqual(await api(agentPath + '/state'), initial);
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
