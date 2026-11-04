import { ObservationTiming } from "./client.js";

const timing = new ObservationTiming({ changed(status) {
  const output = document.getElementById("observation_clock_status");
  if (output) output.textContent = status.status === "ready" ? "Sensor timing ready"
    : status.reason === "disconnected" ? "Connect to synchronize sensor timing"
    : "Sensor timing unavailable — observations are not being sent";
  window.dispatchEvent(new Event("prometheus-observation-timing"));
} });
globalThis.PrometheusObservationTiming = timing;
document.addEventListener("visibilitychange", () => timing.visibilityChanged());
window.addEventListener("pagehide", () => timing.stop());
