import { SpeechBuffer } from './speech-buffer.js';
import { LivePlaybackObserver } from './playback-cue.js';

/** Owns the audible path. The remote track is connected only to a silent capture node. */
export class ApprovedSpeechPlayer {
  constructor({audio, api, diagnostic = () => {}, withheld = () => {}, played = () => {}, caption = () => {}, status = () => {}}) {
    Object.assign(this, {audio, api, diagnostic, withheld, played, caption, status});
    this.closed = false; this.segments = new Map(); this.completed = new Map(); this.attempts = new Map(); this.pending = false;
    this.observer = new LivePlaybackObserver(audio, cue => void this.finish(cue), diagnostic);
  }
  async prepare(input) {
    this.context = new AudioContext(); this.buffer = new SpeechBuffer(this.context.sampleRate);
    await this.context.audioWorklet.addModule('/live/speech-capture-worklet.js');
    if (this.closed) return;
    this.destination = this.context.createMediaStreamDestination(); this.audio.srcObject = this.destination.stream;
    this.observer.stream(this.destination.stream);
    this.input = this.context.createMediaStreamSource(input);
    this.inputCapture = new AudioWorkletNode(this.context, 'live-speech-capture', {processorOptions: {inputOnly: true}});
    this.inputCapture.port.onmessage = ({data}) => { if (data.voice && !this.closed && !this.inputMuted) this.interrupt(); };
    this.input.connect(this.inputCapture); this.inputCapture.connect(this.context.destination);
    await this.context.resume(); await this.audio.play();
    if (this.closed) return;
    this.visibility = () => { if (document.hidden) this.interrupt(); };
    this.volume = () => { if (this.audio.muted || this.audio.volume <= 0 || this.audio.paused) this.interrupt(); };
    document.addEventListener('visibilitychange', this.visibility);
    this.audio.addEventListener('volumechange', this.volume); this.audio.addEventListener('pause', this.volume);
  }
  stream(stream) {
    if (this.closed || !this.context) throw Error('Approval player is not ready');
    if (this.source) throw Error('Remote audio stream changed');
    this.source = this.context.createMediaStreamSource(stream);
    this.capture = new AudioWorkletNode(this.context, 'live-speech-capture');
    this.capture.port.onmessage = ({data}) => {
      if (this.closed) return;
      try { this.buffer.frame(data.samples, data.end); }
      catch (_) { this.interrupt(); this.withheld('Audio capture could not be confirmed. Ask for help or reconnect.'); }
    };
    this.source.connect(this.capture); this.capture.connect(this.context.destination);
    // Chromium issue 40094084: remote WebRTC may supply zeros to Web Audio until
    // a media element consumes it. This private sink is always muted AND zero-volume.
    this.remoteSink = document.createElement('audio'); this.remoteSink.muted = true; this.remoteSink.volume = 0;
    this.remoteSink.srcObject = stream;
    void this.remoteSink.play().catch(() => {
      if (!this.closed) { this.interrupt(); this.withheld('Audio capture was blocked. Reconnect when ready.'); }
    });
  }
  receipt(event) { if (!this.closed) this.buffer.caption(event, this.context.currentTime * 1000); }
  inputTranscript() { this.interrupt(); }
  interrupt() {
    if (this.closed) return;
    this.buffer.interrupt(); this.observer.interrupt(); this.current = null;
    if (this.playing) { try { this.playing.stop(); } catch (_) {} this.playing.disconnect(); this.playing = null; }
    this.caption(''); this.diagnostic({phase: 'speech_approval', outcome: 'interrupted'});
  }
  mute(value) { this.inputMuted = value; if (value) this.interrupt(); }
  update(segments, cue, requestId) {
    if (this.closed) return;
    if (this.current && this.finishing !== this.current.id && (Date.parse(this.current.expiresAt) <= Date.now()
        || requestId != null && requestId !== this.current.id)) {
      this.interrupt(); this.withheld('Speech was superseded or expired. Check the current task state before continuing.');
    }
    this.finalCue = cue;
    for (const segment of segments || []) this.segments.set(segment.segmentId, segment);
    while (this.segments.size > 100) this.segments.delete(this.segments.keys().next().value);
    while (this.attempts.size > 128) this.attempts.delete(this.attempts.keys().next().value);
    this.ackFinal(); void this.check();
  }
  async check() {
    if (this.pending || this.current || this.closed) return;
    for (const segment of this.segments.values()) {
      const old = this.attempts.get(segment.segmentId);
      if (old?.done || old?.after > performance.now()) continue;
      const candidate = this.buffer.select(segment);
      if (!candidate) {
        if (segment.speaker === 'ASSISTANT' && segment.status === 'COMPLETE') {
          if (!old) this.attempts.set(segment.segmentId, {seen: performance.now()});
          else if (old.seen != null && performance.now() - old.seen > 10000) {
            this.attempts.set(segment.segmentId, {done: true});
            this.withheld('Speech could not be matched to complete audio. Ask for help or reconnect.');
            this.diagnostic({phase: 'speech_approval', outcome: 'missing_audio_match', segmentId: segment.segmentId,
              capture: this.buffer.diagnostic()});
          }
        }
        continue;
      }
      const generation = this.buffer.generation;
      this.pending = true;
      try {
        const approval = await this.api('speech-approvals', {method: 'POST', body: JSON.stringify({segmentId: segment.segmentId})});
        if (this.closed || generation !== this.buffer.generation) return;
        if (['PENDING', 'CHECKING'].includes(approval.status)) {
          this.attempts.set(segment.segmentId, {after: performance.now() + 3000}); return;
        }
        this.attempts.set(segment.segmentId, {done: true});
        if (approval.status === 'CORRECTING') {
          this.buffer.take(candidate);
          this.status('The unheard draft was rejected. Checking one correction of the same response.');
          this.diagnostic({phase: 'speech_approval', outcome: 'correcting', segmentId: segment.segmentId}); return;
        }
        if (approval.status !== 'APPROVED') {
          this.buffer.take(candidate);
          this.withheld('Speech could not be confirmed. Ask for help or use Pause or Stop.');
          this.diagnostic({phase: 'speech_approval', outcome: approval.status.toLowerCase(), segmentId: segment.segmentId}); return;
        }
        if (approval.segmentId !== segment.segmentId || approval.text !== segment.text
            || JSON.stringify(approval.receiptIds) !== JSON.stringify(segment.receiptIds) || !(Date.parse(approval.expiresAt) > Date.now()))
          throw Error('Approval identity mismatch');
        const release = await this.api(`speech-approvals/${approval.id}/release`, {method: 'POST', body: JSON.stringify({segmentId: segment.segmentId})});
        if (this.closed || generation !== this.buffer.generation || !release || document.hidden || this.context.state !== 'running'
            || this.audio.muted || this.audio.paused || this.audio.volume <= 0) return;
        const pcm = this.buffer.take(candidate); if (!pcm) return;
        const audio = this.context.createBuffer(1, pcm.length, this.context.sampleRate); audio.copyToChannel(pcm, 0);
        const source = this.context.createBufferSource(); source.buffer = audio; source.connect(this.destination);
        this.current = approval; this.playing = source;
        this.observer.scheduled({id: approval.id, segmentId: segment.segmentId, expiresAt: approval.expiresAt}, segment);
        this.caption(approval.text); source.start();
        this.status('Playing the checked response. You can interrupt at any time.');
        this.diagnostic({phase: 'speech_approval', outcome: 'released', segmentId: segment.segmentId,
          bufferDelayMs: this.context.currentTime * 1000 - candidate.voiceEnd});
        return;
      } catch (_) { if (!this.closed) { this.attempts.set(segment.segmentId, {done: true}); this.withheld('Speech delivery could not be confirmed. Ask for help or reconnect.'); } }
      finally { this.pending = false; }
    }
  }
  async finish(cue) {
    if (this.closed || this.current?.id !== cue.id) return;
    const current = this.current, generation = this.buffer.generation;
    this.finishing = cue.id;
    try {
      const accepted = await this.api(`speech-approvals/${cue.id}/played`, {method: 'POST', body: JSON.stringify({segmentId: cue.segmentId})});
      if (this.closed || generation !== this.buffer.generation) return;
      if (accepted) {
        this.completed.set(cue.id, cue.segmentId);
        while (this.completed.size > 128) this.completed.delete(this.completed.keys().next().value);
        this.played(current); this.ackFinal(); this.status('Speak naturally. Replies are checked before playback.');
      }
      else this.withheld('The speech completion could not be confirmed. Check the current task state before continuing.');
    } catch (_) { if (!this.closed) this.withheld('The speech completion could not be confirmed. Ask for help before continuing.'); }
    finally { this.finishing = null; if (this.current === current) { this.current = null; this.caption(''); this.playing?.disconnect(); this.playing = null; } }
  }
  ackFinal() {
    const cue = this.finalCue;
    if (!cue?.segmentId || this.completed.get(cue.id) !== cue.segmentId) return;
    this.completed.delete(cue.id); // One HTTP attempt; uncertain acknowledgements never replay audio.
    void this.api(`playback-cues/${cue.id}`, {method: 'POST', body: JSON.stringify({segmentId: cue.segmentId})})
      .then(ok => this.diagnostic({phase: 'playback_cue', outcome: ok ? 'accepted' : 'obsolete'}))
      .catch(() => this.withheld('Final delivery acknowledgement was lost. Check the task state; do not repeat the action.'));
  }
  close() {
    if (this.closed) return;
    this.interrupt(); this.closed = true; this.observer.close();
    document.removeEventListener?.('visibilitychange', this.visibility);
    this.audio.removeEventListener?.('volumechange', this.volume); this.audio.removeEventListener?.('pause', this.volume);
    this.source?.disconnect(); this.capture?.disconnect(); this.input?.disconnect(); this.inputCapture?.disconnect();
    if (this.remoteSink) { this.remoteSink.pause(); this.remoteSink.srcObject = null; this.remoteSink = null; }
    this.segments.clear(); this.attempts.clear(); this.completed.clear();
    if (this.context) void this.context.close().catch(() => {});
  }
}
