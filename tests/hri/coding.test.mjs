import test from 'node:test';
import assert from 'node:assert/strict';
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
