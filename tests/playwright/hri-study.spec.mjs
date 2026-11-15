import { test, expect } from './fixtures/offline-ui-assets.mjs';
import fs from 'node:fs/promises';

const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
const agreement={goal:'Write a brief greeting',humanRole:'Judge the wording',agentRole:'Write a greeting',success:'The participant accepts the wording',questions:[],checks:[],dialogueLimit:5,reactions:null};
async function invite(request,ready=false) {
  expect(process.env.PROMETHEUS_ADMIN_TOKEN,'Use the isolated acceptance runner').toBeTruthy();
  const response=await request.post('/admin/study/attempts',{headers:admin,data:{mode:'SYNTHETIC',scenario:'guess'}});expect(response.ok()).toBeTruthy();
  const invitation=await response.json();
  if(ready) {
    const headers={'X-Prometheus-Study-Token':invitation.token};
    const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v1.json','utf8'));
    for(const [path,data] of [['consent',{version:'synthetic-test-v1'}],['forms/pre',Object.fromEntries(materials.pre.map(i=>[i.id,i.kind==='rating7'?'4':'None']))],
      ['forms/intention',{intention:'A short friendly greeting'}],['forms/private',{private:'private-umbrella'}]])
      expect((await request.post('/api/study/'+path,{headers,data})).ok()).toBeTruthy();
  }
  return invitation;
}
async function model(request,operation='KEEP',speech='Tell me about your activity.') {
  const response={operation,agreement:operation==='PROPOSE'?agreement:null,reply:{speech}};
  expect((await request.post('/__live-fixture/study-reply',{data:{response:JSON.stringify(response)}})).ok()).toBeTruthy();
}
async function open(page,invitation) { await page.goto('/hri-study/index.html#token='+invitation.token);await expect(page.locator('#workspace')).toBeVisible();expect(page.url()).not.toContain(invitation.token); }
async function exported(request,invitation) { const response=await request.get(`/admin/study/attempts/${invitation.session.id}/export`,{headers:admin});expect(response.ok()).toBeTruthy();return response.json(); }
async function screenshot(page,info,name,width=1440) { await page.setViewportSize({width,height:900});await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath(name+'.png'),fullPage:true}); }
async function noMedia(page) {
  const requests=[];page.on('request',r=>{if(/\/(live|speech|transcription)\//.test(r.url()))requests.push(r.url());});
  await page.addInitScript(()=>{window.mediaRequests=0;navigator.mediaDevices.getUserMedia=()=>{window.mediaRequests++;throw Error('Study must not request media');};});return requests;
}

test('study preparation is clearly closed and text only', async ({page}, info) => {
  const media = [];
  page.on('request', r => { if (/\/(live|speech|transcription)\//.test(r.url())) media.push(r.url()); });
  await page.goto('/hri-study/');
  await expect(page.getByRole('heading', {name: 'Coordinating an activity together'})).toBeVisible();
  await expect(page.getByText('Preparation in progress')).toBeVisible();
  await expect(page.locator('body')).toContainText('Participant sessions are not open yet');
  expect(media).toEqual([]);
  for (const width of [1440, 390]) {
    await page.setViewportSize({width, height: 900});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({path: info.outputPath(`study-${width}.png`), fullPage: true});
  }
});

test('preparation, agreement, native SSE, refresh and finish use one persisted attempt',async({page,request},info)=>{
  const media=await noMedia(page);let streams=0;page.on('response',r=>{if(r.url().endsWith('/api/study/stream')&&r.status()===200)streams++;});
  const invitation=await invite(request);await open(page,invitation);
  await page.getByLabel('I understand that this is a synthetic test and will use invented information.').check();
  await page.getByRole('button',{name:'Continue with synthetic test'}).click();
  await expect(page.getByRole('heading',{name:'A little background'})).toBeVisible();
  for(const field of await page.locator('#fields textarea').all())await field.fill('None');
  await page.locator('#fields select').selectOption('4');await page.getByRole('button',{name:'Save and continue'}).click();
  await expect(page.getByRole('heading',{name:'Your initial intention'})).toBeVisible();
  await page.locator('#fields textarea').fill('A short friendly greeting');await page.getByRole('button',{name:'Save and continue'}).click();
  await expect(page.getByRole('heading',{name:'Your private choices'})).toBeVisible();
  await page.locator('#fields textarea').fill('private-umbrella');await page.getByRole('button',{name:'Begin conversation'}).click();
  await expect(page.locator('#connection')).toHaveText('Connected');
  await model(request,'PROPOSE');await page.getByLabel('Your message',{exact:true}).fill('Propose a greeting, but do not start.');
  await page.getByLabel('Your message',{exact:true}).press('Enter');await page.getByLabel('Your message',{exact:true}).press('Enter');
  await expect(page.locator('#activity-status')).toHaveText('Proposal ready for review');
  await expect(page.locator('#messages .message').filter({hasText:'Proposed task:'})).toHaveCount(1);
  expect((await exported(request,invitation)).requests).toHaveLength(1);
  await screenshot(page,info,'proposal-desktop');
  await model(request,'START','Welcome to the activity.');await page.getByLabel('Your message',{exact:true}).fill('I accept the proposal. Start now.');
  await page.getByRole('button',{name:'Send message',exact:true}).click();
  await expect(page.locator('#messages').getByText('Welcome to the activity.',{exact:true})).toHaveCount(1);
  await expect(page.locator('#activity-status')).toHaveText('Activity running');
  await page.getByLabel('Your message',{exact:true}).fill('Pause the task.');await page.getByRole('button',{name:'Send message',exact:true}).click();
  await expect(page.locator('#activity-status')).toHaveText('Activity paused');
  await expect(page.locator('#messages')).toContainText('Your agreed terms and progress are saved.');
  await expect(page.locator('#messages')).not.toContainText('Initial state: CONVERSATION');
  await page.getByLabel('Your message',{exact:true}).fill('This unsent draft survives a refresh.');await page.reload();
  await expect(page.getByLabel('Your message',{exact:true})).toHaveValue('This unsent draft survives a refresh.');
  await expect(page.locator('#messages').getByText('Welcome to the activity.',{exact:true})).toHaveCount(1);
  await screenshot(page,info,'agreement-mobile',390);
  await page.getByRole('button',{name:'Help',exact:true}).focus();await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();await page.getByRole('button',{name:'Back to session'}).click();
  await page.getByRole('button',{name:'Finish interaction'}).click();await page.getByRole('button',{name:'Save and finish'}).click();
  await expect(page.getByRole('heading',{name:'Your interaction is saved'})).toBeVisible();
  await screenshot(page,info,'finished-mobile',390);
  const data=await exported(request,invitation);expect(data.session.id).toBe(invitation.session.id);expect(data.session.finishReason).toBe('ACTIVITY_ENDED');
  expect(data.requests).toHaveLength(3);expect(data.agreements).toHaveLength(1);expect(data.manifest.recordingComplete).toBe(true);
  expect(streams).toBeGreaterThan(0);expect(media).toEqual([]);expect(await page.evaluate(()=>window.mediaRequests)).toBe(0);
  expect(await page.locator('body').innerText()).not.toContain('private-umbrella');
});

test('lost HTTP response reconciles the committed message without resending',async({page,request},info)=>{
  const invitation=await invite(request,true);await model(request,'KEEP','A confirmed response after transport loss.');await open(page,invitation);
  await expect(page.getByLabel('Your message',{exact:true})).toBeVisible();
  await page.route('**/api/study/turns',async route=>{await route.fetch();await route.abort('connectionreset');},{times:1});
  await page.getByLabel('Your message',{exact:true}).fill('Please discuss my activity.');await page.getByRole('button',{name:'Send message',exact:true}).click();
  await expect(page.locator('#messages').getByText('A confirmed response after transport loss.',{exact:true})).toHaveCount(1);
  await expect(page.locator('#pending-panel')).toBeHidden();await page.reload();
  await expect(page.locator('#messages').getByText('A confirmed response after transport loss.',{exact:true})).toHaveCount(1);
  expect((await exported(request,invitation)).requests).toHaveLength(1);await screenshot(page,info,'recovered-desktop');
});

for(const status of [429,503,400]) test(`HTTP ${status} preserves text and offers deliberate recovery`,async({page,request},info)=>{
  const invitation=await invite(request,true);await model(request);await open(page,invitation);
  const ids=[];page.on('request',r=>{if(r.url().endsWith('/api/study/turns')&&r.method()==='POST')ids.push(r.postDataJSON().requestId);});
  await page.route('**/api/study/turns',route=>route.fulfill({status,body:'Rejected by fault fixture'}),{times:1});
  await page.getByLabel('Your message',{exact:true}).fill('Please keep this draft safe.');await page.getByRole('button',{name:'Send message',exact:true}).click();
  await expect(page.locator('#notice')).toBeVisible();await expect(page.getByRole('button',{name:'Send saved message',exact:true})).toBeVisible();
  await expect(page.getByLabel('Your message',{exact:true})).toHaveValue('Please keep this draft safe.');
  await screenshot(page,info,'error-'+status,390);
  if(status===400) {
    await page.getByRole('button',{name:'Discard pending submission'}).click();await expect(page.locator('#pending-panel')).toBeHidden();
    const data=await exported(request,invitation);expect(data.requests).toHaveLength(1);
    expect(data.requests[0].receipt.reason).toBe('cancelled_before_receipt');
  } else {
    await page.getByRole('button',{name:'Send saved message',exact:true}).click();await expect(page.locator('#pending-panel')).toBeHidden();
    expect((await exported(request,invitation)).requests).toHaveLength(1);expect(new Set(ids).size).toBe(1);
  }
});

test('slow inference keeps help and withdrawal reachable and fences the late result',async({page,request},info)=>{
  const invitation=await invite(request,true);await model(request,'KEEP','Late response must not be committed.');await open(page,invitation);
  await request.post('/__live-fixture/inference/hold');
  try {
    await page.getByLabel('Your message',{exact:true}).fill('Discuss a slow activity.');await page.getByRole('button',{name:'Send message',exact:true}).click();
    await expect(page.locator('#pending-panel')).toBeVisible();await expect(page.getByRole('button',{name:'Send message',exact:true})).toBeDisabled();
    await screenshot(page,info,'pending-desktop');
    await page.getByRole('button',{name:'Help',exact:true}).click();await page.getByRole('button',{name:'Back to session'}).click();
    await page.getByRole('button',{name:'Withdraw',exact:true}).click();await page.getByRole('button',{name:'Withdraw from session',exact:true}).click();
    await expect(page.getByRole('heading',{name:'You have withdrawn'})).toBeVisible();
    await request.post('/__live-fixture/inference/clear');
    await expect.poll(async()=> (await exported(request,invitation)).requests[0]?.receipt.status).toBe('REJECTED');
    expect((await exported(request,invitation)).records.filter(r=>r.kind==='TURN')).toHaveLength(0);
  } finally { await request.post('/__live-fixture/inference/clear'); }
});

test('offline drafts survive reconnect and long untrusted text renders safely',async({page,request,context},info)=>{
  const media=await noMedia(page);const invitation=await invite(request,true);await open(page,invitation);
  await page.getByLabel('Your message',{exact:true}).fill('My unsent message.');await context.setOffline(true);
  await expect(page.locator('#notice')).toContainText('offline');await screenshot(page,info,'offline-mobile',390);
  await context.setOffline(false);await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.getByLabel('Your message',{exact:true})).toHaveValue('My unsent message.');
  const text='<img src=x onerror=alert(1)> '+ 'A long response with space to reflect. '.repeat(12)+'x'.repeat(180);
  await model(request,'KEEP',text);await page.getByLabel('Your message',{exact:true}).fill('Please explain the activity.');await page.getByRole('button',{name:'Send message',exact:true}).click();
  await expect(page.locator('#messages').getByText(text,{exact:true})).toBeVisible();expect(await page.locator('#messages img').count()).toBe(0);
  await screenshot(page,info,'long-message-mobile',390);await screenshot(page,info,'long-message-desktop');
  expect(media).toEqual([]);expect(await page.evaluate(()=>window.mediaRequests)).toBe(0);
});
