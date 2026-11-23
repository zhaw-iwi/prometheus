import {hardware} from './fixtures/native-hardware.mjs';
import {test, expect} from './fixtures/offline-ui-assets.mjs';

for (const type of ['core.coordination', 'core.live_multimodal']) test(`direct commands render once through Text and persisted SSE (${type})`, async ({page, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the disposable local MySQL acceptance runner.');
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access = await (await request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`, {headers:admin, data:{agentTypeKeys:[type]}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents', {headers, data:{agentDefinitionKey:type}})).json();
  const path = `/demo/agents/${agent.id}`;
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  const memory = async () => Object.fromEntries((await (await request.get(path + '/storage', {headers})).json()).map(e => [e.key, JSON.parse(e.value)]));
  const plan = async () => {
    const events = await (await request.get(path + '/eventhistory', {headers})).json();
    return events.filter(e => e.type === 'resp.behaviour_plan').map(e => ({id:e.id, ...JSON.parse(e.payload)}));
  };
  const interpret = async (intent, kind, value, silent = true) => {
    const command = {intent, kind, value, silent};
    const data = type === 'core.coordination' ? {operation:'KEEP', agreement:null, reply:{}, command} : {command};
    await request.post('/__live-fixture/direct-command', {data});
  };
  const say = async text => {
    await page.getByTestId('text-input').fill(text);
    await page.getByTestId('send-text').click();
  };
  try {
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
    await expect(page.getByTestId('send-text')).toBeEnabled();
    await page.getByRole('button', {name:'Robot', exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    await interpret('ACTION', 'HAND_SIGN', 'rock'); await say('Make a rock sign without speaking');
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await expect(page.locator('#embodiment_summary')).toContainText('rock');
    await expect.poll(async () => (await memory())['command.handledTurns']?.length).toBe(1);
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('requested-rock.png')});
    await interpret('ACTION', 'GESTURE', 'OPEN_QUESTION'); await say('Show me a question gesture');
    await expect(page.locator('#embodiment_summary')).toContainText('question');
    await expect.poll(async () => (await memory())['command.handledTurns']?.length).toBe(2);
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('requested-question.png')});
    await say('Show me that question gesture again');
    await expect.poll(async () => (await memory())['command.handledTurns']?.length).toBe(3);
    const before = await plan();
    expect(before.filter(p => p.motion?.handSign === 'rock')).toHaveLength(1);
    expect(before.filter(p => p.nonVerbal?.gesture === 'OPEN_QUESTION')).toHaveLength(2);
    expect(before.filter(p => p.motion?.handSign || p.nonVerbal?.gesture === 'OPEN_QUESTION').every(p => !p.speech)).toBe(true);
    await interpret('UNSUPPORTED', null, null); await say('Walk over here');
    await expect.poll(async () => (await memory())['command.lastResult']?.status).toBe('UNSUPPORTED');
    expect((await plan()).filter(p => p.motion?.handSign || p.nonVerbal?.gesture === 'OPEN_QUESTION')).toHaveLength(3);
    await page.reload();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
    expect((await memory())['command.handledTurns']).toHaveLength(4);
    expect((await memory())['coordination.agreement']).toBeUndefined();
    expect(errors).toEqual([]);
    await info.attach('direct-command-events', {contentType:'application/json', body:Buffer.from(JSON.stringify(await plan()))});
  } finally {
    await request.delete('/__live-fixture/direct-command');
    await request.delete(path, {headers});
  }
});

for (const type of ['core.coordination', 'core.live_multimodal']) test(`direct command mode parity with native result identity (${type})`, async ({page, context, request}, info) => {
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true' || !process.env.PROMETHEUS_ADMIN_TOKEN,
    'Use the disposable local MySQL acceptance runner.');
  const code = Math.random().toString(36).slice(2,7).toUpperCase();
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access = await (await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:[type]}});
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await (await request.post('/demo/agents',{headers,data:{agentDefinitionKey:type}})).json();
  const path = `/demo/agents/${agent.id}`;
  const memory = async () => Object.fromEntries((await (await request.get(path+'/storage',{headers})).json()).map(e=>[e.key,JSON.parse(e.value)]));
  const errors=[], speech=[]; page.on('pageerror',e=>errors.push(e.message));
  page.on('request',r=>{if(r.method()==='POST' && /\/(speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech)/.test(r.url()))speech.push(r.url());});
  await hardware(context);
  const interpret = async (kind,value,silent=false) => {
    const command={intent:'ACTION',kind,value,silent};
    await request.post('/__live-fixture/direct-command',{data:type==='core.coordination'?{operation:'KEEP',agreement:null,reply:{},command}:{command}});
  };
  const continuous = async () => {
    await page.getByTestId('continuous-speech-tab').click(); await page.getByTestId('toggle-transcription').click();
    await expect(page.getByTestId('transcription-transport-status')).toHaveText('Transcription Connected');
    await expect(page.locator('#listen_status')).toHaveText('Listening');
  };
  const transcript = async (item,text) => page.evaluate(({item,text})=>{
    for(const event of [{type:'input_audio_buffer.committed',item_id:item},
      {type:'conversation.item.input_audio_transcription.completed',item_id:item,transcript:text}])
      window.__nativeChannel.dispatchEvent(new MessageEvent('message',{data:JSON.stringify({event_id:crypto.randomUUID(),...event})}));
  },{item,text});
  const stage=page.getByTestId('embodiment-stage');
  try {
    await page.emulateMedia({reducedMotion:'reduce'}); // Stable representative WebGL poses for visual review.
    await page.goto(`/valerian/?agentId=${agent.id}`);
    await page.getByTestId('access-code-input').fill(code); await page.getByTestId('submit-access-code').click();
    await page.getByRole('button',{name:'Robot',exact:true}).click(); await expect(stage).toHaveAttribute('data-ready','true');
    await interpret('HAND_SIGN','rock'); await continuous(); await transcript('continuous-rock','Make a rock sign');
    await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(1);
    await expect(stage).toHaveAttribute('data-hand-sign','rock');
    await expect(page.locator('#listen_status')).toHaveText('Listening');
    await page.getByTestId('gptlive-tab').click();
    await page.getByTestId('gptlive-start').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    const provider=await (await request.get('/__live-fixture/latest')).json();
    const before=speech.length;
    const native=async(text,receipt)=>request.post(`/__live-fixture/speak/${provider.providerId}`,{data:{speaker:'USER',text,receipt}});
    if(type==='core.live_multimodal'){
      await request.post('/__live-fixture/direct-command',{data:{nonVerbal:{gesture:'EXPLAIN'},motion:{handSign:'paper'},
        command:{intent:null,kind:null,value:null,silent:false}}});
      expect((await native('What small activities can we do together?','malformed-optional-command')).ok()).toBe(true);
      await expect(page.getByTestId('gptlive-history')).toContainText('What small activities can we do together?');
      expect((await memory())['command.handledTurns']).toHaveLength(1);
      await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
      await expect(stage).toHaveAttribute('data-hand-sign','rock');
    }
    await interpret('HAND_SIGN','scissor');
    await native('Show me scissors','command-scissor');
    await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(2);
    await expect(stage).toHaveAttribute('data-hand-sign','scissor');
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('native-scissors-light.png')});
    await expect.poll(async()=>JSON.stringify(await (await request.get('/__live-fixture/latest')).json())).toContain('COMMAND RESULT for user turn');
    const replay=(await (await request.get('/__live-fixture/latest')).json()).captions
      .map(e=>typeof e==='string'?JSON.parse(e):e).find(e=>e.event_id==='command-scissor');
    expect(replay).toBeTruthy();
    expect((await request.post(`/__live-fixture/event/${provider.providerId}`,{data:replay})).ok()).toBe(true);
    await page.waitForTimeout(2300);
    expect((await memory())['command.handledTurns']).toHaveLength(2);
    await interpret('GESTURE','OPEN_QUESTION',true); await native('Show a question gesture without speaking','command-question');
    await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(3);
    await expect(stage).toHaveAttribute('data-gesture','OPEN_QUESTION');
    await page.evaluate(()=>setTheme('dark'));
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('native-question-dark.png')});
    const behaviourIds=async()=>(await (await request.get(path+'/live/history',{headers})).json())
      .filter(e=>e.type==='resp.behaviour_plan').map(e=>e.id);
    const previousOutputs=await behaviourIds();
    const unavailable={intent:'UNSUPPORTED',kind:null,value:null,silent:false};
    await request.post('/__live-fixture/direct-command',{data:type==='core.coordination'
      ?{operation:'KEEP',agreement:null,reply:{},command:unavailable}:{command:unavailable}});
    await native('Walk over here','command-unsupported-after-question');
    await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(4);
    expect((await memory())['command.lastResult']).toMatchObject({status:'UNSUPPORTED',kind:null,value:null,silent:false});
    expect(await behaviourIds()).toEqual(previousOutputs);
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    expect(speech).toHaveLength(before);
    await page.getByTestId('gptlive-stop').click(); await expect(page.getByTestId('gptlive-status')).toHaveText('Idle');
    await interpret('HAND_SIGN','paper'); await continuous(); await transcript('continuous-paper','Show paper');
    await expect.poll(async()=>(await memory())['command.handledTurns']?.length).toBe(5);
    await expect(stage).toHaveAttribute('data-hand-sign','paper');
    await expect(page.locator('#listen_status')).toHaveText('Listening');
    if(type==='core.coordination') expect(speech.length).toBeGreaterThan(before);
    await page.setViewportSize({width:390,height:844});
    await page.locator('#embodiment_panel').screenshot({path:info.outputPath('continuous-paper-narrow.png')});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.getByTestId('toggle-transcription').click();
    await page.getByTestId('gptlive-tab').click(); await page.getByTestId('gptlive-start').click();
    await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
    const reconnect=await (await request.get('/__live-fixture/latest')).json();
    expect(reconnect.instructions).not.toContain('COMMAND RESULT for user turn');
    expect((await memory())['command.handledTurns']).toHaveLength(5);
    await page.getByTestId('gptlive-stop').click();
    expect(errors).toEqual([]);
    await info.attach('mode-parity-memory',{contentType:'application/json',body:Buffer.from(JSON.stringify(await memory()))});
  } finally { await request.delete('/__live-fixture/direct-command'); await request.delete(path,{headers}); }
});
