import {trialCapture} from './trial_capture.mjs';
// Paid synthetic visitor, real cockpit/media/provider/backend and disposable MySQL.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {installOfflineUiAssets} from '../playwright/fixtures/offline-ui-assets.mjs';

const [permission, output] = process.argv.slice(2);
const mode = process.argv.find(v => v.startsWith('--agent=repair-'))?.slice('--agent=repair-'.length);
const base = process.env.PROMETHEUS_BASE_URL, key = process.env.OPENAI_API_KEY;
const ledger = process.env.PROMETHEUS_REPAIR_BUDGET_LEDGER;
if (permission !== '--allow-paid-provider' || !output || !['baseline','live','continuous','text'].includes(mode)
    || !base || !['localhost','127.0.0.1','[::1]'].includes(new URL(base).hostname)
    || !ledger || !JSON.parse(fs.readFileSync(ledger)).attempts.some(e => path.resolve(e.artifact) === path.dirname(path.resolve(output))))
  throw Error('Use a reserved, isolated paid repair runner.');
const plan = JSON.parse(fs.readFileSync(new URL('./gptlivefix-paid-plan.json', import.meta.url)));
const native = ['baseline','live'].includes(mode);
const clips = {
  hello: 'Hello. In one short sentence, who are you?',
  capabilities: 'What small activities can we do together? Please answer briefly.',
  planning: 'How can you help me plan something? Give a short answer.',
  proposal: 'Propose, but do not start: after acceptance and a fresh scissors sign, offer one welcome sentence for lab visitors. I show the sign and judge the welcome; you offer it. Success means I accept the welcome. One action total in this session. First verify scissors recognition. Use default settings.',
  accept: 'Start the saved proposal now.',
  help: 'Please help.',
  reflection: 'That welcome was useful. It met my goal.',
  revision: 'Change only the welcome to a short farewell, keeping all other terms unchanged. Propose it without starting.'
};
if (mode === 'baseline') for (const name of Object.keys(clips).slice(3)) delete clips[name];
assert.ok(Object.keys(clips).length <= plan.limits.stimulusCalls);
assert.ok(Object.values(clips).join('').length <= plan.limits.stimulusCharacters);
const report = {mode, startedAt:new Date().toISOString(), passed:false, turns:[], network:[], stimulus:[],
  speechAudio:[], evidenceErrors:[], clocks:{browser:'performance_now_ms', server:'unix_ms', provider:'audio_offset_ms'}, physicalAudibility:'not_measured', plan};
