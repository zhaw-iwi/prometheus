import test from 'node:test';
import assert from 'node:assert/strict';
import {SignEdge,StudyCamera} from '../../../src/main/resources/public/hri-study/camera.js';

const scope={mode:'ANSWER',revision:'1',run:'run',question:'one'};
function release(edge,s=scope){edge.sample(null,0,s);edge.sample(null,0,s);}
function sign(edge,s=scope){return [1,2,3].map(()=>edge.sample('Thumb_Up',.9,s)).filter(Boolean);}
test('a held sign cannot answer at startup, a later question, a revised run or after restart',()=>{
  const edge=new SignEdge();assert.deepEqual(sign(edge),[]);release(edge);assert.equal(sign(edge).length,1);
  assert.deepEqual(sign(edge,{...scope,question:'two'}),[]);assert.deepEqual(sign(edge,{...scope,revision:'2',run:'new'}),[]);
  release(edge,{...scope,question:'two'});assert.equal(sign(edge,{...scope,question:'two'}).length,1);edge.reset();assert.deepEqual(sign(edge),[]);
});
test('recognition needs confidence, three frames and one unchanged scope',()=>{
  const edge=new SignEdge();release(edge);assert.equal(edge.sample('Thumb_Down',.64,scope),null);
  edge.sample('Open_Palm',.9,scope);edge.sample('Open_Palm',.9,scope);
  assert.equal(edge.sample('Open_Palm',.9,{...scope,question:'two'}),null);
  release(edge,{...scope,question:'two'});edge.sample('Open_Palm',.9,{...scope,question:'two'});edge.sample('Open_Palm',.9,{...scope,question:'two'});
  assert.equal(edge.sample('Open_Palm',.9,{...scope,question:'two'}).sign,'OPEN_PALM');
});
function fixture(options={}){
  let stopped=0,closed=0;const statuses=[];
  const stream={getTracks:()=>[{stop(){stopped++;},addEventListener(){}}]};
  const camera=new StudyCamera({video:{play:async()=>{},readyState:0},scope:()=>null,clock:()=>new Date().toISOString(),
    onStatus:(message,ready)=>statuses.push({message,ready}),onFrame(){},media:async()=>stream,models:async()=>({close(){closed++;}}),...options});
  return {camera,statuses,stream,stopped:()=>stopped,closed:()=>closed};
}
test('Stop while permission is pending releases the late stream without starting detectors',async()=>{
  let accept;const f=fixture({media:()=>new Promise(resolve=>accept=resolve),models:()=>{throw Error('Must not load');}});
  const start=f.camera.start();f.camera.stop();accept(f.stream);await start;assert.equal(f.stopped(),1);assert.equal(f.camera.running,false);
});
test('Stop while models load closes the late models; detector failure releases the track',async()=>{
  let accept;const f=fixture({models:()=>new Promise(resolve=>accept=resolve)});const start=f.camera.start();await new Promise(resolve=>setImmediate(resolve));
  f.camera.stop();let disposed=false;accept({close(){disposed=true;}});await start;assert.equal(disposed,true);assert.equal(f.stopped(),1);assert.equal(f.camera.running,false);
  const failed=fixture({models:async()=>{throw Error('download');}});await failed.camera.start();assert.equal(failed.stopped(),1);assert.match(failed.statuses.at(-1).message,/startup failed/);
});
test('permission denial is actionable and never reports running',async()=>{
  const f=fixture({media:async()=>{const e=Error();e.name='NotAllowedError';throw e;}});await f.camera.start();
  assert.equal(f.camera.running,false);assert.match(f.statuses.at(-1).message,/permission was denied/);
});

test('low-confidence or unclassified hands cannot release a consumed gesture',()=>{
  const edge=new SignEdge();release(edge);assert.equal(sign(edge).length,1);
  for(let i=0;i<4;i++)edge.sample('Thumb_Up',.4,scope,true);assert.deepEqual(sign(edge),[]);
  for(let i=0;i<4;i++)edge.sample(null,0,scope,true);assert.deepEqual(sign(edge),[]);
  release(edge);assert.equal(sign(edge).length,1);
});
