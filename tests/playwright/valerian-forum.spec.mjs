import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {test, expect} from './fixtures/offline-ui-assets.mjs';
import {hardware} from './fixtures/native-hardware.mjs';
import {openValerianSession} from './fixtures/valerian-session.mjs';
import {attention, count, fingers} from '../gptlive/hand_fixtures.mjs';
import {exactPose, outputPose} from '../gptlive/hand_scenarios.mjs';
import {armingReady} from '../gptlive/commanding_evidence.mjs';
import {trialCapture} from '../gptlive/trial_capture.mjs';
import {handBudgetCapture} from '../gptlive/hand_budget_capture.mjs';
import {playHandStimulus} from '../gptlive/hand_audio_input.mjs';
import {capturesConsent, deniesRenderedHand} from '../gptlive/forum_speech.mjs';

const prompts = JSON.parse(readFileSync('tests/gptlive/forum-cases.json', 'utf8'));
const playbook = readFileSync('.doc/posters/human-robot-coordination/PLAYBOOK.md', 'utf8');
const paid = process.env.PROMETHEUS_FORUM_PROVIDER_MODE;
test.use({launchOptions:{args:['--autoplay-policy=no-user-gesture-required']}});
const arm = 'I am waiting for a fresh right index cue before I count.';
const sampleReplies = {
  identity:'I’m Valerian, a digital agent from ZHAW.', abilities:'We can agree on a task using hand gestures.',
  vision:'I have no current camera observations.', scissors:'The supplied estimate reports scissors.',
  sign:'I can show a right thumbs-up.', propose:'Show me your right index finger first.',
  accept:arm, repair:arm, reflect:'I recorded one three-step count.', back:'We are back in conversation.'
};
const agreement = {
  goal:'Count once on a fresh right index cue', humanRole:'Demonstrate and judge the count',
  agentRole:'Count one through three with the right hand', success:'The visitor sees all three numbers once',
  questions:[], dialogueLimit:1
};
const condition = {eventType:'obs.hand.pose', minConfidence:0, handPose:{schemaVersion:1,
  hands:[{handedness:'right', all:fingers.map(finger => ({finger, operator:'eq', value:finger === 'index' ? 'straight' : 'bent'}))}]}};
const action = 'Count one through three on the right hand starting with the thumb, holding each step for half a second.';
agreement.checks = [condition];
agreement.reactions = {goal:agreement.goal, maxActions:1, firstAction:'ON_CUE', sessionBound:true,
  rules:[{...condition, action, effect:'ACT', samples:1, cooldownSeconds:3}]};
const sequence = {kind:'sequence', steps:[1,2,3].map(n => ({holdMs:500, action:{kind:'pose',
  hands:[{side:'right', fingers:Object.fromEntries(fingers.map((finger, i) => [finger, Number(i >= n)]))}]}}))};

// Observe actual renderer holding frames even when a 500 ms step falls between HTTP polls.
function watchFrames() {
  const stage = document.querySelector('[data-testid="embodiment-stage"]');
  window.forumFrames = [];
  new MutationObserver(() => {
    const {handEvent, handPhase, sequenceStep} = stage.dataset;
    if (!handEvent || handPhase !== 'holding' ||
        forumFrames.some(frame => frame.id === handEvent && frame.step === sequenceStep)) return;
    const snapshot = ValerianEmbodiment.snapshot();
    forumFrames.push({id:handEvent, step:sequenceStep, action:snapshot.pose.action});
  }).observe(stage, {attributes:true});
}

