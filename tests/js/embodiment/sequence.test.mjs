import test from 'node:test';
import assert from 'node:assert/strict';
import {handAction} from '../../../src/main/resources/public/valerian/hand/actions.js';
import {HandSequencePlayer} from '../../../src/main/resources/public/valerian/hand/sequence-player.js';
import {EmbodimentState,normalizePlan} from '../../../src/main/resources/public/valerian/embodiment/model.js';
import {createRobot} from '../../../src/main/resources/public/valerian/embodiment/robot.js';
const fingers=['thumb','index','middle','ring','little'];
const counting=()=>({kind:'sequence',steps:[1,2,3,4,5].map(n=>({action:{kind:'pose',hands:[{side:'right',fingers:Object.fromEntries(fingers.map((f,i)=>[f,i<n?0:1]))}]},holdMs:500}))});
const plan=action=>({motion:{handAction:{schemaVersion:1,action}}});
function fixture(){let now=0;const requests=[],action=counting();const request=async(operation,step)=>{requests.push({operation,step});return {id:'execution',status:operation==='COMPLETE'?'COMPLETED':operation==='CANCEL'?'INTERRUPTED':'RUNNING',step,current:action.steps[step]};};return {action,requests,request,clock:()=>now,at:value=>{now=value;}};}
test('five counting steps render all fingers in order and require one final receipt',async()=>{
  const f=fixture(),player=new HandSequencePlayer(f.action,f);player.start();await player.chain;const robot=createRobot();
  for(let i=0;i<5;i++){
    f.at(i*1200+500);const s=player.sample(f.clock());assert.equal(s.index,i);assert.equal(s.phase,'holding');assert.equal(s.blend,1);
    const pose=normalizePlan(plan(s.action));robot.apply({pose,handBlend:s.blend,transition:1,gesture:0,ambient:0});
    for(const [j,finger] of ['index','middle','ring','little'].entries())assert.equal(robot.root.getObjectByName('right.'+finger+'.1').rotation.x>1,j+1>i);
    f.at((i+1)*1200);player.sample(f.clock());await player.chain;
  }
  assert.equal(player.status,'completed');assert.deepEqual(f.requests.map(r=>r.operation),['START','STEP','STEP','STEP','STEP','COMPLETE']);
});
test('Stop after two prevents steps three to five, including late authorization',async()=>{
  const f=fixture(),player=new HandSequencePlayer(f.action,f);player.start();await player.chain;f.at(500);player.sample(f.clock());f.at(1200);player.sample(f.clock());await player.chain;
  f.at(1700);player.sample(f.clock());player.cancel();await player.chain;f.at(9000);assert.equal(player.sample(f.clock()).blend,0);assert.equal(player.status,'interrupted');assert.deepEqual(f.requests.map(r=>[r.operation,r.step]),[['START',0],['STEP',1],['CANCEL',1]]);
  let resolve;const late=new HandSequencePlayer(f.action,{...f,request:()=>new Promise(r=>{resolve=r;})});late.start();await Promise.resolve();late.cancel('scope_changed');resolve({id:'late',status:'RUNNING',step:0,current:f.action.steps[0]});await Promise.resolve();assert.equal(late.status,'interrupted');assert.equal(late.index,-1);
});
test('history and hidden or reduced views never start; visibility and stalled frames cancel',async()=>{
  for(const options of [{delivery:'history'},{delivery:'replay'},{delivery:'live',fromHistory:true}]){const f=fixture(),state=new EmbodimentState();state.visibility(true);state.receive(plan(f.action),{...options,sequenceRequest:f.request,clock:f.clock});assert.equal(state.sample(0).sequenceStatus,'preview');assert.equal(f.requests.length,0);}
  for(const [visible,reduced] of [[false,false],[true,true]]){const f=fixture(),state=new EmbodimentState();state.visibility(visible,reduced);state.receive(plan(f.action),{delivery:'live',sequenceRequest:f.request,clock:f.clock});assert.equal(state.sample(0).sequenceStatus,'preview');assert.equal(f.requests.length,0);}
  const f=fixture(),state=new EmbodimentState();state.visibility(true);state.receive(plan(f.action),{delivery:'live',sequenceRequest:f.request,clock:f.clock});await state.sequence.chain;state.visibility(false);await state.sequence.chain;state.visibility(true);assert.equal(state.sample(10000).sequenceStatus,'interrupted');
  const lost=new HandSequencePlayer(f.action,f);lost.start();await lost.chain;f.at(1500);lost.sample(f.clock());await lost.chain;assert.equal(lost.reason,'rendering_gap');assert.equal(lost.index,0);
});
test('bad steps, target rejection, disconnected receipts and legacy capabilities fail closed',async()=>{
  const action=counting();for(const holdMs of [99,5001,1.5]){const bad=structuredClone(action);bad.steps[0].holdMs=holdMs;assert.throws(()=>handAction({schemaVersion:1,action:bad}));}
  const nested=structuredClone(action);nested.steps[0].action=action;assert.throws(()=>handAction({schemaVersion:1,action:nested}));assert.match(normalizePlan(plan(action),['motion.handAction'],['hand.poses.v2']).warnings[0],/unavailable/);
  for(const request of [async()=>({id:'x',status:'INTERRUPTED',reason:'target_unavailable'}),async()=>{throw new Error('offline');}]){const player=new HandSequencePlayer(action,{request,clock:()=>0});player.start();await player.chain;assert.ok(['interrupted','unknown'].includes(player.status));assert.equal(player.sample(500).blend,0);}
});
