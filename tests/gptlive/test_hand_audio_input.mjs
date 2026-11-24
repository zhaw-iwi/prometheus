import {spokenClarification} from './hand_audio_input.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {playHandStimulus,handReplyObserved} from './hand_audio_input.mjs';
import {commandTiming,controlTimings} from './hand_command_timing.mjs';

test('only exact-turn send and ACK bracket each observed fragment, retaining overlap',()=>{
  const turn={commandSource:{id:'pose-turn'},clock:{sent:100,received:104,serverTimeMs:1000},
    events:[110,121,132].map(at=>({at,type:'session.output_transcript.delta',delta:'word',event_id:'e'+at}))};
  const traces=[{sourceId:'old',phase:'command_accepted_ack',serverMs:999,sequence:1},
    {sourceId:'pose-turn',phase:'command_accepted_send',serverMs:1015,sequence:2},
    {sourceId:'pose-turn',phase:'command_accepted_ack',serverMs:1021,sequence:3}];
  const timing=commandTiming(turn,traces,new Map());
  assert.equal(timing.delivery.length,2);
  assert.deepEqual(timing.observations.map(e=>e.observed),['observed_before_result_sent','undetermined','observed_after_result_ack']);
  assert.ok(commandTiming(turn,[],new Map()).observations.every(e=>e.observed==='undetermined'));
});

test('renderer timing requires the exact pose and uses the rendered acknowledgement',()=>{
  const turn={commandSource:{id:'user',position:4},memory:{'command.lastResult':{sourcePosition:4,renderedEvent:'pose'}},
    clock:{sent:100,received:104,serverTimeMs:1000},events:[120,150].map(at=>({at,type:'output_voice'}))};
  const traces=[['old','command_pose_receipt_committed',1000],['user','command_accepted_ack',1005],
    ['pose','command_pose_receipt_committed',1024],['user','command_rendered_send',1026],['user','command_rendered_ack',1032]]
    .map(([sourceId,phase,serverMs],sequence)=>({sourceId,phase,serverMs,sequence}));
  const {renderer}=commandTiming(turn,traces,new Map());
  assert.equal(renderer.committedMs,1024);assert.equal(renderer.sendMs,1026);assert.equal(renderer.ackMs,1032);
  assert.deepEqual(renderer.observations.map(e=>e.observed),['observed_before_result_sent','observed_after_result_ack']);
  turn.commandSource.position=8;
  assert.equal(commandTiming(turn,traces,new Map()).renderer,null);
});

test('arming joins its comment by occasion ID and brackets both replies, including one before commit',()=>{
  const at=ms=>new Date(ms).toISOString();
  const memory={'core.task.response':{id:'arm',created:at(1020),expires:at(2000)},'task.response':{id:'arm',purpose:'ARMING'}};
  const append=(sequence,phase,serverMs,sourceId)=>({sequence,sourceId,phase,serverMs,type:'session.commentary.append'});
  const report={checkpoints:[],turns:[{clock:{sent:100,received:102,serverTimeMs:1000},memory}],
    browser:[111,145,250].map(at=>({at,type:'output_voice'})),finalSession:{handle:'s',context:{recent:[]}},
    diagnostics:{sessions:[{handle:'s',server:{context:{recent:[append(1,'send',1001,'other'),
      append(2,'send',1030,'arm'),append(3,'ack',1035,'arm'),append(4,'task_narration_withdrawn',1100,'arm')]}}}]}};
  const [r]=controlTimings(report);
  assert.equal(r.purpose,'arming');assert.deepEqual(r.windowMs,[1000,1100]);
  assert.deepEqual(r.observations.map(e=>e.observed),['observed_before_result_sent','observed_after_result_ack']);
  assert.deepEqual(r.delivery.map(e=>e.sequence),[2,3,4]);
});

test('a caption or received audio alone cannot finish waiting for native playback',()=>{
  globalThis.__trial={events:[{type:'output_voice',at:10},{type:'session.output_transcript.delta',delta:'Old reply',at:11}]};
  const scope={index:2,startMs:100};
  try{
    __trial.events.push({type:'session.output_transcript.delta',delta:'Hmm.',at:101});
    assert.equal(handReplyObserved(scope),false);
    __trial.events.push({type:'native_audio_received',at:102});assert.equal(handReplyObserved(scope),false);
    __trial.events.push({type:'output_voice',at:99});assert.equal(handReplyObserved(scope),false);
    __trial.events.push({type:'output_voice',at:120});assert.equal(handReplyObserved(scope),true);
    assert.equal(handReplyObserved({index:5,startMs:100}),false,'Playback alone is not a reply caption');
  }finally{delete globalThis.__trial;}
});

test('long PCM stimulus transfers losslessly without starving the browser poll',async()=>{
  const browser=await chromium.launch({headless:true});
  try{
    const page=await browser.newPage();
    await page.evaluate(()=>{
      globalThis.__handBudget={inputSeconds:0};globalThis.__marks=[];
      globalThis.__trial={input:{},mark:(type,details)=>__marks.push({type,...details}),context:{
        createBuffer:(_channels,length,rate)=>({length,rate,copyToChannel:samples=>{globalThis.__samples=samples;}}),
        createBufferSource:()=>({connect(){},start(){setTimeout(()=>this.onended(),30);}})
      }};
      globalThis.__polls=0;globalThis.__pollTimer=setInterval(()=>__polls++,10);
    });
    const pcm=new Int16Array(24000*45);for(let i=0;i<pcm.length-24000;i++)pcm[i]=(i*71)%65536-32768;
    const bytes=Buffer.from(pcm.buffer),started=Date.now();
    const result=await page.evaluate(playHandStimulus,{pcmBase64:bytes.toString('base64'),name:'long-clip'});
    assert.ok(Date.now()-started<3000,'Must leave ample margin before the 20-second Live status deadline');
    const actual=await page.evaluate(()=>{clearInterval(__pollTimer);const restored=Int16Array.from(__samples,value=>value*32768);
      let binary='';const bytes=new Uint8Array(restored.buffer);for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
      return {base64:btoa(binary),seconds:__handBudget.inputSeconds,polls:__polls,marks:__marks};});
    assert.equal(createHash('sha256').update(Buffer.from(actual.base64,'base64')).digest('hex'),createHash('sha256').update(bytes).digest('hex'));
    assert.equal(actual.seconds,45);assert.ok(actual.polls>0);assert.deepEqual(actual.marks.map(m=>m.type),['input_start','input_end']);
    assert.ok(Math.abs(result.speechEndMs-result.startMs-44000)<1);
    await page.evaluate(()=>{__handBudget.inputSeconds=359;});
    await assert.rejects(()=>page.evaluate(playHandStimulus,{pcmBase64:bytes.toString('base64'),name:'over-budget'}),/Native input audio limit/);
  }finally{await browser.close();}
});

test('the visitor waits through a backchannel for the actual clarification prompt',()=>{
  const event=(id,speech,complete=true)=>({id,type:'resp.behaviour_plan',provenance:{origin:'NATIVE',complete},payload:JSON.stringify({speech})});
  const early=[event('prior','Which response?'),event('ack','Mm-hmm.'),event('partial','How should',false)];
  assert.equal(spokenClarification(early,['prior']),undefined);
  for(const speech of ['What would you like me to do?', 'Please tell me how to respond.'])
    assert.equal(spokenClarification([...early,event('ask',speech)],['prior']),speech);
});
