import test from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {trialCapture} from './trial_capture.mjs';
import {handBudgetCapture} from './hand_budget_capture.mjs';

test('received audio is retained and detected even with muted HTML playback, without provider access',async()=>{
  const browser=await chromium.launch({headless:true,args:['--autoplay-policy=no-user-gesture-required']});
  try{const page=await browser.newPage();await page.setContent('<audio id="native" autoplay></audio>');
    await page.evaluate(trialCapture);await page.evaluate(handBudgetCapture,{sessionSeconds:360,nativeTextBytes:250000});
    await page.evaluate(async()=>{const context=new AudioContext(),osc=context.createOscillator(),gain=context.createGain(),stream=context.createMediaStreamDestination();
      gain.gain.value=.2;osc.connect(gain).connect(stream);const audio=document.querySelector('audio');audio.muted=true;audio.srcObject=stream.stream;
      window.fixtureAudio={context,osc};await context.resume();osc.start();await audio.play();});
    await page.waitForFunction(()=>__trial.events.some(e=>e.type==='native_audio_received'));
    assert.ok(await page.evaluate(()=>__handBudget.lastNativeVoice>0));
    await page.waitForTimeout(1100);const result=await page.evaluate(()=>__handBudget.finish());
    assert.equal(result.files.length,1);assert.ok(result.files[0].length>1000);assert.equal(result.exhausted,null);
    assert.equal(await page.evaluate(()=>__trial.events.filter(e=>e.type==='output_voice').length),0);
  }finally{await browser.close();}
});
