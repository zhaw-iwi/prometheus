import {handAction,handTiming} from './actions.js';

// One finite renderer lease. It waits for each scoped authorization, never catches
// up missed poses, and never treats history or a lost connection as completion.
export class HandSequencePlayer {
  constructor(action,{request,clock,wake=()=>{}}){
    this.steps=handAction({schemaVersion:1,action}).steps;this.request=request;this.clock=clock;this.wake=wake;
    this.status='starting';this.index=-1;this.generation=0;this.chain=Promise.resolve();this.lastSample=null;
  }
  start(){this.send('START',0);}
  send(operation,index){
    const generation=this.generation;this.status=operation==='START'?'starting':'waiting';
    this.chain=this.chain.then(()=>this.request(operation,index)).then(receipt=>{
      if(generation!==this.generation)return;
      if(this.id&&receipt.id!==this.id)throw new Error('Changed execution identity');this.id=receipt.id;
      if(receipt.status==='COMPLETED'){this.status='completed';this.wake();return;}
      if(receipt.status!=='RUNNING'){this.status=receipt.status==='INTERRUPTED'?'interrupted':'unknown';this.reason=receipt.reason;this.wake();return;}
      if(receipt.step!==index||receipt.current?.holdMs!==this.steps[index].holdMs)throw new Error('Changed sequence step');
      this.action=handAction({schemaVersion:1,action:receipt.current.action});if(this.action.kind==='sequence')throw new Error('Nested sequence');
      this.index=index;this.started=this.clock();this.lastSample=this.started;this.status='running';this.wake();
    }).catch(()=>{if(generation===this.generation){this.status='unknown';this.reason='connection_lost';this.wake();}});
  }
  cancel(reason='interrupted'){
    if(['completed','interrupted','unknown'].includes(this.status))return;
    this.generation++;this.status='interrupted';this.reason=reason;
    this.chain=this.chain.then(()=>this.request('CANCEL',Math.max(0,this.index))).catch(()=>{});this.wake();
  }
  sample(now){
    if(this.status==='running'&&this.lastSample!==null&&now-this.lastSample>1000)this.cancel('rendering_gap');
    this.lastSample=now;
    if(this.status!=='running')return {action:this.action||this.steps[0].action,blend:0,phase:['starting','waiting'].includes(this.status)?'waiting':'released',active:['starting','waiting'].includes(this.status),index:this.index,status:this.status,total:this.steps.length};
    const elapsed=Math.max(0,now-this.started),{enter,release}=handTiming,hold=this.steps[this.index].holdMs,end=enter+hold+release;
    if(elapsed>=end){this.send(this.index+1===this.steps.length?'COMPLETE':'STEP',this.index+1===this.steps.length?this.index:this.index+1);return this.sample(now);}
    return {action:this.action,blend:elapsed<enter?elapsed/enter:elapsed<enter+hold?1:Math.max(0,1-(elapsed-enter-hold)/release),phase:elapsed<enter?'entering':elapsed<enter+hold?'holding':'releasing',active:true,index:this.index,status:this.status,total:this.steps.length};
  }
}
