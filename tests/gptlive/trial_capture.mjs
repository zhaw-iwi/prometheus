// Shared real-browser media instrumentation; synthetic visitor input, real provider output.
export function trialCapture() {
    window.__trial = {events:[],mark(type,fields={}) {this.events.push({type,...fields,at:performance.now()}); if(this.events.length>12000) throw Error('Trial diagnostic budget exceeded');}};
    const originalPeer = window.RTCPeerConnection;
    window.RTCPeerConnection = class extends originalPeer {
      constructor(...args) {
        super(...args);
        this.addEventListener('connectionstatechange',()=>__trial.mark('peer_connection',{state:this.connectionState,ice:this.iceConnectionState}));
      }
      createDataChannel(...args) {
        const channel = super.createDataChannel(...args);
        channel.addEventListener('message', e => {
          const value = JSON.parse(e.data);
          if (value.type === 'session.output_transcript.delta') __trial.lastOutputCaption = performance.now();
          if (value.type?.includes('transcript')) __trial.mark(value.type,{event_id:value.event_id,item_id:value.item_id,delta:value.delta,transcript:value.transcript,start_ms:value.start_ms,end_ms:value.end_ms});
        }); return channel;
      }
    };
    Object.defineProperty(navigator,'mediaDevices',{configurable:true,value:{
      async getUserMedia() {
        __trial.context ||= new AudioContext({sampleRate:24000}); await __trial.context.resume();
        __trial.input = __trial.context.createMediaStreamDestination();
        __trial.mic = __trial.input.stream.getAudioTracks()[0]; return __trial.input.stream;
      }, async enumerateDevices(){return [];},getSupportedConstraints(){return {};},addEventListener(){},removeEventListener(){}
    }});
    for (const type of ['ended','error','waiting','stalled']) document.addEventListener(type,event=>{
      const audio=event.target;if(audio instanceof HTMLAudioElement) __trial.mark('media_'+type,{element:audio.id,time:audio.currentTime,duration:audio.duration,code:audio.error?.code});
    },true);
    document.addEventListener('playing', event => {
      const audio = event.target; if (!(audio instanceof HTMLAudioElement)) return;
      __trial.mark('media_playing',{element:audio.id,time:audio.currentTime,duration:audio.duration});
      const context = new AudioContext(), source = context.createMediaStreamSource(audio.captureStream()), meter = context.createAnalyser();
      const gain = context.createGain(); gain.gain.value = 0; source.connect(meter).connect(gain).connect(context.destination);
      const samples = new Float32Array(meter.fftSize); let voiced = false;
      const timer = setInterval(() => {
        meter.getFloatTimeDomainData(samples);
        const rms = Math.sqrt(samples.reduce((s,v)=>s+v*v,0)/samples.length), active = rms>.015 && !audio.muted && !audio.paused && audio.volume>0;
        if (active) __trial.lastVoice = performance.now();
        if (active && !voiced) __trial.mark('output_voice',{element:audio.id,rms,micEnabled:__trial.mic?.enabled});
        voiced = active;
      },20);
      audio.addEventListener('ended',()=>{clearInterval(timer);void context.close();},{once:true});
      void context.resume();
    },true);
    document.addEventListener('DOMContentLoaded',()=>{
      const status = document.getElementById('listen_status');
      if (status) new MutationObserver(()=>__trial.mark('input_gate',{status:status.textContent,enabled:__trial.mic?.enabled})).observe(status,{childList:true,subtree:true});
    });
}
