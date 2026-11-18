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

async function hardware(context) {
  await context.route('https://transcription.invalid/offer', r => r.fulfill({contentType:'application/sdp', body:'synthetic-answer'}));
  await context.addInitScript(() => {
    window.__nativeContext = new AudioContext();
    const remote = __nativeContext.createMediaStreamDestination(), oscillator = __nativeContext.createOscillator();
    window.__nativeGain = __nativeContext.createGain(); __nativeGain.gain.value = 0;
    oscillator.connect(__nativeGain).connect(remote); oscillator.start();
    Object.defineProperty(navigator, 'mediaDevices', {configurable:true, value:{
      async getUserMedia() {await __nativeContext.resume(); const input = __nativeContext.createMediaStreamDestination();
        window.__nativeMic = input.stream.getAudioTracks()[0]; return input.stream;},
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
