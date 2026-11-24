import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chromium} from '@playwright/test';
import {playHandStimulus} from './hand_audio_input.mjs';

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
