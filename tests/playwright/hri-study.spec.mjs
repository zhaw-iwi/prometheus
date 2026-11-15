import { test, expect } from './fixtures/offline-ui-assets.mjs';
import fs from 'node:fs/promises';

const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
const agreement={goal:'Write a brief greeting',humanRole:'Judge the wording',agentRole:'Write a greeting',success:'The participant accepts the wording',questions:[],checks:[],dialogueLimit:5,reactions:null};
async function invite(request,ready=false,scenario='guess',foundation=true) {
  expect(process.env.PROMETHEUS_ADMIN_TOKEN,'Use the isolated acceptance runner').toBeTruthy();
  const response=await request.post('/admin/study/attempts',{headers:admin,data:{mode:'SYNTHETIC',scenario,foundation}});expect(response.ok()).toBeTruthy();
  const invitation=await response.json();
  if(ready) {
    const headers={'X-Prometheus-Study-Token':invitation.token};
    const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v2.json','utf8'));
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
async function assessment(page,request,invitation,info,missing=false) {
  await expect(page.locator('#step-heading')).toHaveText('Your interaction is saved');
  await expect(page.locator('#composer')).toBeHidden();
  await page.getByRole('button',{name:'Save understanding answers'}).click();
  expect(await page.locator('#fields textarea:invalid').count()).toBe(3);
  await screenshot(page,info,'understanding-validation-mobile',390);
  for(const field of await page.locator('#fields textarea').all())await field.fill(missing?'Not applicable.':'Synthetic coding response: the saved roles and conditions remain, except the requested revision.');
  await screenshot(page,info,'understanding-desktop');
  await page.getByRole('button',{name:'Save understanding answers'}).focus();await page.keyboard.press('Enter');
  await expect(page.locator('#step-heading')).toHaveText('Your experience');
  for(const field of await page.locator('#fields select').all())await field.selectOption(missing?'SKIP':'4');
  await screenshot(page,info,'questionnaire-desktop');await screenshot(page,info,'questionnaire-mobile',390);
  await page.reload();await expect(page.locator('#fields select').first()).toHaveValue(missing?'SKIP':'4');
  await page.getByRole('button',{name:'Save questionnaire'}).click();await expect(page.locator('#terminal-title')).toHaveText('About this study');
  await screenshot(page,info,'debrief-mobile',390);await page.getByRole('button',{name:'Finish study and save receipt'}).click();
  await expect(page.locator('#terminal-title')).toHaveText('Your study is complete');await page.reload();await expect(page.locator('#receipt')).toContainText(invitation.session.id);
  const data=await exported(request,invitation);expect(data.manifest.procedureComplete).toBe(true);expect(data.manifest.recordingComplete).toBe(true);
  expect(data.assessment.umuxLite.raw0to100).toBe(missing?null:50);
  expect(data.records.filter(r=>r.kind==='ASSESSMENT_REFERENCE')).toHaveLength(1);
  await fs.writeFile(info.outputPath('completed-export.json'),JSON.stringify(data,null,2));
  expect((await request.post('/api/study/forms/understanding',{headers:{'X-Prometheus-Study-Token':invitation.token},data:{changed:'after feedback'}})).ok()).toBe(false);
}

test('guessing game predicts, plays, revises and ends with a reported result',async({page,request},info)=>{
  const invitation=await invite(request,true,'guess',false);const media=await noMedia(page);
  const terms={goal:'Guess an everyday object',humanRole:'Keep the secret and answer',agentRole:'Ask and guess',success:'The human confirms a specific guess',
    questions:[],checks:[],reactions:null,dialogueLimit:6,activity:{kind:'GUESSING',secretOwner:'HUMAN',answers:['YES','NO','UNKNOWN'],questionLimit:5,guessesCount:true}};
  const control=async(operation,agreement=null)=>expect((await request.post('/__live-fixture/study-reply',{data:{response:JSON.stringify({operation,agreement,reply:{speech:'Ready.'}})}})).ok()).toBeTruthy();
  const move=async(kind,words)=>expect((await request.post('/__live-fixture/game-reply',{data:{response:JSON.stringify({kind,words})}})).ok()).toBeTruthy();
  const send=async text=>{await page.locator('#message').fill(text);await page.locator('#send').click();await expect(page.locator('#message')).toHaveValue('');await expect(page.locator('#pending-panel')).toBeHidden();};
  await control('PROPOSE',terms);await move('QUESTION','Is it used indoors?');await open(page,invitation);
  await send('Propose a game where I keep the secret and answer yes, no or unknown. You ask and guess, with five questions including guesses.');
  await expect(page.locator('#prediction-form')).toBeVisible();await screenshot(page,info,'guess-prediction-desktop');
  await page.locator('#prediction').fill('private-prediction-browser');await page.getByRole('button',{name:'Save private prediction'}).click();
  await expect(page.locator('#prediction-form')).toBeHidden();await control('START');await send('I accept the saved proposal. Start now.');
  await expect(page.locator('#game-progress')).toContainText('spent: 1');await send('Pause');await expect(page.locator('#activity-status')).toHaveText('Activity paused');
  await page.reload();await expect(page.locator('#game-progress')).toContainText('Is it used indoors?');await send('Resume');
  const revised=structuredClone(terms);revised.activity.questionLimit=3;revised.dialogueLimit=4;
  await control('REPLACE',revised);await send('Change the question limit to three, keeping everything else unchanged.');
  await expect(page.locator('#prediction-form')).toBeVisible();await screenshot(page,info,'guess-revision-mobile',390);
  await page.locator('#prediction').fill('It will retain the pending question and questions already spent.');await page.getByRole('button',{name:'Save private prediction'}).click();
  await expect(page.locator('#prediction-form')).toBeHidden();await control('START');await send('I accept the saved proposal. Start now.');
  await expect(page.locator('#game-progress')).toContainText('spent: 1');await move('QUESTION','Is it made of metal?');await send('No');
  await move('GUESS','Is it a kettle?');await send('Yes');await expect(page.locator('#game-progress')).toContainText('spent: 3');
  await expect(page.locator('#activity-status')).toHaveText('Activity running');await send('Yes');
  await expect(page.locator('#activity-status')).toHaveText('Activity ended');await expect(page.locator('#game-progress')).toContainText('human confirmed guess');
  await screenshot(page,info,'guess-ended-mobile',390);
  await page.getByRole('button',{name:'Finish interaction'}).click();await page.getByRole('button',{name:'Save and finish'}).click();
  await expect(page.getByRole('heading',{name:'Your interaction is saved'})).toBeVisible();
  await assessment(page,request,invitation,info);
  const data=await exported(request,invitation);expect(data.agreements).toHaveLength(2);expect(data.manifest.recordingComplete).toBe(true);
  expect(JSON.stringify(data.answers)).toContain('private-prediction-browser');
  expect(await page.locator('#messages').innerText()).not.toContain('private-prediction-browser');expect(media).toEqual([]);
});

test('negotiated signs demonstrate, wait, revise and answer each question once',async({page,request},info)=>{
  const invitation=await invite(request,true,'signs',false);const media=await noMedia(page);
  const terms={goal:'A fictional preference check-in',humanRole:'Answer with the agreed simulated signs',agentRole:'Ask the saved questions in order',success:'The agreed answers are recorded',questions:[],checks:[],dialogueLimit:4,reactions:null,
    activity:{kind:'SIGN_ANSWERS',questions:['Would you like a short activity?','Would you prefer a quiet activity?','Would you like a break afterwards?'],yesSign:'THUMBS_UP',noSign:'THUMBS_DOWN',waitSign:'OPEN_PALM',resume:'EXPLICIT_TEXT',demonstrate:true}};
  const control=async(operation,agreement=null)=>expect((await request.post('/__live-fixture/study-reply',{data:{response:JSON.stringify({operation,agreement,reply:{speech:'Ready.'}})}})).ok()).toBeTruthy();
  const send=async text=>{await page.locator('#message').fill(text);await page.locator('#send').click();await expect(page.locator('#message')).toHaveValue('');await expect(page.locator('#pending-panel')).toBeHidden();};
  const sign=async name=>{await page.getByRole('button',{name,exact:true}).click();await expect(page.locator('#pending-panel')).toBeHidden();};
  const demonstrate=async()=>{for(const name of ['Thumbs up','Thumbs down','Open palm']){await sign(name);await expect(page.getByRole('button',{name,exact:true})).toBeDisabled();}};
  const accept=async()=>{await expect(page.locator('#prediction-form')).toBeVisible();await page.locator('#prediction').fill('PRIVATE_SIGN_PREDICTION');await page.getByRole('button',{name:'Save private prediction'}).click();await expect(page.locator('#prediction-form')).toBeHidden();await control('START');await send('I accept this proposal. Start now.');};
  await control('PROPOSE',terms);await open(page,invitation);await send('Propose a short fictional sign-answer check-in with these three questions and demonstrations before acceptance.');
  await expect(page.locator('#activity-status')).toHaveText('Checking agreed conditions');await expect(page.locator('#prediction-form')).toBeHidden();
  await screenshot(page,info,'signs-demonstration-desktop');await demonstrate();await accept();
  await sign('Thumbs up');await expect(page.locator('#sign-progress')).toContainText('1 answer(s)');await expect(page.locator('#sign-status')).toContainText('quiet');
  await sign('Open palm');await expect(page.locator('#activity-status')).toHaveText('Activity paused');await page.reload();
  await expect(page.locator('#sign-status')).toContainText('quiet');await expect(page.getByRole('button',{name:'Thumbs down',exact:true})).toBeDisabled();await screenshot(page,info,'signs-wait-mobile',390);
  await send('Resume');const revised=structuredClone(terms);revised.activity.yesSign='THUMBS_DOWN';revised.activity.noSign='THUMBS_UP';
  await control('REPLACE',revised);await send('Swap yes and no signs, preserving the questions, wait and stopping conditions.');
  await demonstrate();await accept();await expect(page.locator('#sign-status')).toContainText('quiet');await expect(page.locator('#sign-progress')).toContainText('1 answer(s)');
  await sign('Thumbs down');await expect(page.locator('#sign-progress')).toContainText('2 answer(s)');await sign('Thumbs up');
  await expect(page.locator('#activity-status')).toHaveText('Activity ended');await expect(page.locator('#sign-progress')).toContainText('answers recorded');await screenshot(page,info,'signs-ended-mobile',390);
  await page.getByRole('button',{name:'Finish interaction'}).click();await page.getByRole('button',{name:'Save and finish'}).click();await expect(page.getByRole('heading',{name:'Your interaction is saved'})).toBeVisible();
  await assessment(page,request,invitation,info);
  const data=await exported(request,invitation);expect(data.agreements).toHaveLength(2);expect(Object.keys(data.agreements[1].evidence)).toHaveLength(3);expect(data.manifest.recordingComplete).toBe(true);
  const value=data.records.filter(r=>r.kind==='TURN').at(-1).data;
  expect(value['coordination.signAnswers'].answers.map(a=>a.answer)).toEqual(['YES','YES','NO']);expect(media).toEqual([]);expect(await page.evaluate(()=>mediaRequests)).toBe(0);
});

test('context check-in interrupts held output, revises and resumes a pending question',async({page,request},info)=>{
  const invitation=await invite(request,true,'context',false);const media=await noMedia(page);
  const terms={goal:'A fictional routine check-in',humanRole:'Report yes or no in the fictional scenario',agentRole:'Ask once and offer the agreed response',success:'The agreed fictional response is delivered',questions:[],checks:[],dialogueLimit:2,reactions:null,
    activity:{kind:'CONTEXT_CHECKIN',question:'In this fictional scenario, was the medication routine completed?',yesReply:'Thank you for letting me know.',noReply:'You can consider the reminder in the fictional care plan.',startCount:1,postponeAt:2,departure:'END',resume:'EXPLICIT_WHEN_ONE'}};
  const control=async(operation,agreement=null,speech='Ready.')=>expect((await request.post('/__live-fixture/study-reply',{data:{response:JSON.stringify({operation,agreement,reply:{speech}})}})).ok()).toBeTruthy();
  const send=async text=>{await page.locator('#message').fill(text);await page.locator('#send').click();await expect(page.locator('#message')).toHaveValue('');await expect(page.locator('#pending-panel')).toBeHidden();};
  const predict=async()=>{await expect(page.locator('#prediction-form')).toBeVisible();await page.locator('#prediction').fill('Private context prediction');await page.getByRole('button',{name:'Save private prediction'}).click();await expect(page.locator('#prediction-form')).toBeHidden();};
  const scene=async label=>{await page.getByRole('button',{name:label,exact:true}).click();await expect(page.locator('#pending-panel')).toBeHidden();};
  await control('PROPOSE',terms);await open(page,invitation);await send('Propose my fictional routine check-in with the agreed scene conditions.');await predict();
  await control('START');await send('Start now');await expect(page.locator('#messages').getByText(terms.activity.question,{exact:true})).toHaveCount(0);
  await page.locator('#scene-probe').click();await expect(page.locator('#pending-panel')).toBeHidden();await expect(page.locator('#messages').getByText(terms.activity.question,{exact:true})).toHaveCount(1);
  await control('KEEP',null,'SENSITIVE_DELAYED_OUTPUT');await request.post('/__live-fixture/inference/hold');
  try {
    await page.locator('#message').fill('Please rephrase the question.');await page.locator('#send').click();await expect(page.locator('#pending-panel')).toBeVisible();
    await page.locator('#scene-probe').click();
    await expect.poll(async()=>{const r=await request.get('/api/study/activity',{headers:{'X-Prometheus-Study-Token':invitation.token}});return (await r.json()).sceneVersion;}).toBe(2);
    await request.post('/__live-fixture/inference/clear');await expect(page.locator('#activity-status')).toHaveText('Activity paused');
    await expect(page.locator('#pending-panel')).toBeHidden();await expect(page.locator('#messages')).not.toContainText('SENSITIVE_DELAYED_OUTPUT');
  } finally {await request.post('/__live-fixture/inference/clear');}
  await page.reload();await expect(page.locator('#checkin-progress')).toContainText('postponed');await screenshot(page,info,'context-postponed-mobile',390);
  await page.locator('#scene-probe').click();await expect(page.locator('#pending-panel')).toBeHidden();await expect(page.locator('#activity-status')).toHaveText('Activity paused');
  await expect(page.locator('#scene-probe')).toHaveText('Planned scene sequence complete');
  const revised=structuredClone(terms);revised.activity.noReply='A reminder may help in this fictional scenario.';
  await control('PROPOSE',revised);await send('Make the support shorter and neutral, preserving all scene and stopping conditions.');await predict();await control('START');await send('Start now');
  await expect(page.locator('#activity-status')).toHaveText('Activity paused');await scene('One person');await send('Resume');
  await expect(page.locator('#activity-status')).toHaveText('Activity running');await send('No');
  await expect(page.locator('#messages').getByText(revised.activity.noReply,{exact:true})).toBeVisible();await expect(page.locator('#activity-status')).toHaveText('Activity ended');
  await screenshot(page,info,'context-ended-desktop',1440);await scene('Nobody visible');await scene('One person');await expect(page.locator('#activity-status')).toHaveText('Activity ended');
  await page.getByRole('button',{name:'Finish interaction'}).click();await page.getByRole('button',{name:'Save and finish'}).click();
  await assessment(page,request,invitation,info);
  const data=await exported(request,invitation);expect(data.agreements).toHaveLength(2);expect(data.manifest.recordingComplete).toBe(true);
  expect(data.requests.some(r=>r.receipt.reason==='scene_changed')).toBe(true);expect(media).toEqual([]);
});

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
  await screenshot(page,info,'information-desktop');await screenshot(page,info,'information-mobile',390);
  await page.getByLabel('I understand that this is a synthetic test and will use invented information.').check();
  await page.getByRole('button',{name:'Continue with synthetic test'}).click();
  await expect(page.getByRole('heading',{name:'A little background'})).toBeVisible();
  await screenshot(page,info,'background-mobile',390);await screenshot(page,info,'background-desktop');
  for(const field of await page.locator('#fields textarea').all())await field.fill('None');
  await page.locator('#fields select').selectOption('4');await page.getByRole('button',{name:'Save and continue'}).click();
  await expect(page.getByRole('heading',{name:'Your initial intention'})).toBeVisible();
  await screenshot(page,info,'intention-desktop');await screenshot(page,info,'intention-mobile',390);
  await page.locator('#fields textarea').fill('A short friendly greeting');await page.getByRole('button',{name:'Save and continue'}).click();
  await expect(page.getByRole('heading',{name:'Your private choices'})).toBeVisible();
  await screenshot(page,info,'private-mobile',390);await screenshot(page,info,'private-desktop');
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

for(const reason of ['NO_AGREEMENT','TECHNICAL'])test(`unsuccessful ${reason} reaches assessment and debrief`,async({page,request},info)=>{
  const invitation=await invite(request,true);await open(page,invitation);
  if(reason==='TECHNICAL'){
    await page.route('**/api/study/turns',r=>r.fulfill({status:503,body:'Fault fixture'}),{times:1});
    await page.locator('#message').fill('Please discuss the task.');await page.locator('#send').click();await expect(page.locator('#notice')).toBeVisible();
  }
  await page.locator('#finish').click();await page.locator('#finish-reason').selectOption(reason);await page.getByRole('button',{name:'Save and finish'}).click();
  await assessment(page,request,invitation,info,true);const data=await exported(request,invitation);expect(data.session.finishReason).toBe(reason);expect(data.agreements).toHaveLength(0);
});
test('operator provisions balanced invitations, records help and exports retained failures',async({page,request},info)=>{
  await page.goto('/hri-study/operator.html');await page.getByLabel('Operator credential').fill(process.env.PROMETHEUS_ADMIN_TOKEN);await page.getByRole('button',{name:'Open operations'}).click();
  await expect(page.locator('#operations')).toBeVisible();await page.locator('#batch-count').fill('3');await page.getByRole('button',{name:'Create invitations'}).click();
  await expect(page.locator('#invitation-links')).toHaveValue(/guess[\s\S]*|signs[\s\S]*|context[\s\S]*/);
  const links=await page.locator('#invitation-links').inputValue();for(const task of ['guess','context','signs'])expect(links).toContain(task+':');
  const id=links.split(' ')[0],row=page.locator('#attempt-rows tr').filter({hasText:id});await row.getByRole('button',{name:'Record help'}).click();
  await page.locator('#assistance-note').fill('Explained the invitation and refresh procedure.');await page.getByRole('button',{name:'Save assistance record'}).click();await expect(page.locator('#operator-notice')).toContainText('Assistance recorded');
  await row.getByRole('button',{name:'Revoke',exact:true}).click();await expect(row).toContainText('REVOKED');
  const download=page.waitForEvent('download');await page.getByRole('button',{name:'Export assessment CSV'}).click();expect((await download).suggestedFilename()).toBe('study-synthetic.csv');
  const dataset=await request.get('/admin/study/export?mode=SYNTHETIC',{headers:admin});expect(dataset.ok()).toBe(true);await fs.writeFile(info.outputPath('dataset.json'),JSON.stringify(await dataset.json(),null,2));
  expect(await page.evaluate(()=>JSON.stringify([localStorage,sessionStorage]))).not.toContain(process.env.PROMETHEUS_ADMIN_TOKEN);
  await screenshot(page,info,'operator-desktop');await screenshot(page,info,'operator-mobile',390);
});