for (const mode of ['Text', 'GPT-Live']) test('Forum playbook / ' + mode, async ({page, context, request}, info) => {
  const live = mode === 'GPT-Live';
  test.skip(!process.env.PROMETHEUS_ADMIN_TOKEN || (!paid && process.env.PROMETHEUS_LIVE_EXPECT_ENABLED !== 'true'),
    'Use the disposable acceptance runner.');
  if (paid) {
    expect(paid).toBe(live ? 'coordination' : 'coordination-text');
    expect(['127.0.0.1','localhost','[::1]']).toContain(new URL(process.env.PROMETHEUS_BASE_URL).hostname);
    const directory = dirname(info.project.outputDir);
    const ledger = JSON.parse(readFileSync('target/forum-provider-budget.json', 'utf8'));
    const budget = JSON.parse(readFileSync('tests/gptlive/forum-provider-plan.json', 'utf8'));
    const reservation = ledger.attempts.at(-1);
    expect(resolve(reservation.artifact)).toBe(directory);
    expect(reservation.mode).toBe(paid);
    expect(ledger.reservedUsd).toBeLessThanOrEqual(budget.maximumReservedUsd);
    writeFileSync(join(directory, 'forum-provider.started'), paid, {flag:'wx'});
  }
  test.setTimeout(paid ? 365000 : 150000);
  for (const line of Object.values(prompts)) expect(playbook).toContain(line);
  async function body(promise) {
    const response = await promise;
    expect(response.ok(), response.url() + ' HTTP ' + response.status()).toBe(true);
    return response.json();
  }
  const code = Math.random().toString(36).slice(2, 7).toUpperCase();
  const admin = {'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access = await body(request.post('/admin/access-codes', {headers:admin, data:{code, enabled:true}}));
  expect((await request.put('/admin/access-codes/' + access.id + '/agent-types',
    {headers:admin, data:{agentTypeKeys:['core.coordination']}})).ok()).toBe(true);
  const headers = {'X-Prometheus-Access-Code':code};
  const agent = await body(request.post('/demo/agents', {headers, data:{agentDefinitionKey:'core.coordination'}}));
  const path = '/demo/agents/' + agent.id;
  const get = route => body(request.get(path + route, {headers}));
  const memory = async () => Object.fromEntries((await get('/storage')).map(e => [e.key, JSON.parse(e.value)]));
  const history = () => get('/eventhistory');
  const state = async () => (await get('/state')).name;
  const stage = name => expect.poll(state, {message:'Expected ' + name, timeout:30000}).toBe('Coordination ' + name);
  const fixture = async (route, data) => {
    expect(paid).toBeFalsy();
    expect((await request.post('/__live-fixture/' + route, {data})).ok()).toBe(true);
  };
  const observe = (type, data) => body(request.post(path + '/acknowledge', {headers, data:{
    type, actor:'camera', kind:'observation', payload:JSON.stringify(data)}}));
  const pose = data => observe('obs.hand.pose', data);
  const scissors = () => observe('obs.hand.sign', {sign:'scissor', confidence:.97, ts:new Date().toISOString()});
  const actions = async () => (await history()).filter(e => e.type === 'resp.behaviour_plan')
    .map(e => JSON.parse(e.payload).motion?.handAction?.action).filter(Boolean);
  let provider, session, audio;
  const segments = () => get('/live/transcripts?sessionId=' + session.handle);
  const turns = [], checkpoints = [], competingSpeech = [];
  const delegated = ['scissors','propose','accept','repair','back'];
  page.on('request', req => {
    if (session && req.method() === 'POST' && /speech-synthesis|speech-approvals|behaviours\/[^/]+\/speech/.test(req.url()))
      competingSpeech.push(req.url());
  });
  async function checkpoint(name) {
    const m = await memory();
    checkpoints.push({name, state:await state(), actions:m['task.actions'] ?? 0,
      draft:m['coordination.draft'], agreement:m['coordination.agreement'], execution:m['hand.execution.v1']});
    console.log(mode, name, checkpoints.at(-1).state);
    return m;
  }
  async function settledVoice() {
    await page.waitForFunction(() => performance.now() - (__trial.lastOutputCaption || 0) > 3500 &&
      performance.now() - (__handBudget.lastNativeVoice || 0) > 1200, null, {timeout:45000});
  }
  async function say(key) {
    const text = prompts[key];
    const turn = {key, text}; turns.push(turn);
    if (!paid) {
      const op = key === 'propose' ? 'PROPOSE' : key === 'reflect' ? 'REFLECT' : 'KEEP';
      await fixture('coordination-proposal', {operation:op, agreement:key === 'propose' ? agreement : null,
        reply:{speech:sampleReplies[key]}});
    }
    if (!live) {
      const ack = page.waitForResponse(r => r.url().startsWith(new URL(path + '/acknowledge', page.url()).href)
        && r.request().postDataJSON()?.payload === text);
      await page.getByTestId('text-input').fill(text);
      await page.getByTestId('send-text').click();
      const event = (await body(ack)).responseEvent;
      turn.reply = event && JSON.parse(event.payload).speech;
    } else {
      const existing = new Set((await segments()).map(row => row.segmentId));
      if (paid) {
        await settledVoice();
        const start = await page.evaluate(() => __trial.events.length);
        const clip = audio.clips[key];
        const pcmBase64 = readFileSync('target/forum-audio/' + clip.file).toString('base64');
        await page.evaluate(playHandStimulus, {pcmBase64, name:key});
        await page.waitForFunction(i => __trial.events.slice(i).some(e => e.type === 'output_voice') &&
          __trial.events.slice(i).some(e => e.type === 'session.output_transcript.delta'), start, {timeout:35000});
        await settledVoice();
        turn.reply = await page.evaluate(i => __trial.events.slice(i)
          .filter(e => e.type === 'session.output_transcript.delta').map(e => e.delta).join(''), start);
      } else {
        if (delegated.includes(key)) await fixture('commentary/' + provider, [sampleReplies[key]]);
        await fixture('speak/' + provider, {speaker:'USER', text});
      }
      await expect.poll(async () => (await segments()).some(row => !existing.has(row.segmentId) &&
        row.speaker === 'USER' && row.status === 'COMPLETE'), {timeout:25000}).toBe(true);
      turn.admittedInput = (await segments()).filter(row => !existing.has(row.segmentId) &&
        row.speaker === 'USER' && row.status === 'COMPLETE').map(row => row.text).join(' ');
      if (!paid && !delegated.includes(key)) {
        await fixture('speak/' + provider, {speaker:'ASSISTANT', text:sampleReplies[key]});
      }
      if (!paid) {
        await expect.poll(async () => (await segments()).some(row => !existing.has(row.segmentId) &&
          row.speaker === 'ASSISTANT' && row.status === 'COMPLETE'), {timeout:16000}).toBe(true);
        turn.reply = (await segments()).filter(row => !existing.has(row.segmentId) && row.speaker === 'ASSISTANT')
          .map(row => row.text).join(' ');
      }
    }
    console.log(mode, key, turn.reply);
    return turn.reply;
  }

  try {
    if (live && paid) {
      audio = JSON.parse(readFileSync('target/forum-audio/manifest.json','utf8'));
      const limits = {sessionSeconds:360, nativeTextBytes:250000};
      await context.addInitScript({content:`(${trialCapture.toString()})();(${handBudgetCapture.toString()})(${JSON.stringify(limits)});`});
    } else if (live) await hardware(context);
    await openValerianSession(page, '/valerian/?agentId=' + agent.id, code);
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');
    await page.getByRole('button', {name:'Robot', exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready', 'true');
    await page.evaluate(watchFrames);
    if (live) {
      await page.getByTestId('gptlive-tab').click();
      const opened = page.waitForResponse(r => r.url().endsWith('/live/sessions') && r.status() === 201);
      await page.getByTestId('gptlive-start').click();
      session = await body(opened);
      expect(session.speechApprovalRequired).toBe(false);
      expect(session.committedSpeechOutput).toBe(false);
      await expect(page.getByTestId('gptlive-status')).toHaveText('Active');
      if (!paid) provider = (await body(request.get('/__live-fixture/latest'))).providerId;
    }
    await stage('conversation');
    for (const key of ['identity','abilities','vision','scissors']) {
      if (key === 'scissors') await scissors();
      const reply = await say(key);
      expect(reply?.trim(), 'missing ' + key + ' reply').toBeTruthy();
      if (key === 'identity') expect.soft(reply).toMatch(/Valerian/i);
      if (key === 'abilities') expect.soft(reply).toMatch(/task|activit|gestur|hand|coordinat|plan|work together|movement|cue|reaction/i);
      if (key === 'vision') expect.soft(reply).toMatch(/percep|observ|sensor|camera|feed|input|cue|see|sight|visual|view/i);
      if (key === 'scissors') expect.soft(reply).toMatch(/scissor|peace|victory|two fingers|v[- ]sign/i);
      await stage('conversation');
    }
    if (!paid) await fixture('direct-command', {operation:'KEEP', agreement:null, reply:{},
      command:{intent:'ACTION', kind:'HAND_SIGN', value:'thumbs_up', effector:'right_hand', silent:true}});
    const signReply = await say('sign');
    await expect.poll(() => page.evaluate(() => forumFrames.map(f => f.action))).toContainEqual(
      expect.objectContaining({kind:'sign', sign:'thumbs_up', effector:'right_hand'}));
    expect.soft(deniesRenderedHand(signReply ?? ''), 'Spoken reply denied the thumbs-up shown by the renderer').toBe(false);
    await stage('conversation');
    if (!paid) {
      await request.delete('/__live-fixture/direct-command');
      await fixture('hand-output', {motion:{handAction:{schemaVersion:1, action:sequence}}});
    }
    await say('propose'); await stage('preparation');
    const draft = (await memory())['coordination.draft'];
    expect(draft.reactions.maxActions).toBe(1);
    for (const check of [draft.checks[0], draft.reactions.rules[0]])
      exactPose(check.handPose, [['right', [1,0,1,1,1]]]);
    await pose(count(1)); await stage('preparation'); // Thumb only.
    if (live && !paid) await fixture('commentary/' + provider, ['Practice recognized. Shall we start the one-count task?']);
    const earlierSegments = live ? new Set((await segments()).map(row => row.segmentId)) : null;
    const reviewStart = live && paid ? await page.evaluate(() => __trial.events.length) : 0;
    await pose(attention()); await stage('awaiting acceptance');
    expect((await checkpoint('rehearsal'))['coordination.agreement']).toBeUndefined();
    expect(await actions()).toHaveLength(1);
    if (live) {
      // Native speech can deliver a valid invitation before the backend notification is consumed.
      // The visitor needs an invitation actually heard while the reviewed draft is still inactive.
      const reviewText = paid
        ? () => page.evaluate(i => __trial.events.slice(i).filter(e => e.type === 'session.output_transcript.delta')
          .map(e => e.delta).join(''), reviewStart)
        : async () => (await segments()).filter(row => !earlierSegments.has(row.segmentId)
          && row.speaker === 'ASSISTANT' && row.status === 'COMPLETE').map(row => row.text).join(' ');
      await expect.poll(reviewText, {message:'Spoken proposal review and invitation', timeout:45000})
        .toMatch(/(?:shall|should|can|may|would you|do you|ready).{0,100}(?:start|begin|proceed|accept|go ahead)|say (?:start|yes)|please (?:accept|confirm)|your (?:go-ahead|approval)/i);
      if (paid) {
        await page.waitForFunction(i => __trial.events.slice(i).some(e => e.type === 'output_voice') &&
          __trial.events.slice(i).some(e => e.type === 'session.output_transcript.delta'), reviewStart);
        await settledVoice();
        turns.push({key:'review', reply:await reviewText()});
      }
      await stage('awaiting acceptance');
    }
    await say('accept');
    if (paid && live && (await state()) === 'Coordination awaiting acceptance') await say('repair');
    await stage('execution');
    if (live) expect.soft(capturesConsent(turns.at(-1).admittedInput),
      'The admitted user words must support the transition to Execution').toBe(true);
    await expect.poll(async () => {
      const m = await memory();
      return live ? armingReady(m, Date.now()) : Date.now() > Date.parse(m['task.after']);
    }, {timeout:40000}).toBe(true);
    expect((await checkpoint('armed'))['task.actions']).toBe(0);
    await pose(count(1)); expect((await memory())['task.actions']).toBe(0);
    await pose(attention());
    await stage('reflection');
    const done = await checkpoint('count finished');
    expect(done['task.actions']).toBe(1);
    expect(done['hand.execution.v1'].status).toBe('COMPLETED');
    expect((await history()).some(e => e.type === 'resp.behaviour_plan' && e.statePath?.includes('Coordination finishing')
      && JSON.parse(e.payload).motion?.handAction?.action?.kind === 'sequence')).toBe(true);
    const output = await actions();
    expect(output).toHaveLength(2);
    expect(output[1].steps.map(s => s.holdMs)).toEqual([500,500,500]);
    const frames = await page.evaluate(() => forumFrames.filter(f => f.step && f.action.kind === 'pose'));
    expect(frames.map(f => Number(f.step))).toEqual([1,2,3]);
    frames.forEach((frame, i) => outputPose(frame.action, [['right', fingers.map((_, j) => Number(j > i))]]));
    await pose(count(1)); await pose(attention());
    expect(await actions()).toEqual(output);
    expect((await memory())['task.actions']).toBe(1);
    expect(await say('reflect')).toBeTruthy(); await stage('reflection');
    await say('back'); await stage('conversation');
    const archived = (await checkpoint('returned'))['coordination.outcomes'].find(run => run.run === done['coordination.run']);
    expect(Number(archived?.actions)).toBe(1);
    expect(competingSpeech).toEqual([]);
  } finally {
    try {
      const evidence = {mode, provider:paid ? 'real' : 'fixture', turns, checkpoints, competingSpeech,
        state:await state(), storage:await memory(), history:await history(),
        frames:await page.evaluate(() => window.forumFrames ?? [])};
      if (session) evidence.liveHistory = await get('/live/history');
      if (live && paid && session) {
        evidence.events = await page.evaluate(() => __trial.events);
        const {files, ...budget} = await page.evaluate(() => __handBudget.finish());
        evidence.media = budget;
        evidence.audioFiles = files.map((file, i) => {
          const name = 'received-' + i + '.webm';
          writeFileSync(info.outputPath(name), Buffer.from(file.split(',')[1], 'base64'));
          return name;
        });
      }
      writeFileSync(info.outputPath('forum.json'), JSON.stringify(evidence, null, 2));
    } finally {
      await page.close();
      if (!paid) for (const name of ['direct-command','hand-output','coordination-proposal'])
        await request.delete('/__live-fixture/' + name);
      await request.delete(path, {headers});
    }
  }
});
