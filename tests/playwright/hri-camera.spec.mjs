import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';
const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
const contextTerms={goal:'A fictional routine check-in',humanRole:'Report yes or no in the fictional scenario',agentRole:'Ask once and offer the agreed response',success:'The agreed fictional response is delivered',questions:[],checks:[],dialogueLimit:2,reactions:null,
  activity:{kind:'CONTEXT_CHECKIN',question:'In this fictional scenario, was the medication routine completed?',yesReply:'Thank you for letting me know.',noReply:'A reminder may help.',startCount:1,postponeAt:2,departure:'END',resume:'EXPLICIT_WHEN_ONE'}};
const signTerms={goal:'A fictional preference check-in',humanRole:'Answer with the agreed signs',agentRole:'Ask the saved questions in order',success:'Every agreed question has an answer',questions:[],checks:[],dialogueLimit:4,reactions:null,
  activity:{kind:'SIGN_ANSWERS',questions:['Would you like a short activity?','Would you prefer a quiet activity?','Would you like a break afterwards?'],yesSign:'THUMBS_UP',noSign:'THUMBS_DOWN',waitSign:'OPEN_PALM',resume:'EXPLICIT_TEXT',demonstrate:true}};
async function invite(request,scenario){
  const r=await request.post('/admin/study/attempts',{headers:admin,data:{mode:'SYNTHETIC',scenario}});expect(r.ok()).toBeTruthy();const invitation=await r.json();
  expect(invitation.session.materialsVersion).toBe('hri-text-v3-draft');const headers={'X-Prometheus-Study-Token':invitation.token};
  const material=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v3.json','utf8'));
  for(const [path,data] of [['consent',{version:'synthetic-test-v1'}],['forms/pre',Object.fromEntries(material.pre.map(i=>[i.id,i.kind==='rating7'?'4':'None']))],['forms/intention',{intention:'PRIVATE_INTENT'}],['forms/private',{private:'PRIVATE_PREFERENCE'}]])
    expect((await request.post('/api/study/'+path,{headers,data})).ok()).toBeTruthy();
  return {...invitation,headers};
}
async function fixtures(page,{denied=false,modelFailure=false}={}){
  await page.route('**/hri-study/camera-models.js',route=>route.fulfill({contentType:'application/javascript',body:modelFailure
    ?'export async function loadVisualModels(){throw Error("fixture download failure");}'
    :'export async function loadVisualModels(){return {async sample(){return structuredClone(window.cameraFrame);},close(){window.detectorsClosed++;}};}'}));
  await page.addInitScript(({denied})=>{
    window.cameraFrame={humanCount:1,emotion:'neutral',confidence:.9,gesture:null,gestureConfidence:0};window.cameraRequests=[];window.detectorsClosed=0;
    navigator.mediaDevices.getUserMedia=async constraints=>{
      window.cameraRequests.push(constraints);if(denied){const e=Error();e.name='NotAllowedError';throw e;}
      const canvas=document.createElement('canvas');canvas.width=640;canvas.height=480;const ctx=canvas.getContext('2d');
      window.cameraStream=canvas.captureStream(10);setInterval(()=>{ctx.fillStyle='#b7d1db';ctx.fillRect(0,0,640,480);},80);return window.cameraStream;
    };
  },{denied});
}
async function open(page,i){await page.goto('/hri-study/#token='+i.token);await expect(page.locator('#camera-panel')).toBeVisible();}
async function send(page,text){await expect(page.locator('#send')).toBeEnabled();await page.locator('#message').fill(text);await page.locator('#send').click();await expect(page.locator('#message')).toHaveValue('');await expect(page.locator('#pending-panel')).toBeHidden();}
async function model(request,operation,agreement=null){expect((await request.post('/__live-fixture/study-reply',{data:{response:JSON.stringify({operation,agreement,reply:{speech:'Review the saved terms.'}})}})).ok()).toBeTruthy();}
async function accept(page,request){await expect(page.locator('#prediction-form')).toBeVisible();await page.locator('#prediction').fill('PRIVATE_PREDICTION');await page.locator('#prediction-form button').click();await expect(page.locator('#prediction-form')).toBeHidden();await model(request,'START');await send(page,'I accept this proposal. Start now.');}
async function gesture(page,category){await page.evaluate(()=>{window.cameraFrame.gesture=null;window.cameraFrame.gestureConfidence=0;});await page.waitForTimeout(850);await page.evaluate(category=>{window.cameraFrame.gesture=category;window.cameraFrame.gestureConfidence=.9;},category);await page.waitForTimeout(1350);await expect(page.locator('#pending-panel')).toBeHidden();}
async function capture(page,info,name,width){await page.setViewportSize({width,height:900});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath(name+'.png'),fullPage:true});}
async function finish(page){await page.locator('#finish').click();await page.getByRole('button',{name:'Save and finish'}).click();await expect(page.locator('#step-heading')).toHaveText('Your interaction is saved');await expect.poll(()=>page.evaluate(()=>window.cameraStream?.getTracks().every(t=>t.readyState==='ended')??true)).toBe(true);}

