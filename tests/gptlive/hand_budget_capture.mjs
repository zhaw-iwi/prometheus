// Paid harness only: receive real media, retain it, and close the owned RTC at its limit.
// Serialized as an init script after trialCapture; no synthetic provider messages.
export function handBudgetCapture(limits) {
  const budget=window.__handBudget={peers:[],recordings:[],inputSeconds:0,textBytes:0,started:null,exhausted:null};
  const fail=reason=>{if(budget.exhausted)return;budget.exhausted=reason;__trial.mark('hand_budget_exhausted',{reason});for(const peer of budget.peers)peer.close();};
  const count=value=>{budget.textBytes+=new TextEncoder().encode(value).length;if(budget.textBytes>limits.nativeTextBytes)fail('native text limit');};
  const Parent=window.RTCPeerConnection;
  window.RTCPeerConnection=class extends Parent {
    constructor(...args){super(...args);budget.peers.push(this);if(budget.peers.length>1){fail('second RTC peer');throw Error('One paid native session only');}
      budget.started=performance.now();budget.timer=setTimeout(()=>fail('native session duration'),limits.sessionSeconds*1000);}
    createDataChannel(...args){const channel=super.createDataChannel(...args),send=channel.send.bind(channel);
      channel.send=data=>{count(String(data));if(!budget.exhausted)send(data);};
      channel.addEventListener('message',event=>count(String(event.data)));return channel;}
  };
  document.addEventListener('playing',event=>{
    const audio=event.target;if(!(audio instanceof HTMLAudioElement)||audio.dataset.handCaptured)return;audio.dataset.handCaptured='true';
    const stream=audio.captureStream(),chunks=[],record={chunks,bytes:0,mime:'audio/webm;codecs=opus'};
    const recorder=new MediaRecorder(stream,{mimeType:record.mime,audioBitsPerSecond:64000});record.recorder=recorder;budget.recordings.push(record);
    recorder.ondataavailable=event=>{record.bytes+=event.data.size;if(record.bytes>8*1024*1024)fail('native recording limit');else chunks.push(event.data);};recorder.start(1000);
    const context=new AudioContext(),source=context.createMediaStreamSource(stream),meter=context.createAnalyser();source.connect(meter);
    const samples=new Float32Array(meter.fftSize);let voiced=false;
    record.meter=setInterval(()=>{meter.getFloatTimeDomainData(samples);const rms=Math.sqrt(samples.reduce((a,v)=>a+v*v,0)/samples.length),active=rms>.003;
      if(active)budget.lastNativeVoice=performance.now();
      if(active&&!voiced)__trial.mark('native_audio_received',{rms});voiced=active;},20);record.context=context;
  },true);
  budget.finish=async()=>{
    clearTimeout(budget.timer);const files=[];
    for(const record of budget.recordings){clearInterval(record.meter);if(record.recorder.state!=='inactive')await new Promise(resolve=>{record.recorder.onstop=resolve;record.recorder.stop();});
      await record.context.close();const blob=new Blob(record.chunks,{type:record.mime});
      const data=await new Promise(resolve=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.readAsDataURL(blob);});files.push(data);}
    return {files,inputSeconds:budget.inputSeconds,textBytes:budget.textBytes,exhausted:budget.exhausted,
      rtcElapsedSeconds:budget.started===null?0:(performance.now()-budget.started)/1000};
  };
}
