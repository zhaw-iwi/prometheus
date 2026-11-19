export async function hardware(context) {
  await context.route('https://transcription.invalid/offer', r => r.fulfill({contentType:'application/sdp', body:'synthetic-answer'}));
  await context.addInitScript(() => {
    window.__nativeContext = new AudioContext();
    const remote = __nativeContext.createMediaStreamDestination(), oscillator = __nativeContext.createOscillator();
    window.__nativeGain = __nativeContext.createGain(); __nativeGain.gain.value = 0;
    oscillator.connect(__nativeGain).connect(remote); oscillator.start();
    Object.defineProperty(navigator, 'mediaDevices', {configurable:true, value:{
      async getUserMedia() {await __nativeContext.resume(); const input = __nativeContext.createMediaStreamDestination();
        window.__nativeMic = input.stream.getAudioTracks()[0];
        const noise = __nativeContext.createOscillator(); noise.frequency.value = 170;
        window.__nativeInputGain = __nativeContext.createGain(); __nativeInputGain.gain.value = 0;
        noise.connect(__nativeInputGain).connect(input); noise.start(); return input.stream;},
      async enumerateDevices() {return [];}, getSupportedConstraints() {return {};}, addEventListener() {}, removeEventListener() {}
    }});
    window.RTCPeerConnection = class extends EventTarget {
      constructor() {super(); this.iceGatheringState = 'complete'; this.connectionState = 'new'; this.senders = [];}
      createDataChannel() {const channel = new EventTarget(); channel.readyState = 'connecting'; channel.send = () => {}; channel.close = () => {};
        window.__nativeChannel = this.channel = channel; window.__nativeReceive = event => channel.onmessage?.({data:JSON.stringify(event)}); return channel;}
      addTrack(track) {this.senders.push({track});} getSenders() {return this.senders;}
      async createOffer() {return {type:'offer', sdp:'v=0 synthetic-offer'};} async setLocalDescription(value) {this.localDescription = value;}
      async setRemoteDescription(value) {
        this.connectionState = 'connected'; this.channel.readyState = 'open';
        this.channel.onmessage?.({data:'{"type":"session.started"}'});
        queueMicrotask(() => this.channel.dispatchEvent(new Event('open')));
        if (value.sdp.startsWith('v=0')) this.ontrack?.({streams:[remote.stream], track:remote.stream.getAudioTracks()[0]});
      }
      async getStats() {return new Map();} close() {this.connectionState = 'closed';}
    };
  });
}
