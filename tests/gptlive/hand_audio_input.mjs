// Run in the browser. Compact transfer avoids a CDP object per PCM byte, which
// can stall the page long enough to expire the production Live status poll.
export async function outboundHandAudio() {
  const peer=globalThis.__handBudget.peers[0],mic=globalThis.__trial.mic;
  const sender=peer?.getSenders().find(s=>s.track?.id===mic?.id);
  if(!sender)return {trackMatched:false};
  const stats=[...(await sender.getStats()).values()];
  const rtp=stats.find(s=>s.type==='outbound-rtp'&&s.kind==='audio');
  const source=stats.find(s=>s.type==='media-source'&&s.trackIdentifier===mic.id);
  return {trackMatched:true,enabled:mic.enabled,readyState:mic.readyState,ssrc:rtp?.ssrc,
    packetsSent:rtp?.packetsSent,bytesSent:rtp?.bytesSent,totalAudioEnergy:source?.totalAudioEnergy};
}
export function handReplyObserved({index,startMs}) {
  const events=globalThis.__trial.events.slice(index);
  return events.some(e=>e.type==='session.output_transcript.delta'&&e.delta?.trim())
    &&events.some(e=>e.type==='output_voice'&&e.at>=startMs);
}

export async function playHandStimulus({pcmBase64,name}) {
  const bytes=Uint8Array.from(atob(pcmBase64),value=>value.charCodeAt(0));
  const pcm=new Int16Array(bytes.buffer),samples=Float32Array.from(pcm,value=>value/32768);
  __handBudget.inputSeconds+=samples.length/24000;
  if(__handBudget.inputSeconds>360)throw Error('Native input audio limit');
  const buffer=__trial.context.createBuffer(1,samples.length,24000);buffer.copyToChannel(samples,0);
  let last=samples.length-1;while(last>0&&Math.abs(samples[last])<.01)last--;
  const source=__trial.context.createBufferSource();source.buffer=buffer;source.connect(__trial.input);
  const at=performance.now();__trial.mark('input_start',{name});
  await new Promise(resolve=>{source.onended=resolve;source.start();});
  const speechEndMs=at+last/24;__trial.mark('input_end',{name,speechEndMs});
  return {startMs:at,speechEndMs,bufferEndMs:performance.now(),sampleRate:24000,
    samples:samples.length,tailSilenceMs:(samples.length-1-last)/24,micEnabledAtEnd:__trial.mic?.enabled};
}
