// Explicitly paid acceptance: real local app/database, real control inference and Live speech.
// Synthetic visitor audio and manual sensing; no claim about physical playback or robot motion.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';

const [permission, output] = process.argv.slice(2);
const base = process.env.PROMETHEUS_BASE_URL;
if (permission !== '--allow-paid-provider' || !output || !base || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname))
  throw Error('Run through run_acceptance.py --paid-gala with a local disposable database.');
const key = process.env.OPENAI_API_KEY, token = process.env.PROMETHEUS_ADMIN_TOKEN;
if (!key || !token) throw Error('Missing provider/admin environment credentials');
const report = {startedAt: new Date().toISOString(), rounds: [], passed: false,
  limitations: 'Synthetic audio and camera input. Captions/received media do not prove physical audibility or movement.'};
const clips = {rps: 'Ich möchte Schere Stein Papier spielen.', guess: 'Personenraten bitte.',
  again: 'Nochmal bitte.', question: 'Ist die Person eine Frau?', giveUp: 'Ich gebe auf. Wer war es?',
  info: 'Was ist das SIRA Lab?'};
const audio = {};
const cache = 'target/gala-provider-audio'; fs.mkdirSync(cache, {recursive: true});
for (const [name, input] of Object.entries(clips)) {
  const body = JSON.stringify({model: 'gpt-4o-mini-tts', voice: 'alloy', input, response_format: 'pcm'});
  const file = path.join(cache, createHash('sha256').update(body).digest('hex') + '.pcm');
  if (!fs.existsSync(file)) {
    console.log(`Preparing synthetic audio: ${name}`);
    const response = await fetch('https://api.openai.com/v1/audio/speech', {method: 'POST',
      headers: {Authorization: `Bearer ${key}`, 'Content-Type': 'application/json'}, body, signal: AbortSignal.timeout(120000)});
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
  return page.evaluate(index => logs.slice(index).filter(e => e.type === 'session.output_transcript.delta').map(e => e.delta || '').join(''), index);
}
async function settled(index) {
  await until('assistant output', async () => (await captionsFrom(index)).trim());
  // This is a test pacing heuristic, not a production audio-completion contract.
  await page.waitForFunction(() => performance.now() - logs.findLast(e => e.type === 'session.output_transcript.delta').at > 3500,
    {}, {timeout: 30000});
  return captionsFrom(index);
}
try {
  const admin = {'X-Prometheus-Admin-Token': token};
  const code = ('G' + Math.random().toString(36).slice(2, 6)).toUpperCase();
  const access = await api('/admin/access-codes', 'POST', {code, enabled: true}, admin);
  await api(`/admin/access-codes/${access.id}/agent-types`, 'PUT', {agentTypeKeys: ['usecases.gala_dinner_reception']}, admin);
  scoped = {'X-Prometheus-Access-Code': code};
  const agent = await api('/demo/agents', 'POST', {agentDefinitionKey: 'usecases.gala_dinner_reception', embodiment: 'ROBOT'});
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
  const offer = await page.evaluate(async () => {
    window.logs = []; window.mark = (type, fields = {}) => logs.push({type, ...fields, at: performance.now()});
    window.ac = new AudioContext({sampleRate: 24000}); await ac.resume();
    window.destination = ac.createMediaStreamDestination();
    const oscillator = ac.createOscillator(), gain = ac.createGain(); gain.gain.value = 0;
    oscillator.connect(gain).connect(destination); oscillator.start();
    window.pc = new RTCPeerConnection(); destination.stream.getTracks().forEach(track => pc.addTrack(track, destination.stream));
    const player = document.createElement('audio'); player.autoplay = true; document.body.append(player);
    pc.ontrack = event => {player.srcObject = event.streams[0]; player.play().catch(() => {});};
    window.dc = pc.createDataChannel('oai-events'); dc.onmessage = event => {
      const message = JSON.parse(event.data);
      if (!message.type.includes('audio.')) mark(message.type, message.type === 'session.started' ? {} : message);
    };
    await pc.setLocalDescription(await pc.createOffer());
    await new Promise(resolve => {
      if (pc.iceGatheringState === 'complete') resolve();
      else {pc.onicegatheringstatechange = () => {if (pc.iceGatheringState === 'complete') resolve();}; setTimeout(resolve, 2000);}
    });
    return pc.localDescription.sdp;
  });
  const session = await api(agentPath + '/live/sessions', 'POST', {sdp: offer, voice: 'willow'}); handle = session.handle;
  await page.evaluate(sdp => pc.setRemoteDescription({type: 'answer', sdp}), session.sdp);
  await page.waitForFunction(() => logs.some(e => e.type === 'session.started'), {}, {timeout: 20000});
  await api(`${agentPath}/live/sessions/${handle}/input?muted=false`, 'POST');
  await settled(0);
  for (const game of ['RPS', 'GUESS']) {
    for (let round = 0; round < 2; round++) {
      assert.equal((await storage())['gala.mode'] || 'RECEPTION', 'RECEPTION');
      const entry = {game, round, before: await api(agentPath + '/state')}; report.rounds.push(entry);
      const start = await page.evaluate(() => logs.length);
      await say(round ? 'again' : game.toLowerCase());
      const active = await until(game + ' backend start', async () => {const s = await storage(); return s['gala.mode'] === game && s;});
      // Allow confirmed-context delivery after any automatic early response. This is happy-path pacing,
      // not evidence that the provider waits for readiness (the known prompt-only limitation).
      await wait(5000);
      entry.roundId = active['gala.round_id']; entry.introduction = await settled(start);
      const resultStart = await page.evaluate(() => logs.length);
      if (game === 'RPS') {
        entry.robotSign = active['gala.sign']; entry.visitorSign = 'paper';
        await observe('obs.hand.sign', {sign: 'paper', confidence: 1, source: 'manual',
          captureId: active['gala.rps_capture_id'], ts: new Date().toISOString()});
      } else {
        entry.person = active['gala.person'];
        await say('question'); entry.answer = await settled(resultStart);
        entry.revealStart = await page.evaluate(() => logs.length); await say('giveUp');
      }
      await until('reception after result', async () => (await storage())['gala.mode'] === 'RECEPTION');
      entry.result = await settled(entry.revealStart ?? resultStart);
      entry.after = await api(agentPath + '/state');
      console.log(`Completed ${game} round ${round + 1}`);
    }
  }
  assert.equal(new Set(report.rounds.map(r => r.roundId)).size, 4);
  assert.notEqual(report.rounds[2].person, report.rounds[3].person);
  for (const entry of report.rounds) {
    if (entry.game === 'GUESS') {
      assert.ok(entry.result.includes(entry.person), `Missing reveal: ${entry.result}`);
      assert.match(entry.answer.trim(), entry.person === 'Marie Curie' ? /^Ja\b/i : /^Nein\b/i, 'Incorrect fact-card answer');
      assert.doesNotMatch(entry.introduction, /(?:denk dir|denken Sie sich|such dir|wähle).{0,30}(?:Person|jemanden)/i, 'Reversed guessing roles');
    }
    else assert.match(entry.result, /gewonn|gewinn|unentschieden|verlor|verlier|schlägt|schlaegt|geht an (?:mich|dich)/i, 'Missing RPS resolution');
    assert.doesNotMatch(entry.result, /(?:möchtest|willst|lust).{0,50}(?:nochmal|noch eine|weiterspielen)/i, 'Unrequested replay invitation');
  }
  report.passed = true;
} catch (error) { report.error = error.message; process.exitCode = 1; }
finally {
  clearInterval(heartbeat);
  if (handle) {
    report.ledger = await api(`${agentPath}/live/transcripts?sessionId=${handle}`).catch(error => ({error: error.message}));
    report.conversation = await api(agentPath + '/live/history').catch(error => ({error: error.message}));
    await api(`${agentPath}/live/sessions/${handle}`, 'DELETE').catch(() => {});
  }
  if (page) report.events = await page.evaluate(() => logs).catch(() => []);
  await browser.close();
  fs.mkdirSync(path.dirname(output), {recursive: true}); fs.writeFileSync(output, JSON.stringify(report, null, 2));
  console.log(report.passed ? 'Both real-provider journeys passed.' : `Journey failed: ${report.error}`);
}
