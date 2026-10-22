// Opt-in paid-provider probe. Synthetic audio, real Live speech; no application/database.
import fs from 'node:fs';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';

const args = process.argv.slice(2);
if (!args.includes('--allow-paid-provider') || args.length !== 3)
  throw Error('Usage: node tests/gptlive/gala_provider_trial.mjs --allow-paid-provider fixture.json output.json');
const [fixturePath, outputPath] = args.filter(arg => arg !== '--allow-paid-provider');
const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
const key = process.env.OPENAI_API_KEY;
if (!key) throw Error('Set OPENAI_API_KEY in the environment, never in command arguments');
const headers = {Authorization: key.startsWith('Bearer ') ? key : `Bearer ${key}`, 'Content-Type': 'application/json'};
const clips = {
  first: 'Ich möchte Schere Stein Papier spielen.',
  replay: 'Wollen wir das nochmal spielen?',
  guess: 'Personenraten bitte.',
  questionFirst: 'Ist die Person männlich',
  questionLast: 'oder weiblich?',
  giveUp: 'Ich gebe auf. Wer war es?'
};
const cache = 'target/gala-provider-audio'; fs.mkdirSync(cache, {recursive: true});
fs.mkdirSync(path.dirname(outputPath), {recursive: true});
const pcm = {};
for (const [name, input] of Object.entries(clips)) {
  const body = JSON.stringify({model: 'gpt-4o-mini-tts', voice: 'alloy', input, response_format: 'pcm'});
  const file = path.join(cache, createHash('sha256').update(body).digest('hex') + '.pcm');
  if (!fs.existsSync(file)) {
    const response = await fetch('https://api.openai.com/v1/audio/speech', {
      method: 'POST', headers, body, signal: AbortSignal.timeout(60000)
    });
    if (!response.ok) throw Error(`Synthetic speech generation failed: HTTP ${response.status}`);
    fs.writeFileSync(file, Buffer.from(await response.arrayBuffer()));
  }
  pcm[name] = [...fs.readFileSync(file)];
}

const browser = await chromium.launch({headless: true, args: ['--autoplay-policy=no-user-gesture-required']});
let providerId, page;
const report = {fixture: fixturePath, startedAt: new Date().toISOString(), confirmationDelayMs: 5000,
  withinQuestionPauseMs: 1200, events: [], limitation: 'Synthetic input and provider captions; not acoustic or backend integration acceptance.'};
