// Harness-only regression: this is not provider, detector or application acceptance.
import assert from 'node:assert/strict';
import test from 'node:test';
import {runHandScenario,requireReadyContribution,requireAcknowledgement,demonstrateCount,paceHandReceipts} from './hand_scenarios.mjs';

test('queued count captures respect the receipt limit across successive poses',async()=>{
  const captured=[0,318,374,722,924,1230,1302],latency=[148,56,37,56,71,72,28];
  let time=0;const admitted=[];
  const deliver=paceHandReceipts(async sample=>{
    const start=time;
    assert.ok(admitted.filter(p=>p.receipt>start-1000).length<5);
    time+=latency[sample.index];admitted.push({...sample,receipt:time});
  },async ms=>{time+=ms;},()=>time);
  for(const [index,at] of captured.entries()){
    time=Math.max(time,at);await deliver({index,at});
  }
  assert.deepEqual(admitted.map(p=>p.at),captured);
  assert.equal(admitted.at(-1).receipt,1402);
});

test('count dwell spans fresh samples at camera cadence',async()=>{
  for(const dwellMs of [300,900,2500]){
    let time=0;const captures=[];
    await demonstrateCount(()=>({ts:new Date(time).toISOString()}),{dwellMs,maximumGapMs:5000},
      async sample=>{captures.push(Date.parse(sample.ts));},async ms=>{time+=ms;},()=>time);
    assert.ok(captures.length>=2);
    assert.equal(captures.at(-1)-captures[0],dwellMs);
    assert.ok(captures.slice(1).every((t,i)=>t>captures[i]&&t-captures[i]<=500));
  }
});

test('slow pose acknowledgement preserves capture times and ordered delivery',async()=>{
  let time=0,release;const captured=[],sent=[];
  const firstReceipt=new Promise(resolve=>{release=resolve;});
  const trial=demonstrateCount(()=>{
    captured.push(time);return {ts:new Date(time).toISOString()};
  },{dwellMs:300,maximumGapMs:5000},async sample=>{
    sent.push(Date.parse(sample.ts));if(sent.length===1)await firstReceipt;
  },async ms=>{time+=ms;},()=>time);
  await new Promise(resolve=>setImmediate(resolve));
  assert.deepEqual(captured,[0,300]);assert.deepEqual(sent,[0]);
  time=1730;release();await trial;
  assert.deepEqual(sent,[0,300]);
});

test('persistent capture gaps stop at the agreed limit without advancing the count',async()=>{
  let time=0;
  await assert.rejects(demonstrateCount(()=>({ts:new Date(time).toISOString()}),{dwellMs:300,maximumGapMs:5000},
    async()=>{},async()=>{time+=1700;},()=>time),/agreed step gap/);
  assert.equal(time,5100);
});

function poseJourney({wrongOutput=false}={}) {
  const fingers=['thumb','index','middle','ring','little'];
  const hands=[['left',[1,0,1,1,1]],['right',[0,1,1,1,1]]];
  const condition={hands:hands.map(([handedness,values])=>({handedness,
    all:fingers.map((finger,i)=>({finger,operator:'eq',value:values[i]===0?'straight':'bent'}))}))};
  const action={kind:'pose',hands:hands.map(([side,values])=>({side,
    fingers:Object.fromEntries(fingers.map((finger,i)=>[finger,values[i]]))}))};
  if(wrongOutput)action.hands[0].fingers.index=1;
  const motion={handAction:{schemaVersion:1,action}};
  const native=[{type:'native_audio_received'}, {type:'session.output_transcript.delta',delta:'Here is the pose.'}];
  let reads=0;
  const commands=[], renders=[];
  const context={report:{}, page:{evaluate:async()=>native},
    memory:async()=>({'coordination.evidence':{},'task.actions':1}),
    observe:async(_type,payload)=>payload, wait:async()=>{}, zero:async()=>{},
    propose:async(_name,verify)=>verify({checks:[{handPose:condition}],reactions:{rules:[{handPose:condition}]}}),
    ready:async()=>{}, accept:async()=>{}, complete:async()=>{}, checkpoint:async()=>{},
    history:async()=>++reads===1?[]:[{id:'pose-output',type:'resp.behaviour_plan',payload:JSON.stringify({motion})}],
    render:async(name,kind)=>renders.push({name,kind}),
    command:async(name,status,options)=>{
      assert.equal(options.silent,undefined,'Live action must not demand strict silence');
      options.verify(motion);commands.push({name,status});
    }};
  return {context,commands,renders};
}

test('Live pose journey permits native speech while checking task and immediate output',async()=>{
  const trial=poseJourney();await runHandScenario('L-02',trial.context);
  assert.deepEqual(trial.commands,[{name:'poseshow',status:'ACCEPTED'}]);
  assert.deepEqual(trial.renders,[{name:'task-L-02',kind:'custom_pose'}]);
  assert.equal(trial.context.report.taskOutputs.length,1);
});

test('dropping Live silence does not accept an incorrect finger pose',async()=>{
  const trial=poseJourney({wrongOutput:true});
  await assert.rejects(()=>runHandScenario('L-02',trial.context),/Wrong left index/);
  assert.deepEqual(trial.commands,[]);
});

test('completion without a fresh native requested word cannot pass the spoken journey',()=>{
  const event=(id,speech,origin='NATIVE',complete=true)=>({id,type:'resp.behaviour_plan',payload:JSON.stringify({speech}),provenance:{origin,complete}});
  const prior=event('old','Ready.');
  for(const output of [event('new','Task complete.'),event('new',undefined),event('new','Ready.','SYNTHESIZED'),event('new','Ready.','NATIVE',false)])
    assert.throws(()=>requireReadyContribution([prior,output],['old']),/fresh complete native Ready/);
  assert.throws(()=>requireReadyContribution([event('one','ready ready'),event('two','ready')],[]),/fresh complete native Ready/);
  requireReadyContribution([prior,event('new','Ready.')],['old']);
  assert.throws(()=>requireReadyContribution([event('one','Ready.'),event('two','Ready.')],[]),/fresh complete native Ready/);
});

test('ordinary cue accepts a captured paraphrase and checks the whole native response',()=>{
  const response={purpose:'CONTRIBUTION',status:'CAPTURED',segment:'cue'};
  const segment=speech=>({segment:'cue',complete:true,speech});
  for(const words of ['Got it.','I saw all three.'])requireAcknowledgement([segment(words)],response);
  requireAcknowledgement([segment('I saw'),{...segment('all three.'),segment:'tail'}],response);
  for(const words of [['ready ready','ready'],['I saw all three. I saw all three.']])
    assert.throws(()=>requireAcknowledgement(words.map(segment),response),/Repeated/);
  assert.throws(()=>requireAcknowledgement([segment('Got it.')],{...response,segment:'old'}),/No captured/);
});
