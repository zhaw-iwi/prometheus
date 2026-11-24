import assert from 'node:assert/strict';
import test from 'node:test';
import {startObservationPumps} from './hand_observation_pump.mjs';

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
