// Harness-only regression: this is not provider, detector or application acceptance.
import assert from 'node:assert/strict';
import test from 'node:test';
import {runHandScenario,requireReadyContribution,demonstrateCount} from './hand_scenarios.mjs';

test('count dwell requires distinct captures spanning the hold despite slow acknowledgements',async()=>{
  for(const acknowledgementMs of [0,900]){
    let clock=0;const captures=[];
    await demonstrateCount(()=>({ts:new Date(clock).toISOString()}),300,async make=>{
      const sample=make();captures.push(Date.parse(sample.ts));clock+=acknowledgementMs;return sample;
    },async ms=>{clock+=ms;});
    assert.ok(captures.length>=2);
    assert.ok(captures.at(-1)-captures[0]>=300);
    assert.equal(captures.length,acknowledgementMs?2:3);
  }
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
  requireReadyContribution([prior,event('new','Ready.')],['old']);
  assert.throws(()=>requireReadyContribution([event('one','Ready.'),event('two','Ready.')],[]),/fresh complete native Ready/);
});
