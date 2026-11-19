import { test, expect } from './fixtures/offline-ui-assets.mjs';

// Real application, MySQL, SSE, audio element and Web Audio. Only provider
// transport/microphone are synthetic. This measures the browser route, not a speaker in a room.
for (const stopPending of [false, true]) test(`native Coordination audio precedes blocked backend${stopPending ? ' and Stop fences pending work' : ' across Continuous mode switches'}`, async ({page, context, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the enabled disposable MySQL acceptance runner.');
  const admin = {'X-Prometheus-Admin-Token': process.env.PROMETHEUS_ADMIN_TOKEN};
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const access = await (await request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`, {headers:admin, data:{agentTypeKeys:['core.coordination']}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents', {headers, data:{agentDefinitionKey:'core.coordination'}})).json();
  const path = `/demo/agents/${agent.id}`;
  const inference = async () => (await request.get('/__live-fixture/inference')).json();
  const history = async () => (await request.get(path + '/live/history', {headers})).json();
  await hardware(context);
  const outputRequests = [];
  page.on('request', r => {if (r.method() === 'POST' && /\/(speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech)/.test(r.url())) outputRequests.push(r.url());});
  try {
    await request.post('/__live-fixture/coordination-proposal', {data:{operation:'KEEP', agreement:null,
      reply:{speech:'Backend draft must never be spoken.', nonVerbal:{gesture:'ACKNOWLEDGE'}}}});
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    const startContinuous = async () => {
      await page.getByTestId('continuous-speech-tab').click(); await page.getByTestId('toggle-transcription').click();
      await expect(page.getByTestId('transcription-transport-status')).toHaveText('Transcription Connected');
      await expect(page.locator('#listen_status')).toHaveText('Listening');
      await expect.poll(() => page.evaluate(() => window.__nativeMic?.enabled && window.__nativeMic.readyState === 'live')).toBe(true);
    };
    if (!stopPending) await startContinuous();
    await page.getByTestId('gptlive-tab').click();
    const opening = page.waitForResponse(r => r.url().endsWith('/live/sessions') && r.status() === 201);
    await page.getByTestId('gptlive-start').click(); const session = await (await opening).json();
    expect(session.speechApprovalRequired).toBe(false); expect(session.committedSpeechOutput).toBe(false);
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    const provider = await (await request.get('/__live-fixture/latest')).json();
    expect(provider.instructions).not.toContain('Remain silent. Do not generate replies');
    const callsBefore = outputRequests.length, countBefore = (await inference()).count;
    await request.post('/__live-fixture/inference/hold');
    await request.post(`/__live-fixture/speak/${provider.providerId}`, {data:{speaker:'USER', text:'What can you do?', receipt:'native-user'}});
    await expect.poll(async () => (await inference()).count).toBeGreaterThan(countBefore);
    expect((await inference()).held).toBe(true);
    // Route a real PCM tone through the native HTML audio sink while inference is blocked.
    const audible = await page.evaluate(async () => {
      const audio = document.getElementById('gptlive_audio');
      const meter = __nativeContext.createAnalyser(), sink = __nativeContext.createGain(); sink.gain.value = 0;
      const source = __nativeContext.createMediaStreamSource(audio.captureStream()); source.connect(meter).connect(sink).connect(__nativeContext.destination);
      __nativeGain.gain.value = .15;
      const samples = new Float32Array(meter.fftSize); let peak = 0;
      for (let i = 0; i < 20; i++) {await new Promise(r => setTimeout(r, 20)); meter.getFloatTimeDomainData(samples); peak = Math.max(peak, ...samples.map(Math.abs));}
      __nativeGain.gain.value = 0; source.disconnect();
      return {peak, paused:audio.paused, muted:audio.muted, time:audio.currentTime};
    });
    expect(audible.peak).toBeGreaterThan(.02); expect(audible.paused).toBe(false); expect(audible.muted).toBe(false);
    expect((await inference()).held).toBe(true); expect(outputRequests).toHaveLength(callsBefore);
    if (stopPending) {
      // Stop must release the browser immediately even though server finalization is queued.
      await page.getByTestId('gptlive-stop').click();
      await expect.poll(() => page.evaluate(() => document.getElementById('gptlive_audio').srcObject)).toBeNull();
      expect(await page.evaluate(() => window.__nativeMic.readyState)).toBe('ended');
    }
    await request.post('/__live-fixture/inference/release');
    if (!stopPending) {
      await expect(page.getByTestId('gesture-value')).toHaveText('Acknowledgement');
      await expect(page.getByTestId('gptlive-history')).toContainText('What can you do?');
      await request.post(`/__live-fixture/speak/${provider.providerId}`, {data:{speaker:'USER', text:'What can you do?', receipt:'native-user'}});
      await page.waitForTimeout(2300); // Let the duplicate pass the longest user silence window.
      expect((await history()).filter(e => e.payload === 'What can you do?')).toHaveLength(1);
      const memory = Object.fromEntries((await (await request.get(path + '/storage', {headers})).json()).map(e => [e.key, JSON.parse(e.value)]));
      expect(memory['core.task.response']).toBeUndefined();
      expect(outputRequests).toHaveLength(callsBefore);
      await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
      await startContinuous();
      const completed = page.waitForResponse(r => r.url().includes(path + '/acknowledge') && r.request().method() === 'POST');
      await page.evaluate(() => {
        for (const event of [{type:'input_audio_buffer.committed', item_id:'after-live'},
          {type:'conversation.item.input_audio_transcription.completed', item_id:'after-live', transcript:'What can you do?'}])
          window.__nativeChannel.dispatchEvent(new MessageEvent('message', {data:JSON.stringify({event_id:crypto.randomUUID(), ...event})}));
      });
      const response = await completed; expect(response.ok()).toBe(true);
      expect((await response.json()).responseEvent.provenance).toBeFalsy();
      await expect.poll(() => outputRequests.length).toBeGreaterThan(callsBefore);
      await expect(page.locator('#listen_status')).toHaveText('Listening');
      await page.getByTestId('toggle-transcription').click();
    } else {
      await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
      const events = await history();
      expect(events.filter(e => e.payload === 'What can you do?').length).toBeLessThanOrEqual(1);
      expect(outputRequests).toHaveLength(callsBefore);
    }
    await info.attach('native-route-evidence', {contentType:'application/json', body:Buffer.from(JSON.stringify({audible, stopPending, outputRequests}))});
  } finally {
    await request.post('/__live-fixture/inference/clear'); await request.delete('/__live-fixture/coordination-proposal');
    await request.delete(path, {headers});
  }
});

for (const lostAck of [false, true]) test(`native Coordination final contribution ${lostAck ? 'keeps uncertainty after lost playback acknowledgement' : 'reaches Reflection only after observed playback'}`, async ({page, context, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the enabled disposable MySQL acceptance runner.');
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const access = await (await request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`, {headers:admin, data:{agentTypeKeys:['core.coordination']}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents', {headers, data:{agentDefinitionKey:'core.coordination'}})).json();
  const path = `/demo/agents/${agent.id}`;
  const memory = async () => Object.fromEntries((await (await request.get(path + '/storage', {headers})).json()).map(e => [e.key, JSON.parse(e.value)]));
  const playback = [], synthesis = [];
  page.on('request', r => {
    if (r.method() === 'POST' && r.url().includes('/playback-cues/')) playback.push(r.url());
    if (r.method() === 'POST' && /\/(speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech)/.test(r.url())) synthesis.push(r.url());
  });
  await hardware(context);
  try {
    if (lostAck) await page.route('**/playback-cues/*', route => route.abort());
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await page.getByTestId('gptlive-tab').click(); await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    expect((await request.post(path + '/acknowledge', {headers, data:{type:'obs.user_utterance', actor:'user', kind:'observation',
      payload:'Start one fixture observation contribution now'}})).ok()).toBe(true);
    await expect.poll(async () => (await memory())['task.response']?.status).toBe('CAPTURED');
    expect((await memory())['task.actions']).toBe(1);
    await expect(page.getByTestId('activity-label')).toHaveText('Finishing the response');
    expect(playback).toHaveLength(0);
    const provider = await (await request.get('/__live-fixture/latest')).json();
    const caption = provider.captions.map(JSON.parse).find(e => e.type === 'session.output_transcript.delta' && e.delta === 'First fixture observation.');
    expect(caption).toBeTruthy();
    await page.evaluate(caption => {window.__nativeReceive(caption); __nativeGain.gain.value = .15;}, caption);
    await page.waitForTimeout(700); await page.evaluate(() => {__nativeGain.gain.value = 0;});
    await expect.poll(() => playback.length).toBe(1);
    if (lostAck) {
      await page.waitForTimeout(1500);
      expect((await memory())['coordination.delivery'].status).toBe('PENDING');
      await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
      expect((await memory())['coordination.delivery'].status).toBe('UNCERTAIN');
    } else {
      await expect(page.getByTestId('activity-label')).toHaveText('Reflecting on the task');
      expect((await memory())['coordination.delivery'].status).toBe('PLAYBACK_CONFIRMED');
      await page.getByTestId('gptlive-stop').click();
    }
    expect(playback).toHaveLength(1); expect(synthesis).toHaveLength(0); expect((await memory())['task.actions']).toBe(1);
    const history = await (await request.get(path + '/live/history', {headers})).json();
    expect(history.filter(e => e.provenance?.origin === 'NATIVE' && e.payload.includes('First fixture observation.'))).toHaveLength(1);
    expect(history.filter(e => e.provenance?.origin === 'SYNTHESIZED')).toHaveLength(0);
    await info.attach('native-delivery-evidence', {contentType:'application/json', body:Buffer.from(JSON.stringify({lostAck, playback, synthesis, memory:await memory()}))});
  } finally {await request.delete(path, {headers});}
});

test('native Coordination interruption preserves prefixes through Stop reconnect and reload', async ({page, context, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the enabled disposable MySQL acceptance runner.');
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const access = await (await request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`, {headers:admin, data:{agentTypeKeys:['core.coordination']}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents', {headers, data:{agentDefinitionKey:'core.coordination'}})).json();
  const path = `/demo/agents/${agent.id}`;
  await hardware(context);
  const playback = [];
  page.on('request', r => {if (r.method() === 'POST' && /playback-cues|speech-synthesis|speech-approvals/.test(r.url())) playback.push(r.url());});
  const prefix = 'You can begin only after';
  let firstConnection = true;
  const connect = async () => {
    await page.goto(`/valerian/?agentId=${agent.id}`);
    if (firstConnection) {
      await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
      firstConnection = false;
    }
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await page.getByTestId('gptlive-tab').click();
  };
  const checkPrefix = async () => {
    await expect(page.getByTestId('gptlive-history').locator('.demo-bubble').filter({hasText:prefix})).toHaveCount(1);
    await expect(page.getByTestId('gptlive-history').locator('.demo-bubble').filter({hasText:prefix})).toContainText('Incomplete capture');
    await expect(page.locator('#messages .demo-bubble').filter({hasText:prefix})).toHaveCount(1);
    await expect(page.locator('#messages .demo-bubble').filter({hasText:prefix})).toContainText('Incomplete capture');
  };
  try {
    await connect(); await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    const provider = await (await request.get('/__live-fixture/latest')).json();
    const send = event => request.post(`/__live-fixture/event/${provider.providerId}`, {data:event});
    // Plausible loud non-speech/echo-like input alone cannot trigger a local RMS interruption.
    await page.evaluate(() => {__nativeGain.gain.value = .15; __nativeInputGain.gain.value = .25;});
    await page.waitForTimeout(400);
    expect(await page.evaluate(() => ({paused:document.getElementById('gptlive_audio').paused,
      muted:document.getElementById('gptlive_audio').muted, input:__nativeMic.enabled}))).toEqual({paused:false, muted:false, input:true});
    const before = await page.evaluate(async () => (await import('/live/diagnostics.js')).liveDiagnostics.snapshot());
    expect(before.sessions.at(-1).browser.some(e => e.phase === 'input_boundary' || e.phase === 'output_end')).toBe(false);
    await page.evaluate(() => {__nativeInputGain.gain.value = 0;});
    const output = {type:'session.output_transcript.delta', event_id:'interrupted-prefix', delta:prefix, start_ms:1000, end_ms:1800};
    await send(output); await page.evaluate(e => __nativeReceive(e), output);
    // A deliberate user speech boundary interrupts the open assistant capture.
    const input = {type:'session.input_transcript.delta', event_id:'intentional-interruption', delta:'Wait, please.', start_ms:1500, end_ms:1900};
    await send(input); await page.evaluate(e => {__nativeReceive(e); __nativeGain.gain.value = 0;}, input);
    await checkPrefix();
    // A replayed caption cannot create another bubble or reset newer playback evidence.
    await page.evaluate(e => __nativeReceive(e), output);
    await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
    await checkPrefix();
    const evidence = await page.evaluate(async () => (await import('/live/diagnostics.js')).liveDiagnostics.snapshot());
    expect(evidence.sessions.at(-1).browser.filter(e => e.phase === 'input_boundary')).toHaveLength(1);
    expect(evidence.sessions.at(-1).coverage.physicalAudibility).toBe('not_measured');
    await page.getByTestId('gptlive-start').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    await checkPrefix();
    expect(await page.evaluate(() => document.getElementById('gptlive_audio').muted)).toBe(false);
    await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
    await connect(); await checkPrefix();
    expect(playback).toHaveLength(0);
    const history = await (await request.get(path + '/live/history', {headers})).json();
    expect(history.filter(e => e.provenance?.segmentId && e.payload.includes(prefix))).toHaveLength(1);
    await info.attach('native-interruption-evidence', {contentType:'application/json', body:Buffer.from(JSON.stringify(evidence))});
  } finally {await request.delete(path, {headers});}
});

for (const inFlight of [false, true]) test(`native Coordination withdraws obsolete ${inFlight ? 'in-flight' : 'queued'} proposal after acceptance`, async ({page, context, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the enabled disposable MySQL acceptance runner.');
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const access = await (await request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`, {headers:admin, data:{agentTypeKeys:['core.coordination']}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents', {headers, data:{agentDefinitionKey:'core.coordination'}})).json();
  const path = `/demo/agents/${agent.id}`;
  const memory = async () => Object.fromEntries((await (await request.get(path + '/storage', {headers})).json()).map(e => [e.key, JSON.parse(e.value)]));
  const turn = async text => expect((await request.post(`/__live-fixture/speak/${providerId}`,
    {data:{speaker:'USER', text}})).ok()).toBe(true);
  const agreement = {goal:'One joke on rock', humanRole:'Show rock', agentRole:'Tell one joke', success:'The visitor judges the joke',
    questions:[], checks:[], dialogueLimit:1, reactions:{goal:'One joke on rock', maxActions:1, sessionBound:true, firstAction:'ON_CUE',
      rules:[{eventType:'obs.hand.sign', field:'sign', operator:'eq', value:'rock', action:'Tell one joke', effect:'ACT', minConfidence:0, samples:1, cooldownSeconds:3}]}};
  const outputs = []; let providerId;
  page.on('request', r => {if (r.method() === 'POST' && /playback-cues|speech-synthesis|speech-approvals/.test(r.url())) outputs.push(r.url());});
  await hardware(context);
  try {
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await page.getByTestId('gptlive-tab').click(); await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    providerId = (await (await request.get('/__live-fixture/latest')).json()).providerId;
    await request.post(`/__live-fixture/ack/${providerId}/hold`, {data:{type:inFlight ? 'session.commentary.append' : 'session.thinking.append'}});
    await request.post('/__live-fixture/coordination-proposal', {data:{operation:'PROPOSE', agreement, reply:{speech:'Ready to start?'}}});
    await turn('Propose one joke after rock, without starting.');
    await expect.poll(async () => (await (await request.get(`/__live-fixture/ack/${providerId}`)).json()).waiting).toBe(true);
    const oldId = (await memory())['core.task.response'].id;
    if (inFlight) await page.evaluate(() => {__nativeGain.gain.value = .15;});
    await request.post('/__live-fixture/coordination-proposal', {data:{operation:'START', agreement:null, reply:{speech:'Waiting for rock.'}}});
    await turn('Start now');
    await expect.poll(async () => (await memory())['core.task.response']?.id).not.toBe(oldId);
    const accepted = await memory(); const currentId = accepted['core.task.response'].id;
    expect(currentId).not.toBe(oldId); expect(accepted['task.actions']).toBe(0);
    expect(accepted['coordination.agreement']).toEqual(agreement);
    await request.post(`/__live-fixture/ack/${providerId}/release`);
    const commands = async () => (await (await request.get('/__live-fixture/latest')).json()).commands.map(JSON.parse);
    await expect.poll(async () => (await commands()).filter(c => c.type === 'session.commentary.append' && c.content.includes(currentId)).length).toBe(1);
    const sent = await commands();
    expect(sent.filter(c => c.type === 'session.commentary.append' && c.content.includes(oldId))).toHaveLength(inFlight ? 1 : 0);
    expect(sent.filter(c => c.content?.includes(oldId) && c.content.includes('is withdrawn'))).toHaveLength(1);
    expect(await page.evaluate(() => ({paused:document.getElementById('gptlive_audio').paused, muted:document.getElementById('gptlive_audio').muted})))
      .toEqual({paused:false, muted:false});
    expect(outputs).toHaveLength(0); expect((await memory())['task.actions']).toBe(0);
    await expect(page.getByTestId('gptlive-history')).toContainText('Start now');
    await page.screenshot({path:info.outputPath(`accepted-${inFlight ? 'in-flight' : 'queued'}.png`), fullPage:true});
    await info.attach('task-announcement-evidence', {contentType:'application/json', body:Buffer.from(JSON.stringify({inFlight, oldId, currentId, commands:sent, memory:await memory()}))});
    await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
  } finally {
    if (providerId) await request.post(`/__live-fixture/ack/${providerId}/release`);
    await request.delete('/__live-fixture/coordination-proposal');
    await request.delete(path, {headers});
  }
});

async function hardware(context) {
  await context.route('https://transcription.invalid/offer', r => r.fulfill({contentType:'application/sdp', body:'synthetic-answer'}));
  await context.addInitScript(() => {
    window.__nativeContext = new AudioContext();
    const remote = __nativeContext.createMediaStreamDestination(), oscillator = __nativeContext.createOscillator();
    window.__nativeGain = __nativeContext.createGain(); __nativeGain.gain.value = 0;
    oscillator.connect(__nativeGain).connect(remote); oscillator.start();
    Object.defineProperty(navigator, 'mediaDevices', {configurable:true, value:{
      async getUserMedia() {await __nativeContext.resume(); const input = __nativeContext.createMediaStreamDestination();
        window.__nativeMic = input.stream.getAudioTracks()[0];
        const noise = __nativeContext.createOscillator(); noise.frequency.value = 170;
        window.__nativeInputGain = __nativeContext.createGain(); __nativeInputGain.gain.value = 0;
        noise.connect(__nativeInputGain).connect(input); noise.start(); return input.stream;},
      async enumerateDevices() {return [];}, getSupportedConstraints() {return {};}, addEventListener() {}, removeEventListener() {}
    }});
    window.RTCPeerConnection = class extends EventTarget {
      constructor() {super(); this.iceGatheringState = 'complete'; this.connectionState = 'new'; this.senders = [];}
      createDataChannel() {const channel = new EventTarget(); channel.readyState = 'connecting'; channel.send = () => {}; channel.close = () => {};
        window.__nativeChannel = this.channel = channel; window.__nativeReceive = event => channel.onmessage?.({data:JSON.stringify(event)}); return channel;}
      addTrack(track) {this.senders.push({track});} getSenders() {return this.senders;}
      async createOffer() {return {type:'offer', sdp:'v=0 synthetic-offer'};} async setLocalDescription(value) {this.localDescription = value;}
      async setRemoteDescription(value) {
        this.connectionState = 'connected'; this.channel.readyState = 'open';
        this.channel.onmessage?.({data:'{"type":"session.started"}'});
        queueMicrotask(() => this.channel.dispatchEvent(new Event('open')));
        if (value.sdp.startsWith('v=0')) this.ontrack?.({streams:[remote.stream], track:remote.stream.getAudioTracks()[0]});
      }
      async getStats() {return new Map();} close() {this.connectionState = 'closed';}
    };
  });
}
