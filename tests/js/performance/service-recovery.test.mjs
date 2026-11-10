import test from "node:test";
import assert from "node:assert/strict";
import { ServiceRecovery } from "../../../src/main/resources/public/valerian/service-recovery.js";

test("server errors, throttling and transport loss preserve uncertain mutation status until explicit review", () => {
  const values = [], recovery = new ServiceRecovery(value => values.push(value));
  recovery.scope("agent"); const scope = recovery.capture();
  for (const status of [500, 503, 429, 0]) {
    recovery.failure(scope, status, true);
    assert.deepEqual(recovery.issue, {kind: "unavailable", uncertainMutation: true});
    recovery.dismiss(scope); assert.equal(recovery.issue.kind, "unavailable");
    recovery.reachable(scope); assert.deepEqual(recovery.issue, {kind: "review", uncertainMutation: true});
    recovery.dismiss(scope); assert.equal(recovery.issue, null);
  }
  assert.ok(values.length > 4);
});

test("successful reads do not imply an uncertain mutation failed or permit replay", () => {
  const recovery = new ServiceRecovery(); recovery.scope("agent"); const scope = recovery.capture();
  recovery.failure(scope, 503, true); recovery.failure(scope, 0, false); recovery.reachable(scope);
  assert.equal(recovery.issue.uncertainMutation, true);
  assert.deepEqual(Object.keys(recovery.issue).sort(), ["kind", "uncertainMutation"]);
  assert.equal(recovery.retry, undefined); // No request body, timer or automatic resend API.
});

test("switch, disconnect and same-agent reconnect fence delayed failures and review results", () => {
  const recovery = new ServiceRecovery(); recovery.scope("agent"); const old = recovery.capture();
  recovery.scope(null); recovery.failure(old, 503, true); assert.equal(recovery.issue, null);
  recovery.scope("agent"); recovery.failure(old, 503, true); assert.equal(recovery.issue, null);
  const current = recovery.capture(); recovery.failure(current, 500, false);
  recovery.reachable(old); assert.equal(recovery.issue.kind, "unavailable");
  recovery.reachable(current); assert.equal(recovery.issue, null);
  recovery.scope("another"); recovery.failure(current, 500, true); assert.equal(recovery.issue, null);
});

test("ordinary application rejections are not mislabeled as service unavailability", () => {
  const recovery = new ServiceRecovery(); recovery.scope("agent"); const scope = recovery.capture();
  for (const status of [200, 400, 401, 403, 404, 409]) recovery.failure(scope, status, true);
  assert.equal(recovery.issue, null);
});
