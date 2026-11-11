/** Connection-scoped facts only. Never retains or replays a request, and owns no timer. */
export class ServiceRecovery {
  constructor(changed = () => {}) { this.changed = changed; this.generation = 0; this.key = null; this.issue = null; }
  scope(key) { this.key = key || null; ++this.generation; this.issue = null; this.changed(null); }
  capture() { return this.key ? {key: this.key, generation: this.generation} : null; }
  accepts(scope) { return !!scope && scope.key === this.key && scope.generation === this.generation; }
  failure(scope, status = 0, mutation = false) {
    if (!this.accepts(scope) || !(status === 0 || status === 429 || status >= 500)) return;
    this.issue = {kind: "unavailable", uncertainMutation: mutation || this.issue?.uncertainMutation === true};
    this.changed({...this.issue});
  }
  reachable(scope) {
    if (!this.accepts(scope) || !this.issue) return;
    this.issue = this.issue.uncertainMutation ? {kind: "review", uncertainMutation: true} : null;
    this.changed(this.issue && {...this.issue});
  }
  dismiss(scope) {
    if (!this.accepts(scope) || this.issue?.kind !== "review") return;
    this.issue = null; this.changed(null);
  }
}

if (typeof document !== "undefined") {
  const panel = document.getElementById("service_recovery");
  const title = document.getElementById("service_recovery_title");
  const detail = document.getElementById("service_recovery_detail");
  const dismiss = document.getElementById("service_recovery_dismiss");
  globalThis.PrometheusServiceRecovery = new ServiceRecovery(issue => {
    if (!panel) return;
    panel.hidden = !issue;
    panel.dataset.state = issue?.kind || "clear";
    if (!issue) { document.getElementById("service_recovery_check").disabled = false; return; }
    title.textContent = issue.kind === "review" ? "Connection reachable — review before continuing" : "Service connection unavailable";
    detail.textContent = issue.kind === "review"
      ? "The service is reachable, but the previous request may have committed or may still be processing. Check connection refreshes task status and history; review them before repeating a request. No failed task action was automatically repeated."
      : "The service could not confirm the request. History and task status shown are last known. A failed reply does not prove that an action failed. No failed task action was automatically repeated. You can stop capture locally; that does not confirm the agent's task has stopped.";
    dismiss.hidden = issue.kind !== "review";
  });
  document.getElementById("service_recovery_check")?.addEventListener("click", () => window.dispatchEvent(new Event("prometheus-check-connection")));
  dismiss?.addEventListener("click", () => globalThis.PrometheusServiceRecovery.dismiss(globalThis.PrometheusServiceRecovery.capture()));
}