test('landing is accurate and permission denial or detector failure leaves finish available',async({page,request},info)=>{
  await page.goto('/hri-study/');await expect(page.locator('#entry')).toContainText('typed conversation and your camera');
  for(const failure of [{denied:true},{modelFailure:true}]){
    const i=await invite(request,'guess');await fixtures(page,failure);await open(page,i);
    await expect(page.locator('#camera-status')).toContainText(failure.denied?'permission was denied':'startup failed');await expect(page.locator('#send')).toBeDisabled();
    expect(await page.evaluate(()=>window.cameraRequests.every(r=>r.audio===false))).toBe(true);
    await capture(page,info,failure.denied?'camera-denied':'detector-failed',390);await finish(page);
    await page.unroute('**/hri-study/camera-models.js');
  }
});

test('camera face estimates reach the assigned agent and withdrawal stops all capture',async({page,request},info)=>{
  const i=await invite(request,'guess');await fixtures(page);await open(page,i);await expect(page.locator('#camera-status')).toContainText('all three detectors');
  await expect(page.locator('#detector-status')).toContainText('People: 1');
  await expect.poll(async()=>{const r=await request.get('/api/study/activity',{headers:i.headers});return JSON.parse((await r.json()).scene??'{}').humanCount;}).toBe(1);
  await send(page,'What facial expression do you see?');await expect(page.locator('#messages')).toContainText('does not establish your feelings');
  await capture(page,info,'camera-desktop',1440);await capture(page,info,'camera-mobile',390);
  let rejected=0;await page.route('**/api/study/vision',route=>{rejected++;return route.fulfill({status:429,contentType:'application/json',body:JSON.stringify({code:'CAPACITY',message:'All study places are occupied.'})});});
  await page.evaluate(()=>window.cameraFrame.humanCount=0);await expect(page.locator('#camera-status')).toContainText('delivery is interrupted');await expect(page.locator('#send')).toBeDisabled();
  await page.waitForTimeout(1300);expect(rejected).toBe(1);await page.unroute('**/api/study/vision');
  await page.locator('#withdraw').click();await page.locator('#confirm-withdraw').click();await expect(page.locator('#terminal-title')).toHaveText('You have withdrawn');
  expect(await page.evaluate(()=>window.cameraStream.getTracks().every(t=>t.readyState==='ended'))).toBe(true);expect(await page.evaluate(()=>window.detectorsClosed)).toBe(1);
});

