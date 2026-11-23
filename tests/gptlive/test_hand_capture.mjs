import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {handBudgetCapture} from './hand_budget_capture.mjs';
import * as fixture from './hand_fixtures.mjs';

function runtime(){
  const timers=[],events=[],sent=[];
  class Peer {close(){this.closed=true;}createDataChannel(){return {send:value=>sent.push(value),addEventListener(){}};}}
  const context={RTCPeerConnection:Peer,TextEncoder,performance:{now:()=>0},setTimeout:fn=>{timers.push(fn);},clearTimeout(){},document:{addEventListener(){}},__trial:{mark:(type,fields)=>events.push({type,...fields})}};
  context.window=context;vm.runInNewContext(`(${handBudgetCapture})({sessionSeconds:360,nativeTextBytes:10})`,context);return{context,timers,events,sent};
}
test('native duration and text ceilings close RTC before more data can be sent',()=>{
  for(const byText of [false,true]){const r=runtime(),peer=new r.context.RTCPeerConnection(),channel=peer.createDataChannel();
    if(byText)channel.send('a'.repeat(11));else r.timers[0]();
    assert.equal(peer.closed,true);assert.ok(r.context.__handBudget.exhausted);assert.equal(r.sent.length,0);}
});
test('a second peer cannot silently create another paid session',()=>{
  const r=runtime(),first=new r.context.RTCPeerConnection();assert.throws(()=>new r.context.RTCPeerConnection(),/One paid/);assert.equal(first.closed,true);
});
test('controlled evidence preserves count convention, sides and unknowns',()=>{
  for(let n=0;n<=5;n++)assert.equal(Object.values(fixture.count(n).hands[0].fingerStates).filter(v=>v==='straight').length,n);
  const pose=fixture.custom();assert.equal(pose.hands[0].fingerStates.index,'straight');assert.equal(pose.hands[1].fingerStates.thumb,'straight');
  assert.equal(fixture.point(null).direction,null);assert.equal(fixture.ring(false).hands[0].geometry.tipDistances.thumb_index,.8);
  assert.notEqual(fixture.count(1).observationId,fixture.count(1).observationId);
});
