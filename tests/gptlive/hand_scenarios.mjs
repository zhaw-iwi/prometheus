// Happy paths use spoken requests and controlled sensor captures, never injected interpretations.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import * as f from './hand_fixtures.mjs';

export function exactPose(condition,expected){
  assert.equal(condition.hands.length,expected.length);
  for(const [side,values] of expected){const hand=condition.hands.find(h=>h.handedness===side);assert.ok(hand,'Missing '+side);
    assert.deepEqual(Object.fromEntries(hand.all.map(p=>{assert.equal(p.operator,'eq');return[p.finger,p.value];})),
      Object.fromEntries(f.fingers.map((finger,i)=>[finger,values[i]===0?'straight':'bent'])));}
}
export function outputPose(action,expected){assert.equal(action.kind,'pose');assert.equal(action.hands.length,expected.length);
  for(const [side,values] of expected){const hand=action.hands.find(h=>h.side===side);assert.ok(hand);for(let i=0;i<5;i++)
    assert.ok(values[i]===0?hand.fingers[f.fingers[i]]<.4:hand.fingers[f.fingers[i]]>.55,'Wrong '+side+' '+f.fingers[i]);}}
const custom=[['left',[1,0,1,1,1]],['right',[0,1,1,1,1]]];
function named(action,sign,effector='right_hand'){assert.equal(action.kind,'sign');assert.equal(action.sign,sign);assert.equal(action.effector,effector);}
function pointing(action,direction,effector){assert.equal(action.kind,'point');assert.equal(action.direction,direction);assert.equal(action.effector,effector);assert.equal(action.frame.space,'robot_local');}
function countOutput(action,n,hold){assert.equal(action.kind,'sequence');assert.equal(action.steps.length,n);action.steps.forEach((step,i)=>{assert.equal(step.holdMs,hold);outputPose(step.action,[['right',f.fingers.map((_,j)=>j<=i?0:1)]]);});}
const actionOf=motion=>{assert.equal(motion.handAction.schemaVersion,1);return motion.handAction.action;};
const conditions=(draft,key,verify)=>{assert.equal(draft.checks.length,1);assert.equal(draft.reactions.rules.length,1);verify(draft.checks[0][key]);verify(draft.reactions.rules[0][key]);};