const started = Date.now(), audio = {}, pendingEvidence = new Set();
let browser, page, agentPath, scoped, sessionHandle, visitorTurns = 0, closing = false;
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
function checkTime() {if (!closing && Date.now() - started > plan.limits.journeySeconds * 1000) throw Error('Journey time budget exhausted');}
async function api(route, method = 'GET', body, headers = scoped) {
  checkTime();
  const response = await fetch(base + route, {method, headers:{...headers,'Content-Type':'application/json'},
    body:body === undefined ? undefined : JSON.stringify(body), signal:AbortSignal.timeout(30000)});
  if (!response.ok) throw Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
const memory = async () => Object.fromEntries((await api(agentPath + '/storage')).map(e => [e.key,JSON.parse(e.value)]));
const history = () => api(agentPath + '/live/history');
async function until(label, action, ms = 45000) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    checkTime();
    if(native && !closing && sessionHandle && page && await page.getByTestId('gptlive-status').textContent()!=='Active')
      throw Error('Live transport stopped: '+await page.getByTestId('gptlive-detail').textContent());
    const result = await action(); if (result) return result; await wait(250);
  }
  throw Error('Timed out: ' + label);
}
async function quiet() {
  if (mode === 'text') return;
  await page.waitForFunction(() => performance.now() - (__trial.lastVoice || 0) > 1200
    && performance.now() - (__trial.lastOutputCaption || 0) > 3500, null, {timeout:35000});
  if (mode === 'continuous') await page.waitForFunction(() => document.getElementById('listen_status').textContent === 'Listening'
    && __trial.mic?.enabled, null, {timeout:30000});
}
async function say(name) {
  if (++visitorTurns > plan.limits.visitorTurns) throw Error('Visitor turn budget exhausted');
  await quiet();
  const beforeEvents = await history(), beforeIds = new Set(beforeEvents.map(e=>e.id));
  const before = beforeEvents.filter(e => e.type === 'obs.user_utterance').length;
  const turn = {name, intendedInput:clips[name], index:await page.evaluate(() => __trial.events.length)};
  report.turns.push(turn); console.log('Visitor: ' + name);
  if (mode === 'text') {
    await page.getByTestId('text-input').fill(clips[name]);
    await page.getByTestId('send-text').click();
  } else turn.input = await page.evaluate(async ({bytes,name}) => {
    const pcm = new Int16Array(Uint8Array.from(bytes).buffer), samples = Float32Array.from(pcm, v => v / 32768);
    const buffer = __trial.context.createBuffer(1,samples.length,24000); buffer.copyToChannel(samples,0);
    let last = samples.length - 1; while (last > 0 && Math.abs(samples[last]) < .01) last--;
    const source = __trial.context.createBufferSource(); source.buffer = buffer; source.connect(__trial.input);
    const at = performance.now(); __trial.mark('input_start',{name});
    await new Promise(resolve => {source.onended = resolve; source.start();});
    const speechEndMs = at + last / 24; __trial.mark('input_end',{name,speechEndMs});
    return {startMs:at,speechEndMs,bufferEndMs:performance.now(),durationMs:samples.length/24};
  }, {bytes:audio[name],name});
  await until('durable user ingress', async () => (await history()).filter(e => e.type === 'obs.user_utterance').length > before);
  if (native) {
    await page.waitForFunction(index => __trial.events.slice(index).some(e => e.type === 'session.output_transcript.delta'), turn.index, {timeout:35000});
    await quiet();
  } else if (mode === 'continuous') {
    await page.waitForFunction(index => __trial.events.slice(index).some(e => e.type === 'output_voice'), turn.index, {timeout:30000});
    await quiet();
  }
  turn.memory = await memory(); turn.state = await api(agentPath + '/state');
  turn.history = (await history()).filter(e=>!beforeIds.has(e.id));
  turn.events = await page.evaluate(index => __trial.events.slice(index), turn.index);
  turn.spoken = turn.events.filter(e => e.type === 'session.output_transcript.delta').map(e => e.delta).join('');
  if (native && ['hello','capabilities','planning'].includes(name)) {
    const normalize = text => text.replace(/\s+/g,'');
    turn.history = await until('complete ordinary native capture for '+name, async()=> {
      const fresh=(await history()).filter(e=>!beforeIds.has(e.id));
      const spoken=fresh.filter(e=>e.provenance?.origin==='NATIVE'&&e.type==='resp.behaviour_plan');
      return spoken.length && spoken.every(e=>e.provenance.complete)
        && normalize(spoken.map(e=>JSON.parse(e.payload).speech||'').join(' '))===normalize(turn.spoken) ? fresh : null;
    },10000);
  }
  if (turn.input) {
    const onset = turn.events.find(e => e.type === 'output_voice' && e.at >= turn.input.speechEndMs - 500);
    const caption = turn.events.find(e => e.type === 'session.input_transcript.delta' || e.type === 'conversation.item.input_audio_transcription.completed');
    turn.latency = {speechEndToOutputMs:onset ? onset.at - turn.input.speechEndMs : null,
      speechEndToInputCaptionMs:caption ? caption.at - turn.input.speechEndMs : null};
    // The first streaming input delta can precede speech end; negative values are expected.
    turn.latency.inputCaptionMarker = 'first streaming delta, not final transcript';
    assert.ok(onset, 'No actual audio was observed for ' + name);
  }
  return turn.memory;
}
async function observe(ts) {
  return api(agentPath + '/acknowledge?profile=full_plan','POST',{type:'obs.hand.sign',actor:'camera',kind:'observation',
    payload:JSON.stringify({sign:'scissor',confidence:.95,ts})});
}
function terms(agreement) {
  // A response-only revision can change goal/agent response wording. Protect the
  // human's commitments and every executable condition, limit and preparation.
  const value = structuredClone(agreement);
  for (const key of ['goal','agentRole','questions']) delete value[key];
  if (value.reactions) {
    delete value.reactions.goal;
    for (const rule of value.reactions.rules) delete rule.action;
  }
  return value;
}