try {
  page = await browser.newPage(); await page.goto('about:blank');
  const offer = await page.evaluate(async () => {
    window.logs = []; window.epoch = performance.now(); window.sequence = 0;
    window.mark = (type, fields = {}) => logs.push({type, ...fields, ms: performance.now() - epoch});
    window.ac = new AudioContext({sampleRate: 24000}); await ac.resume();
    window.destination = ac.createMediaStreamDestination();
    const oscillator = ac.createOscillator(), gain = ac.createGain(); gain.gain.value = 0;
    oscillator.connect(gain).connect(destination); oscillator.start();
    window.pc = new RTCPeerConnection(); destination.stream.getTracks().forEach(track => pc.addTrack(track, destination.stream));
    const audio = document.createElement('audio'); audio.autoplay = true; document.body.append(audio);
    pc.ontrack = event => {audio.srcObject = event.streams[0]; audio.play().catch(() => {});};
    window.dc = pc.createDataChannel('oai-events');
    dc.onmessage = event => {
      const message = JSON.parse(event.data);
      if (!message.type.includes('audio.')) mark(message.type, message.type === 'session.started' ? {} : message);
    };
    window.send = (type, fields) => {
      const id = `gala_trial_${++sequence}`; dc.send(JSON.stringify({type, event_id: id, ...fields})); return id;
    };
    await pc.setLocalDescription(await pc.createOffer());
    await new Promise(resolve => {
      if (pc.iceGatheringState === 'complete') resolve();
      else {pc.onicegatheringstatechange = () => {if (pc.iceGatheringState === 'complete') resolve();}; setTimeout(resolve, 2000);}
    });
    return pc.localDescription.sdp;
  });
  const response = await fetch('https://api.openai.com/v1/live/sessions', {
    method: 'POST', headers, signal: AbortSignal.timeout(30000), body: JSON.stringify({
      session: {model: 'gpt-live-1', store: false, instructions: fixture.instructions, input: fixture.input,
        delegation: {type: 'client'}, audio: {output: {voice: 'marin'}}}, transport: {type: 'webrtc', sdp: offer}
    })
  });
  if (!response.ok) throw Error(`Live creation failed: HTTP ${response.status}`);
  const created = await response.json(); providerId = created.session.id;
  await page.evaluate(sdp => pc.setRemoteDescription({type: 'answer', sdp}), created.transport.sdp);
  await page.waitForFunction(() => logs.some(event => event.type === 'session.started'), {}, {timeout: 20000});
  async function audio(name) {
    await page.evaluate(async ({name, bytes, text}) => {
      const view = new DataView(Uint8Array.from(bytes).buffer), samples = new Float32Array(bytes.length / 2);
      for (let i = 0; i < samples.length; i++) samples[i] = view.getInt16(i * 2, true) / 32768;
      const buffer = ac.createBuffer(1, samples.length, 24000); buffer.copyToChannel(samples, 0);
      const source = ac.createBufferSource(); source.buffer = buffer; source.connect(destination);
      mark('trial.input.start', {name, text});
      await new Promise(resolve => {source.onended = resolve; source.start();});
      mark('trial.input.end', {name});
    }, {name, bytes: pcm[name], text: clips[name]});
  }
  async function context(name) {
    console.log(`Context: ${name}`);
    await page.evaluate(name => mark('trial.confirmation.start', {name}), name);
    for (const command of fixture[name]) {
      let content = command.content;
      if (command.evidenceType && command.expiresAt) {
        // Replay a fresh synthetic observation with the original relative timestamp/TTL.
        const shift = Date.now() + 15000 - Date.parse(command.expiresAt);
        content = content.replace(/\d{4}-\d\d-\d\dT[\d:.]+Z/g, value => new Date(Date.parse(value) + shift).toISOString());
      }
      const id = await page.evaluate(({type, content, name}) => {
        const id = send(type, {delegation_id: null, content}); mark('trial.context.sent', {name, id, kind: type, content}); return id;
      }, {type: command.type, content, name});
      await page.waitForFunction(id => logs.some(event => event.client_event_id === id), id, {timeout: 15000});
      const ack = await page.evaluate(id => logs.find(event => event.client_event_id === id), id);
      if (ack.type !== command.type + 'ed') throw Error(`Unexpected append acknowledgement: ${ack.type}`);
    }
    await page.evaluate(name => mark('trial.confirmation.complete', {name}), name);
  }
  await page.waitForTimeout(8500);
  for (const [request, start, end] of [['first', 'rpsStart', 'rpsEnd'], ['replay', 'rpsReplay', 'rpsReplayEnd']]) {
    await audio(request); await page.waitForTimeout(5000); await context(start);
    await page.waitForTimeout(8500); await context(end); await page.waitForTimeout(8500);
  }
  await audio('guess'); await page.waitForTimeout(5000); await context('guessStart'); await page.waitForTimeout(8500);
  await audio('questionFirst'); await page.waitForTimeout(1200); await audio('questionLast'); await page.waitForTimeout(8500);
  await audio('giveUp'); await page.waitForTimeout(5000); await context('guessEnd'); await page.waitForTimeout(10000);
  await page.evaluate(() => send('session.close', {}));
  await page.waitForFunction(() => logs.some(event => event.type === 'session.closed'), {}, {timeout: 15000});
  report.closed = true;
} catch (error) {
  report.error = `${error.name}: ${error.message}`; process.exitCode = 1;
} finally {
  if (page) report.events = await page.evaluate(() => logs).catch(() => []);
  if (providerId) {
    try { await fetch(`https://api.openai.com/v1/live/sessions/${providerId}/hangup`, {method: 'POST', headers, signal: AbortSignal.timeout(10000)}); }
    catch { report.hangupUnconfirmed = true; }
  }
  await browser.close(); fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({output: outputPath, closed: report.closed, error: report.error}));
}