export async function runHandScenario(mode,c){
  const {report,page,api,memory,observe,wait,zero,propose,ready,accept,complete,command,render,checkpoint,until}=c;
  const emit=async(make,type='obs.hand.pose')=>observe(type,make());
  const negative=async(make,type='obs.hand.pose')=>{await emit(make,type);await wait(400);await zero();};
  const prepare=async(make,type)=>{await emit(make,type);await ready();await zero();await accept();};
  const finish=async(make,type,kind,verify)=>{const before=(await c.history()).map(e=>e.id);
    const cue=await emit(make,type);await observe(type||'obs.hand.pose',cue);assert.equal((await memory())['task.actions'],1);await complete();
    const outputs=(await c.history()).filter(e=>!before.includes(e.id)&&e.type==='resp.behaviour_plan').map(e=>({id:e.id,...JSON.parse(e.payload)}));
    report.taskOutputs=outputs;if(verify){const hands=outputs.filter(p=>p.motion?.handAction);assert.equal(hands.length,1);verify(actionOf(hands[0].motion));}
    if(kind)await render('task-'+mode,kind); // Native speech is reviewed for meaning, not required to be absent.
  };
  const framePump=async()=>{await emit(()=>f.point(null),'obs.hand.pointing');await c.runPump(()=>observe('obs.hand.pointing',f.point(null),{retain:false}));};
  if(mode==='L-01'){
    await c.say('signcapabilities');await command('signshow','ACCEPTED',{kind:'thumbs_up',verify:m=>named(actionOf(m),'thumbs_up')});
    await command('signrepeat','ACCEPTED',{kind:'thumbs_up',verify:m=>named(actionOf(m),'thumbs_up')});
    await propose('signpropose',d=>conditions(d,'handSign',p=>assert.deepEqual(p,{sign:'thumbs_up',handedness:'right'})));
    await negative(()=>f.sign('thumbs_down'),'obs.hand.signs');assert.deepEqual((await memory())['coordination.evidence'],{});
    await prepare(f.sign,'obs.hand.signs');await negative(()=>f.sign('thumbs_down'),'obs.hand.signs');await finish(f.sign,'obs.hand.signs','peace',a=>named(a,'peace'));
  }else if(mode==='L-02'){
    await propose('posepropose',d=>conditions(d,'handPose',p=>exactPose(p,custom)));
    for(const hand of f.custom().hands)await negative(()=>f.pose([hand]));assert.deepEqual((await memory())['coordination.evidence'],{});
    await prepare(f.custom);await negative(()=>f.pose([f.custom().hands[0]]));await finish(f.custom,undefined,'custom_pose',a=>outputPose(a,custom));
    await command('poseshow','ACCEPTED',{kind:'custom_pose',verify:m=>outputPose(actionOf(m),custom)});
  }else if(mode==='L-03'){
    await command('ringambiguous','CLARIFY');await command('ringshow','ACCEPTED',{kind:'ok_ring',verify:m=>named(actionOf(m),'ok_ring')});
    await propose('ringpropose',d=>conditions(d,'handPose',p=>{assert.equal(p.hands.length,1);const h=p.hands[0];assert.equal(h.handedness,'right');
      assert.deepEqual(h.relations,[{kind:'tip_proximity',fingers:['thumb','index'],operator:'lt',value:.18}]);
      assert.equal(h.all.length,3);for(const finger of ['middle','ring','little'])assert.ok(h.all.some(v=>v.finger===finger&&v.operator==='eq'&&v.value==='straight'));}));
    await negative(()=>f.ring(false));assert.deepEqual((await memory())['coordination.evidence'],{});await prepare(f.ring);await negative(()=>f.ring(false));await finish(f.ring,undefined,'ok_ring',a=>named(a,'ok_ring'));
  }else if(mode==='L-04'){
    await framePump();await propose('directionpropose',d=>conditions(d,'handPoint',p=>{assert.equal(p.direction,'right');assert.equal(p.handedness,'right');assert.deepEqual(p.frame,f.frame);}));
    await negative(()=>f.point('left'),'obs.hand.pointing');await prepare(f.point,'obs.hand.pointing');await negative(()=>f.point('left'),'obs.hand.pointing');
    await finish(f.point,'obs.hand.pointing','point',a=>pointing(a,'right','right_hand'));
    await command('directionshow','ACCEPTED',{kind:'point',verify:m=>pointing(actionOf(m),'left','left_hand')});
  }else if(mode==='L-05'){
    await framePump();const scene=await api('/hand-scene','POST',f.sceneConfig());const target=f.ref(scene.targets[0]);report.scene=scene;
    await propose('displaypropose',d=>conditions(d,'handPoint',p=>assert.deepEqual(p.target,target)));
    await negative(()=>f.point('left'),'obs.hand.pointing');await prepare(f.point,'obs.hand.pointing');await negative(()=>f.point('left'),'obs.hand.pointing');await finish(f.point,'obs.hand.pointing');
    await command('displayshow','ACCEPTED',{kind:'point',verify:m=>{const a=actionOf(m);assert.deepEqual(a.target,target);assert.equal(a.effector,'right_hand');assert.ok(Math.abs(a.resolved.position.x-3.2)<.001);}});
  }else if(mode==='L-06'){
    let ambiguous=true,person=true;await framePump();await c.stopPump();const scene=await api('/hand-scene','POST',f.sceneConfig(true));report.scene=scene;
    const fresh=async()=>{await observe('obs.scene.targets',f.targets({ambiguous,person}),{retain:false});};
    await fresh();await c.runPump(fresh);const ambiguousCue=await emit(f.point,'obs.hand.pointing');const pointEvent=(await c.history()).find(e=>e.type==='obs.hand.pointing'&&JSON.parse(e.payload).observationId===ambiguousCue.observationId);assert.equal(JSON.parse(pointEvent.payload).resolution.status,'ambiguous');
    await command('cupambiguous','CLARIFY',{});
    const bind=async(id,label,destination)=>{const targets=(await memory())['hand.scene.observed.v1'].targets,target=targets.find(t=>t.id.endsWith(':'+id));assert.ok(target);await api('/hand-scene/binding','POST',{target:f.ref(target),label,destination:f.ref(scene.targets[destination])});return(await memory())['hand.scene.selected.v1'];};
    const cup=await bind('cup-a','My cup',0);ambiguous=false;await wait(750);
    await propose('cuppropose',d=>conditions(d,'handPoint',p=>assert.deepEqual(p.target,cup)));await prepare(f.point,'obs.hand.pointing');await negative(()=>f.point('left'),'obs.hand.pointing');await finish(f.point,'obs.hand.pointing');
    const visitor=await bind('visitor','Visitor',1);await command('personshow','ACCEPTED',{kind:'point',verify:m=>{const a=actionOf(m);assert.deepEqual(a.target,visitor);assert.deepEqual(a.resolved.position,{x:-3,y:2,z:0});}});
    person=false;await wait(3500);await command('personrepeat','CLARIFY',{});
  }else if(mode==='L-07'){
    const draft=await propose('inputpropose',d=>conditions(d,'handSequence',s=>{assert.deepEqual(s.steps,[1,2,3].map(count=>({kind:'count',count,hands:'right'})));assert.ok(s.dwellMs>=300&&s.dwellMs<=5000);}));
    const dwell=draft.checks[0].handSequence.dwellMs;
    const counts=async values=>{for(const n of values){const start=Date.now();do{await emit(()=>f.count(n));await wait(250);}while(Date.now()-start<dwell+500);}await checkpoint('counts-'+values.join('-'));};
    await counts([1,3]);assert.deepEqual((await memory())['coordination.evidence'],{});await zero();await counts([1,2,3]);await ready();await accept();
    await counts([1,3]);await zero();await counts([1,2,3]);await complete();await counts([3]);assert.equal((await memory())['task.actions'],1);
  }else if(mode==='L-08'){
    const sequence=async(name,n,hold,stopAt=null)=>{const previousEvent=(await memory())['hand.execution.v1']?.id??null;
      const frames=c.sequenceFrames(name,n,{stopAt,previousEvent});const [turn]=await Promise.all([command(name,'ACCEPTED',{verify:m=>countOutput(actionOf(m),n,hold)}),frames]);
      if(stopAt)await until('confirmed Stop',async()=>(await memory())['hand.execution.v1']?.status==='INTERRUPTED');return turn;};
    await sequence('outputcount',5,500);await sequence('outputrepeat',5,1000,2);const stopped=(await memory())['hand.execution.v1'];await wait(2000);assert.deepEqual((await memory())['hand.execution.v1'],stopped);
    await propose('outputpropose',d=>conditions(d,'handPose',p=>exactPose(p,[['right',[1,0,1,1,1]]])));await prepare(f.attention);
    const frames=c.sequenceFrames('task-count',3,{previousEvent:stopped.id});await Promise.all([emit(f.attention),frames]);await complete();
    const outputs=(await c.history()).filter(e=>e.type==='resp.behaviour_plan').map(e=>JSON.parse(e.payload)).filter(p=>p.motion?.handAction?.action?.kind==='sequence');assert.equal(outputs.length,3);countOutput(actionOf(outputs[2].motion),3,500);
    await emit(f.attention);assert.equal((await memory())['task.actions'],1);await page.getByTestId('gptlive-stop').click();await wait(500);await c.captureMedia();await page.reload({waitUntil:'domcontentloaded'});await wait(1500);assert.equal((await memory())['hand.execution.v1'].status,'COMPLETED');
  }else if(mode.startsWith('L-09')){
    for(const name of ['hello','activities','planning'])await c.say(name);
    report.conversation=c.conversationLatency(report.turns.slice(0,3));
    if(mode==='L-09-comparison')fs.writeFileSync('target/hand-native-baseline.json',JSON.stringify({artifact:c.directory},null,2));
    else {const baseline=JSON.parse(fs.readFileSync(path.join(JSON.parse(fs.readFileSync('target/hand-native-baseline.json')).artifact,'journey.json')));
      assert.equal(baseline.automatedPassed,true);assert.equal(baseline.acceptanceRevision,report.acceptanceRevision,'Comparison acceptance revision differs');report.comparison=c.compareConversations(baseline,report);assert.equal(report.comparison.passed,true,'Native latency regression');
      await command('ordinarynew','ACCEPTED',{kind:'index_up',verify:m=>named(actionOf(m),'index_up')});}
  }else throw Error('Unimplemented hand scenario');
  await checkpoint('final');
}
