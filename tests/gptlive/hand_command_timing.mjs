// The browser and backend sample different clocks. Keep a bracket, including
// drift, and report receipt of evidence rather than the model's decision time.
export function commandTiming(turn, traces, diagnostics) {
  const source=turn.commandSource?.id;
  const delivery=source ? traces.filter(e=>e.sourceId===source && /^command_.*_(send|ack)$/.test(e.phase))
    .sort((a,b)=>a.sequence-b.sequence) : [];
  const sends=delivery.filter(e=>e.phase.endsWith('_send'));
  const acks=delivery.filter(e=>e.phase.endsWith('_ack'));
  const events=turn.events||[];
  const {sent,received,serverTimeMs}=turn.clock||{};
  const clockValid=Number.isFinite(sent)&&Number.isFinite(received)&&Number.isFinite(serverTimeMs)
    &&received>=sent&&received-sent<=1000;
  const observations=events.filter(e=>e.type==='output_voice'||(e.type==='session.output_transcript.delta'&&e.delta?.trim())).map(e=>{
    const provider=diagnostics.get(e.event_id);
    const error=1+Math.max(Math.abs(e.at-sent),Math.abs(e.at-received))*.0001;
    const earliest=clockValid?serverTimeMs+e.at-received-error:null;
    const latest=clockValid?serverTimeMs+e.at-sent+error:null;
    let observed='undetermined';
    if(sends.length && latest!==null && latest<sends[0].serverMs) observed='observed_before_result_sent';
    else if(acks.length && earliest!==null && earliest>acks[0].serverMs) observed='observed_after_result_ack';
    return {type:e.type,delta:e.delta,eventId:e.event_id,browserMs:e.at,serverRangeMs:[earliest,latest],
      sidebandReceiptMs:provider?.serverMs??null,observed};
  });
  return {source,delivery,observations};
}
