import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {playHandStimulus,handReplyObserved} from './hand_audio_input.mjs';
import {commandTiming} from './hand_command_timing.mjs';

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
