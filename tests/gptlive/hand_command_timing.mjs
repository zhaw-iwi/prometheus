// A silent-period timeout can leave the backend receipt available only during teardown.
export function interruptedCommandSource(turn,history,receipt) {
  const position=receipt?.sourcePosition;
  if(!Number.isInteger(position)||position<0)return null;
  const source=history?.[position];
  if(source?.type!=='obs.user_utterance'||!source.id||turn.beforeIds.includes(source.id))return null;
  const words=text=>text?.toLowerCase().match(/[\p{L}\p{N}]+/gu)?.join(' ');
  const intended=words(turn.intendedInput);
  return intended&&words(source.payload)===intended?{id:source.id,position}:null;
}

// The browser and backend sample different clocks. Keep a bracket, including
// drift, and report receipt of evidence rather than the model's decision time.
export function commandTiming(turn, traces, diagnostics) {
  const source=turn.commandSource?.id;
  const delivery=source ? traces.filter(e=>e.sourceId===source && /^command_.*_(send|ack)$/.test(e.phase))
    .sort((a,b)=>a.sequence-b.sequence) : [];
  const receipt=turn.memory?.['command.lastResult'];
  const poseId=source && receipt && receipt.sourcePosition===turn.commandSource.position ? receipt.renderedEvent : null;
  const committed=poseId ? traces.find(e=>e.sourceId===poseId && e.phase==='command_pose_receipt_committed') : null;
  const send=delivery.find(e=>e.phase==='command_rendered_send'), ack=delivery.find(e=>e.phase==='command_rendered_ack');
  const renderer=poseId ? {poseId,committedMs:committed?.serverMs??null,sendMs:send?.serverMs??null,
    ackMs:ack?.serverMs??null,observations:observations(turn.events||[],turn.clock,diagnostics,send?.serverMs,ack?.serverMs)} : null;
  return {source,delivery,renderer,observations:observations(turn.events||[],turn.clock,diagnostics,
    delivery.find(e=>e.phase.endsWith('_send'))?.serverMs,delivery.find(e=>e.phase.endsWith('_ack'))?.serverMs)};
}

function observations(events,clock,diagnostics,sendMs,ackMs) {
  const {sent,received,serverTimeMs}=clock||{};
  const clockValid=Number.isFinite(sent)&&Number.isFinite(received)&&Number.isFinite(serverTimeMs)
    &&received>=sent&&received-sent<=1000;
  return events.filter(e=>e.type==='output_voice'||(e.type==='session.output_transcript.delta'&&e.delta?.trim())).map(e=>{
    const provider=diagnostics.get(e.event_id);
    const error=1+Math.max(Math.abs(e.at-sent),Math.abs(e.at-received))*.0001;
    const earliest=clockValid?serverTimeMs+e.at-received-error:null;
    const latest=clockValid?serverTimeMs+e.at-sent+error:null;
    let observed='undetermined';
    if(Number.isFinite(sendMs) && latest!==null && latest<sendMs) observed='observed_before_result_sent';
    else if(Number.isFinite(ackMs) && earliest!==null && earliest>ackMs) observed='observed_after_result_ack';
    return {type:e.type,delta:e.delta,eventId:e.event_id,browserMs:e.at,serverRangeMs:[earliest,latest],
      sidebandReceiptMs:provider?.serverMs??null,observed};
  });
}

// Reusable against the original journey.json. A time window is evidence of ordering,
// not an attribution of a native sentence to an internal model decision.
export function controlTimings(report) {
  const server=report.diagnostics?.sessions?.find(s=>s.handle===report.finalSession?.handle)?.server;
  const traces=new Map(), diagnostics=new Map(), occasions=new Map();
  for(const status of [server,report.finalSession]) {
    for(const e of status?.context?.recent||[]) traces.set(e.sequence,e);
    for(const e of status?.recent||[]) if(e.eventId && e.type==='session.output_transcript.delta') diagnostics.set(e.eventId,e);
  }
  const turns=report.turns.filter(t=>Number.isFinite(t.clock?.serverTimeMs));
  const memories=[...report.checkpoints.map(c=>c.memory),...report.turns.map(t=>t.memory),report.accepted,report.finalStorage];
  for(const memory of memories) {
    const r=memory?.['core.task.response'], task=memory?.['task.response'];
    const arming=r?.id && r.id===task?.id && task.purpose==='ARMING';
    const draft=memory?.['coordination.draft'];
    const review=r?.proposalReview && draft?.questions?.length===0
      && draft.checks.length===Object.keys(memory['coordination.evidence']||{}).length;
    if(!r?.id || !(review || arming || r.consentRepair)) continue;
    const purpose=arming?'arming':r.consentRepair?'consent_repair':'proposal_review';
    occasions.set(r.id,{id:r.id,purpose,createdMs:Date.parse(r.created),expiresMs:Date.parse(r.expires)});
  }
  return [...occasions.values()].sort((a,b)=>a.createdMs-b.createdMs).map(occasion=>{
    const own=[...traces.values()].filter(e=>e.sourceId===occasion.id).sort((a,b)=>a.sequence-b.sequence);
    const commentary=own.filter(e=>e.type==='session.commentary.append');
    const send=commentary.find(e=>e.phase==='send'), ack=commentary.find(e=>e.phase==='ack');
    const turn=turns.findLast(t=>t.clock.serverTimeMs<=occasion.createdMs);
    const end=Math.min(occasion.expiresMs,turns.find(t=>t.clock.serverTimeMs>occasion.createdMs)?.clock.serverTimeMs??Infinity,
      commentary.find(e=>e.phase==='task_narration_withdrawn')?.serverMs??Infinity);
    const start=occasion.purpose!=='proposal_review' && turn ? turn.clock.serverTimeMs : occasion.createdMs;
    const samples=observations(report.browser||[],turn?.clock,diagnostics,send?.serverMs,ack?.serverMs)
      .filter(e=>e.serverRangeMs[0]!==null && e.serverRangeMs[1]>=start && e.serverRangeMs[0]<=end);
    return {...occasion,windowMs:[start,end],clock:turn?.clock,delivery:own,observations:samples};
  });
}
