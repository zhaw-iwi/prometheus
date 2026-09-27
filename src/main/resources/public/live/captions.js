/** Provider receipt IDs, rather than text equality, reconcile previews with durable segments. */
export class LiveCaptions {
  constructor() { this.pending = new Map(); this.seen = new Set(); this.sequence = 0; }
  receive(event) {
    const speaker = event?.type === "session.input_transcript.delta" ? "USER"
      : event?.type === "session.output_transcript.delta" ? "ASSISTANT" : null;
    if (!speaker || typeof event.event_id !== "string" || typeof event.delta !== "string") return false;
    if (this.seen.has(event.event_id)) return false;
    if (this.seen.size >= 8192 || this.pending.size >= 256 || event.delta.length > 3000) throw new Error("Caption capacity reached. Reconnect to continue.");
    this.seen.add(event.event_id);
    this.pending.set(event.event_id, { speaker, text: event.delta, start: Number.isFinite(event.start_ms) ? event.start_ms : Infinity, sequence: this.sequence++ });
    return true;
  }
  reconcile(segments) {
    for (const segment of segments || []) if (segment.status !== "PENDING")
      for (const id of segment.receiptIds || []) this.pending.delete(id);
  }
  snapshot() {
    const values = [...this.pending.values()].sort((a, b) => a.start - b.start || a.sequence - b.sequence);
    return Object.fromEntries(["USER", "ASSISTANT"].map(speaker => [speaker, values.filter(value => value.speaker === speaker).map(value => value.text).join("")]));
  }
}
