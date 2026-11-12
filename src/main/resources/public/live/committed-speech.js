import { ApprovedSpeechPlayer } from './approved-speech.js';

/** Backend-authored Speech audio. Native Live output is never connected to audible playback. */
export class CommittedSpeechPlayer extends ApprovedSpeechPlayer {
  stream(stream) {
    if (this.closed || this.remoteSink) throw Error('Speech player cannot replace its remote stream');
    this.remoteSink = document.createElement('audio');
    this.remoteSink.muted = true; this.remoteSink.volume = 0; this.remoteSink.srcObject = stream;
    void this.remoteSink.play().catch(() => {});
  }
  receipt() {} // Native captions are unrelated to the committed synthesized clip.
  inputTranscript() {} // The existing microphone worklet interrupts immediately; delayed captions are not new voice.
  interrupt() {
    this.observedCompletion = null; this.sourceEnded = false;
    super.interrupt();
  }
  update(segments, cue, requestId) {
    if (requestId != null) this.requestId = requestId;
    super.update([], cue, requestId);
  }
  async check() {
    const id = this.requestId;
    if (!id || this.pending || this.current || this.closed) return;
    const old = this.attempts.get(id);
    if (old?.done || old?.after > performance.now()) return;
    this.pending = true;
    const generation = this.buffer.generation;
    try {
      const reply = await this.api(`speech-synthesis/${id}`, {method: 'POST'});
      if (this.closed || generation !== this.buffer.generation) { this.attempts.set(id, {done: true}); return; }
      if (reply.status === 'PENDING') { this.attempts.set(id, {after: performance.now() + 3000}); return; }
      this.attempts.set(id, {done: true});
      if (reply.status !== 'APPROVED') {
        this.withheld('Speech delivery could not be confirmed. Ask for help or use Pause or Stop.'); return;
      }
      if (reply.id !== id || typeof reply.segmentId !== 'string' || reply.sampleRate !== 24000
          || typeof reply.text !== 'string' || !reply.text || reply.text.length > 3000
          || JSON.stringify(reply.receiptIds) !== JSON.stringify([`synthesis-${reply.segmentId}`])
          || typeof reply.pcm !== 'string' || reply.pcm.length > 2880000) throw Error('Invalid synthesized speech');
      const raw = atob(reply.pcm);
      if (!raw.length || raw.length % 2 || raw.length > 2160000) throw Error('Invalid PCM size');
      const duration = raw.length / 48000;
      if (!(Date.parse(reply.expiresAt) > Date.now() + duration * 1000 + 500)) throw Error('Speech cannot finish before expiry');
      const pcm = new Float32Array(raw.length / 2);
      for (let i = 0; i < pcm.length; i++) {
        const value = raw.charCodeAt(i * 2) | raw.charCodeAt(i * 2 + 1) << 8;
        pcm[i] = (value >= 32768 ? value - 65536 : value) / 32768;
      }
      // TTS may include leading silence. Bind the observer to the known clip's
      // first voiced PCM window, without trimming audio or extending its expiry.
      let onset = null;
      for (let i = 0; i < pcm.length; i += 240) {
        const end = Math.min(pcm.length, i + 240); let energy = 0;
        for (let j = i; j < end; j++) energy += pcm[j] * pcm[j];
        if (Math.sqrt(energy / (end - i)) > 500 / 32768) {onset = i / 24; break;}
      }
      if (onset === null) throw Error('No voiced PCM in the synthesized response');
      const audio = this.context.createBuffer(1, pcm.length, reply.sampleRate); audio.copyToChannel(pcm, 0);
      const release = await this.api(`speech-approvals/${id}/release`, {method: 'POST', body: JSON.stringify({segmentId: reply.segmentId})});
      if (this.closed || generation !== this.buffer.generation || !release || document.hidden || this.context.state !== 'running'
          || this.audio.muted || this.audio.paused || this.audio.volume <= 0
          || !(Date.parse(reply.expiresAt) > Date.now() + duration * 1000 + 500)) return;
      const source = this.context.createBufferSource(); source.buffer = audio; source.connect(this.destination);
      const {pcm: ignored, ...identity} = reply;
      this.current = identity; this.playing = source; this.sourceEnded = false; this.observedCompletion = null;
      const segment = {...identity, speaker: 'ASSISTANT', status: 'COMPLETE'};
      this.observer.scheduled({id, segmentId: reply.segmentId, expiresAt: reply.expiresAt}, segment, Math.min(45000, onset + 500));
      source.onended = () => {
        if (this.current !== identity || generation !== this.buffer.generation || this.closed) return;
        this.sourceEnded = true;
        if (this.observedCompletion) void this.finish(this.observedCompletion);
      };
      this.caption(reply.text); source.start();
      this.status('Playing the response. You can interrupt at any time.');
      this.diagnostic({phase: 'speech_approval', outcome: 'released', origin: 'SYNTHESIZED', segmentId: reply.segmentId});
    } catch (_) {
      this.attempts.set(id, {done: true});
      if (!this.closed) this.withheld('Speech delivery could not be confirmed. Ask for help before continuing.');
    } finally { this.pending = false; }
  }
  async finish(cue) {
    if (this.closed || this.current?.id !== cue.id) return;
    // An internal pause cannot finish a long clip. Require the actual source end
    // as well as the existing audible-output observation, with interruption fences.
    this.observedCompletion = cue;
    if (!this.sourceEnded) return;
    return super.finish(cue);
  }
}
