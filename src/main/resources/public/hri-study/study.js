const $=id=>document.getElementById(id);
const interactive=new Set(['COORDINATION','REVISION','TRIAL']);
const closed=new Set(['WITHDRAWN','REVOKED','REPLACED','COMPLETE']);
const state={token:null,page:null,pending:null,busy:false,syncing:false,again:false,stage:null,events:new Set(),displayed:new Set(),stream:null,cursor:null};
function notice(text='') { $('notice').textContent=text; $('notice').hidden=!text; }
function problem(error) { return !navigator.onLine?'You are offline. Your draft is saved in this tab.':error.status?error.message:'Connection lost. Your draft is saved. Check the saved result before sending again.'; }
function local(key,value) {
  try { if (arguments.length===1) return sessionStorage.getItem(key); if(value===null)sessionStorage.removeItem(key); else sessionStorage.setItem(key,value); }
  catch { notice('Your browser cannot preserve local drafts. Keep this page open while you work.'); }
  return null;
}
function key(suffix) { return `hri.${state.page?.session.id}.${suffix}`; }
function pending(value) { state.pending=value;state.receipt=null;state.unknown=false;local(key('pending'),value?JSON.stringify(value):null); }
function parse(value,fallback=null) { try{return JSON.parse(value)??fallback;}catch{return fallback;} }
function node(tag,text,className) { const result=document.createElement(tag); if(text!==undefined)result.textContent=text; if(className)result.className=className; return result; }
async function api(path,body,options={}) {
  const response=await fetch('/api/study/'+path,{method:body===undefined?'GET':'POST',headers:{'X-Prometheus-Study-Token':state.token,'Content-Type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body),signal:options.signal??AbortSignal.timeout(40000),cache:'no-store'});
  if(!response.ok) { const error=new Error(response.status===401?'This invitation is invalid. Reopen the session with your invitation.'
    :response.status===429?'The service is busy. Your text is saved; check the submission status.'
    :response.status===409?'The session has changed. Check its status before continuing.'
    :response.status>=500?'The service could not confirm the result. Your text is saved; check its status.'
    :'The request could not be accepted. Check your input and session status.'); error.status=response.status; throw error; }
  return response.status===204?null:response.json();
}
function telemetry(type,code='') { if(state.page&&!closed.has(state.page.session.stage))void api('client-events',{id:crypto.randomUUID(),type,clientTime:new Date().toISOString(),code}).catch(()=>{}); }
function stopStream() { state.stream?.abort(); state.stream=null; }
async function stream() {
  if(state.stream||!interactive.has(state.page?.session.stage))return;
  const controller=new AbortController(); state.stream=controller;
  try {
    const headers={'X-Prometheus-Study-Token':state.token}; if(state.cursor)headers['Last-Event-ID']=state.cursor;
    const response=await fetch('/api/study/stream',{headers,signal:controller.signal,cache:'no-store'});
    if(!response.ok||!response.body)throw new Error('Stream unavailable');
    $('connection').textContent='Connected'; if(state.wasOffline){state.wasOffline=false;notice();} const reader=response.body.getReader(),decoder=new TextDecoder(); let buffer='';
    while(!controller.signal.aborted) {
      const {done,value}=await reader.read(); if(done)break;
      buffer+=decoder.decode(value,{stream:true}); buffer=buffer.replaceAll('\r\n','\n');
      let end; while((end=buffer.indexOf('\n\n'))>=0) {
        const block=buffer.slice(0,end); buffer=buffer.slice(end+2);
        if(block.split('\n').some(line=>line.startsWith('data:'))) {
          const id=block.split('\n').find(line=>line.startsWith('id:'))?.slice(3).trim(); if(id)state.cursor=id;
          void refresh(); // Fetch canonical saved events; never render a speculative stream payload.
        }
      }
    }
    if(!controller.signal.aborted)throw new Error('Stream disconnected');
  } catch(error) {
    if(!controller.signal.aborted) { $('connection').textContent='Reconnecting…'; telemetry('DISCONNECT','stream'); }
  } finally {
    if(state.stream===controller)state.stream=null;
    if(!controller.signal.aborted&&interactive.has(state.page?.session.stage))setTimeout(()=>{void refresh();void stream();},2000);
  }
}
function renderPreparation(page) {
  const stage=page.session.stage;
  $('step-heading').textContent=({INFORMATION:'Before you begin',PRE:'A little background',INTENTION:'Your initial intention',PRIVATE:'Your private choices'})[stage]??'Preparation';
  $('information').textContent=stage==='INFORMATION'?page.information:stage==='PRIVATE'?'These answers are saved separately and are not shown to the assistant.':'Use fictional information. You can write “prefer not to say” for background questions.';
  $('preparation-task').textContent=page.task??''; $('preparation-task').hidden=!page.task;
  $('fields').replaceChildren(); const saved=parse(local(key('form.'+stage)),{});
  if(stage==='INFORMATION') {
    const label=node('label'); const input=node('input'); input.type='checkbox';input.id='consent-check';input.required=true;
    label.append(input,node('span','I understand that this is a synthetic test and will use invented information.'));$('fields').append(label);
    $('continue').textContent='Continue with synthetic test';
  } else {
    for(const item of page.fields) {
      const wrapper=node('div'),label=node('label',item.prompt); const field=node(item.kind==='rating7'?'select':'textarea');
      field.id='field-'+item.id; field.name=item.id;field.required=true;label.htmlFor=field.id;
      if(item.kind==='rating7') {
        const empty=node('option','Choose a rating');empty.value='';field.append(empty);
        for(let i=1;i<=7;i++){const option=node('option',i===1?'1 — Not at all comfortable':i===7?'7 — Very comfortable':String(i));option.value=String(i);field.append(option);}
      }else {field.maxLength=2000;field.rows=3;}
      field.value=saved[item.id]??'';field.addEventListener('input',()=>local(key('form.'+stage),JSON.stringify(Object.fromEntries(new FormData($('step-form'))))));
      wrapper.append(label,field);$('fields').append(wrapper);
    }
    $('continue').textContent=stage==='PRIVATE'?'Begin conversation':'Save and continue';
  }
}
function renderPage(page) {
  const previous=state.stage;state.page=page;const session=page.session;state.stage=session.stage;
  $('entry').hidden=true;$('workspace').hidden=false;$('mode').textContent=session.mode==='SYNTHETIC'?'Synthetic test session':session.mode==='PILOT'?'Pilot session':'Study session';
  $('heading').textContent=page.title;$('heading').dataset.session=session.id;
  $('progress').textContent=interactive.has(session.stage)?'Conversation · Review and coordinate the activity in your own words.'
    :session.stage==='ASSESSMENT'?'Interaction saved · Review stage':closed.has(session.stage)?'Session closed':'Preparation · Your progress is saved after each step.';
  $('preparation').hidden=!['INFORMATION','PRE','INTENTION','PRIVATE'].includes(session.stage);
  $('interaction').hidden=!interactive.has(session.stage);$('terminal').hidden=!closed.has(session.stage)&&session.stage!=='ASSESSMENT';
  $('withdraw').hidden=closed.has(session.stage);
  if(!$('preparation').hidden&&previous!==session.stage)renderPreparation(page);
  if(interactive.has(session.stage)) { $('task-title').textContent=page.title;$('task-card').textContent=page.task;void stream(); }
  else stopStream();
  if(!$('terminal').hidden) {
    $('terminal-title').textContent=session.stage==='WITHDRAWN'?'You have withdrawn':session.stage==='ASSESSMENT'?'Your interaction is saved':'This session is closed';
    $('terminal-copy').textContent=session.stage==='ASSESSMENT'?'The synthetic interface test ends here. Your conversation and ending reason have been saved for review.'
      :session.stage==='WITHDRAWN'?'Further interaction has stopped. The session is recorded as withdrawn.':'Your saved record is retained. Further task messages are unavailable in this session.';
    $('receipt').textContent='Session receipt: '+session.id;
  }
}
function renderAgreement(conversation) {
  state.hasDraft=!!conversation.draft;
  const phase={CONVERSATION:'Exploring an activity',COORDINATION:'Clarifying the activity',PREPARATION:'Checking agreed conditions',READY:'Proposal ready for review',EXECUTION:'Activity running',PAUSED:'Activity paused',REFLECTION:'Activity ended',UNCERTAIN:'Activity needs review',FINISHING:'Activity finishing'};
  $('activity-status').textContent=phase[conversation.phase]??'Preparing';
  const signature=JSON.stringify([conversation.draft,conversation.agreement]);if(signature===state.agreementSignature)return;
  state.agreementSignature=signature;$('agreement').replaceChildren();
  for(const [label,agreement] of [['Saved proposal',conversation.draft],['Accepted terms',conversation.agreement]]) {
    if(!agreement)continue;$('agreement').append(node('h3',label));const list=node('dl');
    for(const [term,value] of [['Goal',agreement.goal],['Your role',agreement.humanRole],['Assistant’s role',agreement.agentRole],['Success criterion',agreement.success],['Maximum assistant contributions',agreement.dialogueLimit]]) {
      if(agreement.activity&&term==='Maximum assistant contributions')continue;
      list.append(node('dt',term),node('dd',String(value??'Not yet specified')));
    }
    if(agreement.questions?.length)list.append(node('dt','Still to clarify'),node('dd',agreement.questions.join(' ')));
    if(agreement.activity?.kind==='GUESSING') {
      list.append(node('dt','Game roles'),node('dd','You keep the secret and answer; the assistant asks and guesses.'),
        node('dt','Question limit'),node('dd',`${agreement.activity.questionLimit} total questions, including guesses. Already asked questions remain spent after revision.`),
        node('dt','Allowed answers'),node('dd',agreement.activity.answers.join(', ')),
        node('dt','Ending'),node('dd','Your confirmation of a specific guess means reported success. Budget exhaustion is a separate result.'));
    }
    if(agreement.activity?.kind==='CONTEXT_CHECKIN') {
      for(const [term,value] of [['Check-in question',agreement.activity.question],['If yes',agreement.activity.yesReply],['If no',agreement.activity.noReply],
        ['Scene rules','A fresh one-person scene starts the question after acceptance. Two or more postpone it. Resume with one visible person continues it. Departure ends an ongoing encounter; return does not restart it. Unknown means wait.']])
        list.append(node('dt',term),node('dd',value));
    }
    $('agreement').append(list);
  }
  if(!conversation.draft&&!conversation.agreement)$('agreement').append(node('p','No activity has been agreed yet.','hint'));
}
function renderActivity(activity) {
  state.activity=activity;
  const predictionKey=key('prediction.'+activity.revision);
  if(state.predictionKey!==predictionKey){state.predictionKey=predictionKey;$('prediction').value=local(predictionKey)??'';}
  $('prediction-form').hidden=!activity.predictionRequired||activity.predictionSaved;
  const game=parse(activity.game);$('game-progress').hidden=!game;
  $('game-progress').textContent=game?`Questions and guesses spent: ${game.questions}. ${game.outcome?'Recorded result: '+game.outcome.replaceAll('_',' ').toLowerCase():game.pending?'Pending: '+game.pending.words:''}`:'';
  $('study-instructions').hidden=!activity.revisionInstruction;
  $('revision-card').textContent=activity.revisionInstruction??'';$('probe-card').textContent=activity.probeInstruction??'';
  $('scene-panel').hidden=state.page.session.scenario!=='context';
  const scene=parse(activity.scene),checkin=parse(activity.context);
  const count=scene?.humanCount;$('scene-status').textContent=(count==null?'Scene unknown':count===0?'Nobody visible':count===1?'One person visible':`${count} people visible`)+(activity.sceneApplied?'':' · change awaiting processing');
  $('checkin-progress').textContent=checkin?`Check-in: ${checkin.status.toLowerCase().replaceAll('_',' ')}. ${checkin.outcome?'Recorded result: '+checkin.outcome.toLowerCase().replaceAll('_',' '):''}`:'';
  const step=activity.sceneProbeStep,script=activity.sceneProbe??[],required=['WAITING_FOR_ONE','WAITING_FOR_ANSWER','POSTPONED'][step];
  state.canProbe=step<script.length&&checkin?.status===required&&!state.hasDraft;
  $('scene-probe').textContent=step<script.length?`Next planned scene: ${script[step]===1?'one person':'two people'}`:'Planned scene sequence complete';
  $('scene-probe').disabled=!state.canProbe||state.pending?.kind==='scene';
}
function renderEvents(events) {
  const follow=$('messages').scrollTop+$('messages').clientHeight >= $('messages').scrollHeight-60 || state.events.size===0;
  let appended=false;
  for(const event of events) {
    if(!['obs.user_utterance','resp.behaviour_plan'].includes(event.type))continue;
    const assistant=event.type==='resp.behaviour_plan'; const text=assistant?(parse(event.payload,{})?.speech??'No text response.'):event.payload;
    // A scene command creates a local release barrier before its HTTP request.
    // Old responses arriving during that request wait for the scoped projection.
    if(assistant&&state.pending?.kind==='scene'&&!state.events.has(event.id))continue;
    if(!state.events.has(event.id)) {
      const message=node('div',undefined,'message'+(assistant?'':' user'));message.dataset.eventId=event.id;
      message.append(node('span',assistant?'Assistant':'You','speaker'),node('p',text));$('messages').append(message);state.events.add(event.id);
      appended=true;
    }
    if(assistant&&!state.displayed.has(event.id)&&interactive.has(state.page.session.stage)&&document.visibilityState==='visible') {
      state.displayed.add(event.id);void api('display',{eventId:event.id,clientTime:new Date().toISOString(),disposition:event.withheld?'WITHHELD':'DISPLAY'}).catch(()=>state.displayed.delete(event.id));
    }
  }
  if(appended&&follow)$('messages').scrollTop=$('messages').scrollHeight;
}
function renderPending(receipt=state.receipt,unknown=state.unknown) {
  state.receipt=receipt;state.unknown=unknown;
  $('pending-panel').hidden=!state.pending;$('send').disabled=state.busy||!!state.pending;
  document.querySelectorAll('[data-scene]').forEach(button=>button.disabled=state.pending?.kind==='scene');
  $('scene-probe').disabled=!state.canProbe||state.pending?.kind==='scene';
  if(!state.pending)return;
  $('pending-text').textContent=state.pending.text;
  $('pending-status').textContent=receipt?.status==='UNCERTAIN'?'The result is uncertain. Check the saved conversation, then discard this submission to continue.'
    :unknown?'No saved result was found. Your text is preserved; you can explicitly send the same submission again.':'Your message is pending. We are checking for its saved result.';
  $('retry').hidden=!unknown;$('cancel').disabled=false;
}
function reconcile(receipt) {
  if(!state.pending||receipt.requestId!==state.pending.requestId)return;
  if(receipt.status==='COMMITTED'||receipt.status==='REJECTED') {
    if(receipt.status==='COMMITTED'){state.page.session.turnVersion=receipt.committedTurn;notice();}
    if(receipt.status==='COMMITTED'&&$('message').value===state.pending.text) { $('message').value='';local(key('draft'),''); }
    if(receipt.status==='REJECTED')notice('The pending submission was not applied. Your draft is preserved.');
    if(state.pending.kind==='scene')state.again=true;
    pending(null);
  }
  renderPending(receipt);
}
async function refresh() {
  if(!state.token)return;if(state.syncing){state.again=true;return;}state.syncing=true;
  try {
    const page=await api('page');const changed=state.page?.session.id!==page.session.id;
    if(changed) {
      state.stage=null;state.agreementSignature=null;state.events.clear();state.displayed.clear();state.cursor=null;$('messages').replaceChildren();state.page=page;
      state.pending=parse(local(key('pending')));$('message').value=local(key('draft'))??'';
    }
    renderPage(page);
    if(interactive.has(page.session.stage)||page.session.stage==='ASSESSMENT') {
      const [conversation,events,activity]=await Promise.all([api('conversation'),api('events'),api('activity')]);renderAgreement(conversation);renderEvents(events);renderActivity(activity);
      if(!state.pending&&page.session.pendingRequest) {
        const entry=conversation.turns.find(t=>t.receipt.requestId===page.session.pendingRequest);
        if(entry) {
          if(entry.receipt.inputType==='obs.human.presence') {
            const scene=parse(entry.input);pending({kind:'scene',requestId:entry.receipt.requestId,expectedScene:entry.receipt.expectedTurn,
              humanCount:scene.humanCount,observedAt:scene.ts,probeStep:scene.probeStep,text:'A simulated scene change'});
          } else pending({requestId:entry.receipt.requestId,expectedTurn:entry.receipt.expectedTurn,text:entry.input});
        }
      }
      if(state.pending) {
        try {reconcile(await api('turns/'+state.pending.requestId));}
        catch(error){if(error.status===404)renderPending(null,true);else throw error;}
      } else renderPending();
    }
    if(closed.has(page.session.stage))pending(null);
  } catch(error) {notice(problem(error));}
  finally {state.syncing=false;if(state.again){state.again=false;void refresh();}}
}
async function sendSaved() {
  if(state.busy||!state.pending)return;state.busy=true;renderPending();notice();
  try {reconcile(await api(state.pending.kind==='scene'?'scene':'turns',state.pending));}
  catch(error) {notice(problem(error));telemetry('ERROR',String(error.status??'network'));}
  finally {state.busy=false;await refresh();renderPending();}
}
$('entry-form').addEventListener('submit',event=>{event.preventDefault();stopStream();state.token=$('credential').value.trim();local('hri.token',state.token);state.page=null;notice();void refresh();});
$('prediction').addEventListener('input',()=>local(state.predictionKey,$('prediction').value));
$('prediction-form').addEventListener('submit',async event=>{event.preventDefault();try{await api('predictions',{revision:state.activity.revision,prediction:$('prediction').value});notice('Your prediction is saved. You may now accept the proposal in the conversation.');await refresh();}catch(error){notice(problem(error));}});
async function changeScene(humanCount,probeStep) {
  if(!state.activity||state.pending?.kind==='scene')return;
  const request={kind:'scene',requestId:crypto.randomUUID(),expectedScene:state.activity.sceneVersion,humanCount,observedAt:new Date().toISOString(),probeStep,text:'Simulated scene: '+(humanCount==null?'unknown':humanCount+' visible')};
  pending(request);renderPending();notice();
  try{reconcile(await api('scene',request));}catch(error){notice(problem(error));}
  finally{await refresh();renderPending();}
}
document.querySelectorAll('[data-scene]').forEach(button=>button.addEventListener('click',()=>changeScene(button.dataset.scene==='unknown'?null:Number(button.dataset.scene))));
$('scene-probe').addEventListener('click',()=>changeScene(state.activity.sceneProbe[state.activity.sceneProbeStep],state.activity.sceneProbeStep));
$('step-form').addEventListener('submit',async event=>{
  event.preventDefault();if(state.busy)return;state.busy=true;$('continue').disabled=true;notice();
  try {
    const stage=state.page.session.stage;
    if(stage==='INFORMATION')await api('consent',{version:'synthetic-test-v1'});
    else await api('forms/'+({PRE:'pre',INTENTION:'intention',PRIVATE:'private'})[stage],Object.fromEntries(new FormData($('step-form'))));
    local(key('form.'+stage),null);await refresh();
  }catch(error){notice(problem(error));}
  finally{state.busy=false;$('continue').disabled=false;}
});
$('message').addEventListener('input',()=>local(key('draft'),$('message').value));
$('message').addEventListener('keydown',event=>{if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing){event.preventDefault();if(!state.pending&&!state.busy)$('composer').requestSubmit();}});
$('composer').addEventListener('submit',event=>{event.preventDefault();if(state.busy||state.pending||!$('message').value.trim())return;
  pending({requestId:crypto.randomUUID(),expectedTurn:state.page.session.turnVersion,text:$('message').value});void sendSaved();});
$('check').addEventListener('click',()=>{notice();void refresh();});$('retry').addEventListener('click',()=>void sendSaved());
$('cancel').addEventListener('click',async()=>{if(!state.pending)return;const id=state.pending.requestId;$('cancel').disabled=true;
  try{reconcile(await api('turns/'+id+'/cancel',{}));notice('The pending submission was discarded. Review the saved conversation before continuing.');}
  catch(error){if(error.status===404){pending(null);notice('The unsent submission was discarded. Your draft is preserved.');}else notice(problem(error));}
  finally{await refresh();renderPending();}});
$('help').addEventListener('click',()=>{$('help-dialog').showModal();telemetry('HELP','interface');});
$('finish').addEventListener('click',()=>$('finish-dialog').showModal());$('keep-working').addEventListener('click',()=>$('finish-dialog').close());
$('finish-form').addEventListener('submit',async event=>{event.preventDefault();try{await api('finish',{reason:$('finish-reason').value});$('finish-dialog').close();notice();await refresh();}catch(error){notice(problem(error));}});
$('withdraw').addEventListener('click',()=>$('withdraw-dialog').showModal());$('stay').addEventListener('click',()=>$('withdraw-dialog').close());
$('confirm-withdraw').addEventListener('click',async()=>{try{await api('withdraw',{});$('withdraw-dialog').close();notice();await refresh();}catch(error){notice(problem(error));}});
window.addEventListener('online',()=>{telemetry('RECONNECT','network');void refresh();void stream();});
window.addEventListener('offline',()=>{state.wasOffline=true;$('connection').textContent='Offline · draft saved';notice('You are offline. Your draft is saved in this tab.');stopStream();});
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')void refresh();});
setInterval(()=>{if(state.pending&&navigator.onLine)void refresh();},2500);
const fragment=new URLSearchParams(location.hash.slice(1));state.token=fragment.get('token')??local('hri.token');
const compact=matchMedia('(max-width:760px)');function layout(){for(const id of ['task-details','agreement-details','study-step-details'])$(id).open=!compact.matches;requestAnimationFrame(()=>$('messages').scrollTop=$('messages').scrollHeight);}compact.addEventListener('change',layout);layout();
if(fragment.has('token')){history.replaceState(null,'',location.pathname);local('hri.token',state.token);}
if(state.token)void refresh();
