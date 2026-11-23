import {test,expect} from './fixtures/offline-ui-assets.mjs';

async function drawer(page) {
  await page.locator('#open_diagnostics').click();
  await expect(page.locator('#diagnostics_drawer')).toBeVisible();
  await page.getByTestId('agent-drawer-tab').click();
}
async function visuals(page,info) {
  for(const width of [1440,390]) for(const theme of ['light','dark']) {
    await page.setViewportSize({width,height:1000}); await page.evaluate(theme=>setTheme(theme),theme);
    await page.getByTestId('agent-select').scrollIntoViewIfNeeded();
    await page.locator('#diagnostics_drawer').screenshot({path:info.outputPath(`labels-${width}-${theme}.png`)});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
}

test('instance labels distinguish three persisted same-type agents through selection reload and deletion',async({page,request},info)=>{
  test.skip(process.env.PROMETHEUS_LIVE_EXPECT_ENABLED!=='true'||!process.env.PROMETHEUS_ADMIN_TOKEN,'Use disposable MySQL runner.');
  const code=Math.random().toString(36).slice(2,7).toUpperCase();
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const access=await(await request.post('/admin/access-codes',{headers:admin,data:{code,enabled:true}})).json();
  await request.put(`/admin/access-codes/${access.id}/agent-types`,{headers:admin,data:{agentTypeKeys:['core.coordination']}});
  const headers={'X-Prometheus-Access-Code':code},agents=[];
  for(let i=0;i<3;i++)agents.push(await(await request.post('/demo/agents',{headers,data:{agentDefinitionKey:'core.coordination'}})).json());
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  try {
    await page.goto(`/valerian/?agentId=${agents[0].id}`);
    await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
    await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');await drawer(page);
    const options=await page.getByTestId('agent-select').locator('option').evaluateAll(es=>es.filter(e=>e.value).map(e=>({id:e.value,text:e.textContent,title:e.title})));
    expect(options).toHaveLength(3);expect(new Set(options.map(o=>o.text)).size).toBe(3);
    for(const item of options){expect(item.text).toContain(' · '+item.id.slice(0,8));expect(item.title).toContain(item.id);}
    for(const agent of [...agents.slice(1),agents[0]]) {
      await page.getByTestId('agent-select').selectOption(agent.id);
      await expect(page.getByTestId('agent-id-input')).toHaveValue(agent.id);
      await expect(page.getByTestId('selected-agent-identity')).toContainText(agent.id.slice(0,8));
      await expect(page.getByTestId('connect-agent')).toHaveText('Connect');
      const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/demo/agents/${agent.id}/info`&&r.status()===200);
      await page.getByTestId('connect-agent').click();const loaded=await(await response).json();expect(loaded.id||loaded.ID||loaded.iD).toBe(agent.id);
      await expect(page.locator('#agent_info_name')).toContainText(agent.id.slice(0,8));
    }
    await visuals(page,info);
    await page.reload();await expect(page.locator('#behaviour_status')).toHaveText('Behaviour Live');await drawer(page);
    await expect(page.getByTestId('agent-select')).toHaveValue(agents[0].id);
    const remaining=options.filter(o=>o.id!==agents[1].id);
    await page.getByTestId('agent-select').selectOption(agents[1].id);await page.getByTestId('delete-agent').click();
    await expect(page.getByTestId('agent-select').locator('option')).toHaveCount(3);
    for(const option of remaining)await expect(page.getByTestId('agent-select').locator(`option[value="${option.id}"]`)).toHaveText(option.text);
    const saved=await(await request.get('/demo/agents',{headers})).json();
    expect(saved.map(a=>a.name)).toEqual([agents[0].name,agents[2].name]);expect(errors).toEqual([]);
  } finally {for(const agent of agents)await request.delete(`/demo/agents/${agent.id}`,{headers});}
});

test('instance labels preserve long Unicode names as text and expose colliding identities',async({page,context},info)=>{
  const ids=['12345678-aaaa-4000-8000-000000000001','12345678-aaab-4000-8000-000000000002'];
  const name='Valerian 長い名前 '.repeat(6)+'<img src=x onerror=alert(1)>';
  const profile={supportedObservations:['obs.user_utterance'],supportedBehaviourModalities:['speech'],profileTags:[]};
  const agents=ids.map(id=>({id,name,active:true,languageCode:'en',interactionProfile:profile}));
  const json=body=>({contentType:'application/json',body:JSON.stringify(body)});
  await context.addInitScript(()=>{window.EventSource=class{addEventListener(){}close(){}};});
  await context.route('**/demo/**',route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/demo/session')return route.fulfill(json({accessCode:'LABEL',agentTypes:[],agents}));
    if(path==='/demo/agents')return route.fulfill(json(agents));
    if(path==='/demo/agent-types')return route.fulfill(json([]));
    if(path.endsWith('/info'))return route.fulfill(json(agents.find(a=>path.includes(a.id))));
    if(path.endsWith('/eventhistory')||path.endsWith('/storage')||path.endsWith('/states'))return route.fulfill(json([]));
    if(path.endsWith('/state'))return route.fulfill(json({name:'Conversation',innerNames:[]}));
    if(path.endsWith('/transcription/capabilities'))return route.fulfill(json({schemaVersion:1,sessionType:'transcription',model:'gpt-live-transcribe',settings:[],capabilities:{assistantOutput:false,inputTranscription:true}}));
    return route.fulfill({status:404,body:''});
  });
  await page.goto(`/valerian/?agentId=${ids[0]}`);await page.getByTestId('access-code-input').fill('LABEL');await page.getByTestId('submit-access-code').click();
  await expect(page.locator('#agent_info_name')).toContainText('12345678-aaaa');await drawer(page);
  const labels=await page.getByTestId('agent-select').locator('option').allTextContents();
  expect(labels[1]).toMatch(/… · 12345678-aaaa$/);expect(labels[2]).toMatch(/… · 12345678-aaab$/);
  await expect(page.getByTestId('agent-select').locator(`option[value="${ids[0]}"]`)).toHaveAttribute('title',name+' · 12345678-aaaa\nInstance ID: '+ids[0]);
  expect(await page.locator('#agent_info_name img').count()).toBe(0);
  await expect(page.getByTestId('agent-id-input')).toHaveValue(ids[0]);
  await expect(page.getByTestId('selected-agent-identity')).toContainText('12345678-aaaa');
  await visuals(page,info);
});
