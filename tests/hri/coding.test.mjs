import test from 'node:test';
import assert from 'node:assert/strict';
import {summarize} from './analyze.mjs';
// Independently authored analyst labels; production free text still requires human coding.
const cases=[
  {name:'accepted but wrong',communicatedLimit:5,acceptedLimit:3,predictedLimit:3,observedLimit:3,expected:[0,1,1]},
  {name:'faithful but misunderstood',communicatedLimit:5,acceptedLimit:5,predictedLimit:3,observedLimit:5,expected:[1,0,1]},
  {name:'faithful but poorly executed',communicatedLimit:5,acceptedLimit:5,predictedLimit:5,observedLimit:6,expected:[1,1,0]},
  {name:'unspoken preference is not a communicated requirement',communicatedLimit:null,privateLimit:3,acceptedLimit:5,predictedLimit:5,observedLimit:5,expected:[null,1,1]},
  {name:'no agreement stays in denominator',communicatedLimit:5,acceptedLimit:null,predictedLimit:null,observedLimit:null,expected:[null,null,null]},
];
for(const c of cases)test(c.name,()=>{
  const compare=(a,b)=>a==null||b==null?null:Number(a===b);
  assert.deepEqual([compare(c.communicatedLimit,c.acceptedLimit),compare(c.predictedLimit,c.acceptedLimit),compare(c.observedLimit,c.acceptedLimit)],c.expected);
});
test('dataset rehearsal preserves replacement denominators and rejects broken links',()=>{
  const attempt=(id,stage,predecessorId=null)=>({session:{id,stage,predecessorId,participantId:'p1',mode:'SYNTHETIC',scenario:'guess',materialsVersion:'v2'},
    assessment:{acceptedAgreements:0,umuxLite:{complete:false}},agreements:[],answers:[],records:[{kind:'METADATA'}],manifest:{recordingComplete:true}});
  const dataset={schema:'hri-dataset-v1',mode:'SYNTHETIC',attempts:[attempt('a1','REPLACED'),attempt('a2','COMPLETE','a1')],
    counts:{attempts:2,participants:1,attemptStages:{REPLACED:1,COMPLETE:1},initialAssignments:{guess:1}}};
  assert.equal(summarize(dataset).participants,1);dataset.attempts[1].session.scenario='signs';assert.throws(()=>summarize(dataset));
  dataset.attempts[1].session.scenario='guess';dataset.counts.participants=2;assert.throws(()=>summarize(dataset));
});