try {
  if (mode !== 'text') {
    const cache = 'target/core-provider-audio'; fs.mkdirSync(cache,{recursive:true});
    for (const [name,input] of Object.entries(clips)) {
      checkTime(); const body = JSON.stringify({model:'gpt-4o-mini-tts',voice:'alloy',input,response_format:'pcm'});
      const file = path.join(cache,createHash('sha256').update(body).digest('hex')+'.pcm'), cached = fs.existsSync(file);
      if (native && plan.nativeCachedStimuliOnly && !cached) throw Error('Native retry requires cached visitor audio before any provider call');
      if (!cached) {
        const response = await fetch('https://api.openai.com/v1/audio/speech',{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},body,signal:AbortSignal.timeout(30000)});
        if (!response.ok) throw Error(`Synthetic visitor speech HTTP ${response.status}`);
        const bytes = Buffer.from(await response.arrayBuffer());
        assert.ok(bytes.length > 0 && bytes.length <= 7200000, 'Stimulus exceeds 150 seconds'); fs.writeFileSync(file,bytes);
      }
      audio[name] = [...fs.readFileSync(file)]; report.stimulus.push({name,cached,characters:input.length,seconds:audio[name].length/48000});
    }
  }
  browser = await chromium.launch({headless:true,args:['--autoplay-policy=no-user-gesture-required']});
  const context = await browser.newContext({viewport:{width:1440,height:1000}}); await installOfflineUiAssets(context);
  await context.addInitScript(trialCapture);
  page = await context.newPage(); page.setDefaultTimeout(30000);
  page.on('response', async response => {
    if (!response.url().startsWith(base)) return;
    const url = new URL(response.url());
    if (/acknowledge|behaviours\/.*\/speech|speech-synthesis|speech-approvals|playback-cues/.test(url.pathname))
      report.network.push({path:url.pathname,method:response.request().method(),status:response.status(),latency:response.headers()['x-prometheus-timing'] || null});
    if (url.pathname.endsWith('/live/sessions') && response.status()===201) sessionHandle=(await response.json()).handle;
    if (mode==='continuous' && /\/behaviours\/[a-f0-9-]+\/speech$/.test(url.pathname) && response.status()===200) {
      const evidence=(async()=>{
        const bytes=await response.body();assert.ok(bytes.length<=16*1024*1024,'Speech artifact exceeded production size bound');
        const id=url.pathname.split('/').at(-2), file=id+'.mp3';
        fs.writeFileSync(path.join(path.dirname(output),file),bytes);
        report.speechAudio.push({eventId:id,file,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex')});
      })().catch(error=>report.evidenceErrors.push(error.message));
      pendingEvidence.add(evidence);void evidence.finally(()=>pendingEvidence.delete(evidence));
    }
  });
  const code = Math.random().toString(36).slice(2,7).toUpperCase(), definition = mode==='baseline'?'core.live_multimodal':'core.coordination';
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access = await api('/admin/access-codes','POST',{code,enabled:true},admin);
  await api(`/admin/access-codes/${access.id}/agent-types`,'PUT',{agentTypeKeys:[definition]},admin);
  scoped={'X-Prometheus-Access-Code':code};
  const agent=await api('/demo/agents','POST',{agentDefinitionKey:definition}); agentPath='/demo/agents/'+agent.id;
  await page.goto(base+`/valerian/?agentId=${agent.id}`);
  await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
  await page.waitForFunction(()=>document.getElementById('behaviour_status').textContent==='Behaviour Live');
  if (native) {
    await page.getByTestId('gptlive-tab').click();
    await page.getByTestId('gptlive-settings').locator(':scope > summary').click();
    await page.locator('#gptlive_voice').selectOption('willow');
    await page.getByTestId('gptlive-start').click();
    await page.waitForFunction(()=>['Active','Error'].includes(document.getElementById('gptlive_status').textContent));
    assert.equal(await page.getByTestId('gptlive-status').textContent(),'Active',await page.locator('#gptlive_detail').innerText());
  } else if (mode==='continuous') {
    await page.getByTestId('continuous-speech-tab').click();
    await page.locator('[data-bs-target="#speech_output_settings"]').click();
    await page.getByTestId('speech-format').selectOption('mp3');
    await page.getByTestId('toggle-transcription').click();
    await page.waitForFunction(()=>document.getElementById('listen_status').textContent==='Listening' && __trial.mic?.enabled);
  }
  for (const name of ['hello','capabilities','planning']) await say(name);
  assert.equal((await memory())['coordination.agreement'],undefined);
  if (mode!=='baseline') {
    let current=await say('proposal'), draft=current['coordination.draft'];
    assert.ok(draft,'Proposal was not persisted'); assert.equal(current['coordination.agreement'],undefined);
    assert.equal(draft.reactions.firstAction,'ON_CUE'); assert.equal(draft.reactions.maxActions,1);
    assert.equal(draft.reactions.rules[0].value,'scissor'); assert.equal(draft.checks[0].value,'scissor');
    current=await say('accept'); assert.equal(current['coordination.agreement'],undefined,'Preparation cannot be verbally bypassed');
    const preparationIndex=await page.evaluate(()=>__trial.events.length);
    const preparationTime=new Date().toISOString();await observe(preparationTime);
    await until('preparation ready',async()=> Object.keys((await memory())['coordination.evidence'] || {}).length===draft.checks.length);
    if(mode!=='text') await page.waitForFunction(index=>__trial.events.slice(index).some(e=>e.type==='output_voice'),preparationIndex,{timeout:30000});
    await quiet();
    const beforeHelp=await memory();current=await say('help');
    assert.equal(current['task.actions']||0,0);assert.deepEqual(current['coordination.draft'],beforeHelp['coordination.draft']);
    current=await say('accept');
    assert.ok(current['coordination.agreement'],'Acceptance was not applied');assert.equal(current['task.actions'],0);
    const agreement=current['coordination.agreement'];
    if (native) await until('arming captured',async()=> (await memory())['task.response']?.status==='CAPTURED');
    await observe(preparationTime);assert.equal((await memory())['task.actions'],0,'Old preparation cannot be an execution cue');
    await until('cue arming deadline',async()=>Date.now()>Date.parse((await memory())['task.after']));
    const contributionIndex=await page.evaluate(()=>__trial.events.length);
    const cueTime=new Date().toISOString();await observe(cueTime);
    await until('one completed contribution',async()=>{
      const state=await memory();if(state['coordination.delivery']?.status==='UNCERTAIN')throw Error('Native final delivery became uncertain');
      return state['task.phase']==='COMPLETED' && state['task.actions']===1;
    },55000);
    if(mode!=='text') await page.waitForFunction(index=>__trial.events.slice(index).some(e=>e.type==='output_voice'),contributionIndex,{timeout:30000});
    await quiet();
    await observe(cueTime);assert.equal((await memory())['task.actions'],1,'Duplicate cue spent another contribution');
    current=await say('reflection');assert.equal(current['task.actions'],1);
    assert.ok(current['coordination.review']?.userReport,'User reflection was not retained');
    current=await say('revision');draft=current['coordination.draft'];assert.ok(draft,'Revision was not proposed');
    assert.deepEqual(terms(draft),terms(agreement),'Revision changed protected terms');
    assert.equal(current['task.phase'],'CONFIGURATION');
    assert.deepEqual(current['coordination.evidence'],{},'Revision must verify preparation again');
    assert.deepEqual(current['coordination.agreement'],agreement,'The earlier agreement remains inspectable until renewed acceptance');
    assert.notDeepEqual(draft,agreement,'Revision must contain the requested new response');
    report.agreement=agreement;report.revisedDraft=draft;
  }
  if (native) {
    assert.ok(report.network.every(e=>!/speech-synthesis|speech-approvals|behaviours\/.*\/speech/.test(e.path)), 'Native conversation called a competing speech route');
    const values=report.turns.slice(0,3).map(t=>t.latency.speechEndToOutputMs).sort((a,b)=>a-b);
    report.latency={n:values.length,medianMs:values[1],minMs:values[0],maxMs:values.at(-1),p95:null};
    if(mode==='baseline') fs.writeFileSync('target/gptlivefix-native-baseline.json',JSON.stringify({artifact:path.resolve(output),...report.latency},null,2));
    else {
      const baseline=JSON.parse(fs.readFileSync('target/gptlivefix-native-baseline.json'));
      report.latency.baseline=baseline;
      report.latency.targetMet=report.latency.medianMs<=baseline.medianMs+plan.latency.medianAllowanceOverBaselineMs
        &&report.latency.maxMs<=baseline.maxMs+plan.latency.maximumAllowanceOverBaselineMs;
      assert.ok(report.latency.targetMet,'Predeclared ordinary native latency target missed');
    }
  }
  if(mode==='continuous') {
    const events=await page.evaluate(()=>__trial.events);
    assert.ok(events.some(e=>e.type==='input_gate'&&e.status==='Input Paused'));
    assert.ok(events.some(e=>e.type==='output_voice'&&e.micEnabled===false));
    assert.ok(report.network.some(e=>/behaviours\/.*\/speech/.test(e.path)));
  }
  report.passed=true;
} catch(error) {report.error=error.message;report.errorStack=error.stack;process.exitCode=1;}
finally {
  closing=true;
  if(page) {
    report.browser=await page.evaluate(()=>__trial.events).catch(()=>[]);
    report.timings=await page.evaluate(async()=> (await import('/performance/timings.js')).turnTimings.snapshot()).catch(()=>null);
    if(native) {
      await page.getByTestId('gptlive-stop').click({timeout:3000}).catch(()=>{});
      await wait(1000);
      report.diagnostics=await page.evaluate(async()=> (await import('/live/diagnostics.js')).liveDiagnostics.snapshot()).catch(()=>null);
    } else if(mode==='continuous') await page.getByTestId('toggle-transcription').click({timeout:3000}).catch(()=>{});
    await page.screenshot({path:path.join(path.dirname(output),'final-cockpit.png')}).catch(()=>{});
  }
  if(agentPath) {
    report.finalStorage=await memory().catch(e=>({error:e.message}));report.history=await history().catch(e=>({error:e.message}));
    if(sessionHandle) report.finalSession=await api(`${agentPath}/live/sessions/${sessionHandle}`).catch(()=>null);
  }
  await browser?.close();
  await Promise.allSettled([...pendingEvidence]);
  report.finishedAt=new Date().toISOString();
  fs.writeFileSync(output,JSON.stringify(report,null,2));
  console.log(report.passed?'Paid repair journey passed.':'Paid repair journey failed: '+report.error);
}