test('actual camera pipeline postpones for a second detected person and treats stopped camera as unknown',async({page,request},info)=>{
  const i=await invite(request,'context');await fixtures(page);await open(page,i);await expect(page.locator('#scene-status')).toHaveText('One person visible');
  await expect(page.getByRole('button',{name:'Two people',exact:true})).toBeHidden();await model(request,'PROPOSE',contextTerms);await send(page,'Propose the fictional check-in.');await accept(page,request);
  await expect(page.locator('#checkin-progress')).toContainText('waiting for answer');
  await page.evaluate(()=>window.cameraFrame.humanCount=2);await expect(page.locator('#activity-status')).toHaveText('Activity paused');await expect(page.locator('#messages')).toBeHidden();
  await capture(page,info,'camera-two-people',1440);await page.evaluate(()=>window.cameraFrame.humanCount=1);await expect(page.locator('#scene-status')).toHaveText('One person visible');
  await expect(page.locator('#activity-status')).toHaveText('Activity paused');await send(page,'Resume');await expect(page.locator('#activity-status')).toHaveText('Activity running');
  await page.locator('#camera-stop').click();await expect(page.locator('#camera-status')).toContainText('Camera stopped');await expect(page.locator('#activity-status')).toHaveText('Activity paused');
  const a=await (await request.get('/api/study/activity',{headers:i.headers})).json();expect(JSON.parse(a.context).outcome).toBeUndefined();expect(JSON.parse(a.scene).humanCount).toBeNull();
  await finish(page);
});

test('camera gestures demonstrate, consume one held sign, wait, revise and lead to the locked questionnaire',async({page,request},info)=>{
  const i=await invite(request,'signs');await fixtures(page);await open(page,i);await expect(page.locator('#camera-status')).toContainText('all three detectors');
  await model(request,'PROPOSE',signTerms);await send(page,'Propose the sign check-in with demonstrations.');
  for(const sign of ['Thumb_Up','Thumb_Down','Open_Palm'])await gesture(page,sign);await accept(page,request);
  await gesture(page,'Thumb_Up');await expect(page.locator('#sign-progress')).toContainText('1 answer(s)');await page.waitForTimeout(1700);await expect(page.locator('#sign-progress')).toContainText('1 answer(s)');
  await gesture(page,'Open_Palm');await expect(page.locator('#activity-status')).toHaveText('Activity paused');await send(page,'Resume');
  const revised=structuredClone(signTerms);revised.activity.yesSign='THUMBS_DOWN';revised.activity.noSign='THUMBS_UP';await model(request,'REPLACE',revised);await send(page,'Swap yes and no mappings, preserving the rest.');
  for(const sign of ['Thumb_Up','Thumb_Down','Open_Palm'])await gesture(page,sign);await accept(page,request);
  await gesture(page,'Thumb_Down');await expect(page.locator('#sign-progress')).toContainText('2 answer(s)');await gesture(page,'Thumb_Up');await expect(page.locator('#activity-status')).toHaveText('Activity ended');
  await page.getByRole('button',{name:'Continue to questionnaire'}).click();await page.getByRole('button',{name:'Save and finish'}).click();await expect(page.locator('#step-heading')).toHaveText('Your interaction is saved');
  expect(await page.evaluate(()=>window.cameraStream.getTracks().every(t=>t.readyState==='ended'))).toBe(true);
  for(const field of await page.locator('#fields textarea').all())await field.fill('The mapping changed; questions and wait remained.');await page.getByRole('button',{name:'Save understanding answers'}).click();await expect(page.locator('#step-heading')).toHaveText('Your experience');
  for(const field of await page.locator('#fields select').all())await field.selectOption('4');await capture(page,info,'camera-post',390);await page.getByRole('button',{name:'Save questionnaire'}).click();
  await expect(page.locator('#terminal-copy')).toContainText('browser-based estimates');await page.locator('#complete').click();
  const data=await (await request.get(`/admin/study/attempts/${i.session.id}/export`,{headers:admin})).json();expect(data.manifest.procedureComplete).toBe(true);expect(data.manifest.recordingComplete).toBe(true);expect(data.agreements).toHaveLength(2);
  expect(JSON.stringify(data)).not.toContain('data:image');expect(await page.evaluate(()=>window.cameraRequests.every(r=>r.audio===false))).toBe(true);
  await fs.writeFile(info.outputPath('camera-export.json'),JSON.stringify(data,null,2));
});
