import { MicrophoneLease, TranscriptionMedia } from "../transcription/media.js";
import { OutputLease } from "../speech/playback.js";

const $ = (id) => document.getElementById(id);
let current = null;
const show = (text) => { $("status").textContent = text; };
async function api(run, suffix = "", options = {}) {
  const response = await fetch(`/demo/agents/${encodeURIComponent(run.agent)}/live/${suffix}`, {
    ...options, headers: { "Content-Type": "application/json", "X-Prometheus-Access-Code": run.code },
    signal: AbortSignal.timeout(20000), cache: "no-store",
  });
  if (!response.ok) throw new Error(`Live request failed (${response.status}).`);
  return response.json();
}
function check(run) { if (current !== run) throw new DOMException("Stopped", "AbortError"); }
async function stop(message = "Stopped") {
  const run = current;
  current = null;
  if (!run) return;
  // Local silence and track release precede any server round trip.
  $("output").pause(); $("output").srcObject = null;
  run.media?.release(); run.output?.release(); clearInterval(run.poll);
  $("start").disabled = false; $("stop").disabled = true; $("mute").disabled = true;
  show(message);
  if (run.handle) {
    try { const result = await api(run, `sessions/${run.handle}`, { method: "DELETE" });
      if (!current) show(`${message}. Finalization ${result.finalized ? "confirmed" : "unconfirmed"}.`);
    } catch (_) { if (!current) show(`${message}. Server cleanup unconfirmed.`); }
  }
  run.channel?.close(); run.peer?.close();
}
$("start").onclick = async () => {
  if (current) return;
  const run = { agent: $("agent").value.trim(), code: $("code").value.trim(), muted: false };
  current = run; $("start").disabled = true; $("stop").disabled = false;
  $("captions").textContent = ""; show("Connecting…");
  try {
    const capabilities = await api(run, "capabilities"); check(run);
    if (!capabilities.enabled) throw new Error("GPT-Live is disabled on this server.");
    run.output = new OutputLease({ agentId: run.agent, onConflict: () => void stop("Output lease lost") });
    if (!run.output.acquire()) throw new Error("Another tab owns this agent's output.");
    run.media = new TranscriptionMedia({ lease: new MicrophoneLease({ onConflict: () => void stop("Microphone lease lost") }) });
    await run.media.acquire({ echoCancellation: true, noiseSuppression: true, autoGainControl: true });
    if (current !== run) { run.media.release(); return; }
    run.media.setEnabled(false);
    run.peer = new RTCPeerConnection(); run.media.addTracks(run.peer);
    run.media.stream.getAudioTracks().forEach((track) => track.addEventListener("ended", () => { if (current === run) void stop("Microphone disconnected"); }));
    run.peer.ontrack = (event) => { if (current === run) $("output").srcObject = event.streams[0] || new MediaStream([event.track]); };
    run.peer.onconnectionstatechange = () => { if (current === run && ["failed", "disconnected", "closed"].includes(run.peer.connectionState)) void stop("Transport disconnected"); };
    run.channel = run.peer.createDataChannel("oai-events");
    let ready;
    const started = new Promise((resolve) => { ready = resolve; });
    run.channel.onclose = () => { if (current === run) void stop("Data channel closed"); };
    run.channel.onmessage = ({ data }) => {
      if (current !== run) return;
      let event; try { event = JSON.parse(data); } catch (_) { return; }
      if (event.type === "session.started") ready();
      if (["session.input_transcript.delta", "session.output_transcript.delta"].includes(event.type)) {
        const speaker = event.type.includes("input_") ? "User" : "Assistant";
        $("captions").textContent = ($("captions").textContent + `\n${speaker}: ${event.delta || ""}`).slice(-8000);
      }
      if (event.type === "session.closed") void stop("Session closed");
    };
    const offer = await run.peer.createOffer(); check(run); await run.peer.setLocalDescription(offer); check(run);
    const session = await api(run, "sessions", { method: "POST", body: JSON.stringify({ sdp: offer.sdp, voice: $("voice").value }) });
    run.handle = session.handle;
    if (current !== run) { await api(run, `sessions/${run.handle}`, { method: "DELETE" }); return; }
    await run.peer.setRemoteDescription({ type: "answer", sdp: session.sdp });
    let timeout;
    try { await Promise.race([started, new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error("Session did not start.")), 15000); })]); }
    finally { clearTimeout(timeout); }
    check(run);
    await api(run, `sessions/${run.handle}/input?muted=false`, { method: "POST" }); check(run);
    // Media playback may await the first assistant packet; do not block user input on it.
    $("output").play().catch(() => { if (current === run) void stop("Speaker playback blocked"); });
    check(run); run.media.setEnabled(true);
    $("mute").disabled = false; $("mute").textContent = "Mute microphone"; show("Live diagnostic session");
    run.poll = setInterval(async () => {
      if (run.polling) return; run.polling = true;
      try { const status = await api(run, `sessions/${run.handle}`); check(run);
        $("diagnostics").textContent = JSON.stringify(status, null, 2);
        if (status.state !== "attached") void stop("Backend disconnected");
      } catch (_) { if (current === run) void stop("Backend unavailable"); }
      finally { run.polling = false; }
    }, 1000);
  } catch (error) { if (current === run) await stop(error.message); }
};
$("mute").onclick = async () => {
  const run = current; if (!run?.handle) return;
  $("mute").disabled = true; run.media.setEnabled(false);
  try { run.muted = !run.muted;
    await api(run, `sessions/${run.handle}/input?muted=${run.muted}`, { method: "POST" }); check(run);
    run.media.setEnabled(!run.muted); $("mute").textContent = run.muted ? "Unmute microphone" : "Mute microphone";
    $("mute").disabled = false;
  } catch (_) { if (current === run) void stop("Input control failed"); }
};
$("stop").onclick = () => void stop();
globalThis.addEventListener("pagehide", () => void stop());
