import assert from 'node:assert/strict';
import test from 'node:test';
import {startObservationPumps} from './hand_observation_pump.mjs';

test('capture cadence includes request duration without overlap or a burst',async()=>{
  let time=0,active=0;const starts=[],waits=[];
  const pump=startObservationPumps([async()=>{
    assert.equal(active++,0);starts.push(time);
    if(starts.length===1)time+=750;
    await Promise.resolve();active--;
    if(starts.length===3)pump.running=false;
  }],async ms=>{waits.push(ms);time+=ms;},()=>time);
  await pump.done;
  assert.deepEqual(starts,[500,1250,1750]);assert.deepEqual(waits,[500,0,500]);
  assert.equal(pump.error,null);assert.equal(active,0);
});

test('blocked pointing leaves scene captures fresh and cleanup drains the owned request',async()=>{
  let release,pointRequests=0,sceneCaptures=0;
  const blocked=new Promise(resolve=>{release=resolve;});
  const tick=()=>new Promise(resolve=>setTimeout(resolve,1));
  const pump=startObservationPumps([
    async()=>{pointRequests++;await blocked;},
    async()=>{sceneCaptures++;}
  ],tick);
  try{
    const deadline=Date.now()+1000;
    while(sceneCaptures<5&&Date.now()<deadline)await tick();
    assert.ok(sceneCaptures>=5,'Scene capture was starved');assert.equal(pointRequests,1);
    pump.running=false;const before=sceneCaptures;let drained=false;
    void pump.done.then(()=>{drained=true;});await tick();
    assert.equal(drained,false);release();await pump.done;
    assert.equal(sceneCaptures,before);assert.equal(pump.error,null);
  }finally{pump.running=false;release();await pump.done;}
});
