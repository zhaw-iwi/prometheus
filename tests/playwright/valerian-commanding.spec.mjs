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
