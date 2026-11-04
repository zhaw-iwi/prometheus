import test from "node:test";
import assert from "node:assert/strict";
import "../../../src/main/resources/public/valerian/state-graph.js";
import {
  small,
  coordination,
  nested,
} from "../../playwright/fixtures/state-graphs.mjs";

const { project, layout, route } = globalThis.PrometheusStateGraph;

test("small routing hubs leave space between the centre and surrounding states", () => {
  const nodes = Array.from({length: 5}, (_, i) => ({id: String(i), kind: "state"}));
  const groups = nodes.slice(1).map(n => ({source: "0", target: n.id}));
  const positions = [...layout(nodes, groups).positions.values()];
  for (let i = 0; i < positions.length; i++) for (let j = i + 1; j < positions.length; j++) {
    const a = positions[i], b = positions[j];
    assert.ok(a.x + a.width <= b.x || b.x + b.width <= a.x || a.y + a.height <= b.y || b.y + b.height <= a.y);
  }
});

test("parallel routes retain every transition and its priority; routing hub is central", () => {
  const view = project(coordination);
  assert.equal(view.nodes.length, 10);
  assert.equal(view.groups.length, 18);
  assert.equal(view.groups.flatMap((g) => g.edges).length, 47);
  assert.deepEqual(
    view.groups
      .find((g) => g.source === "execution")
      .edges.map((e) => e.priority),
    [1, 2, 3, 4, 5],
  );
  const geometry = layout(view.nodes, view.groups);
  const hub = geometry.positions.get("routing");
  for (const p of geometry.positions.values()) {
    assert.ok(p.x >= 0 && p.x + p.width < geometry.width);
    assert.ok(p.y >= 0 && p.y + p.height < geometry.height);
  }
  assert.ok(hub.x > geometry.positions.get("paused").x);
  assert.deepEqual(layout(view.nodes, view.groups), geometry);
});

test("collapse retains shared states, projects external routes and restores exact topology", () => {
  const view = project(nested, new Set(["outer"]));
  assert.deepEqual(
    view.nodes.map((n) => n.id),
    ["outer", "other", "shared"],
  );
  assert.equal(view.representative("leaf"), "outer");
  assert.equal(view.representative("shared"), "shared");
  assert.ok(
    !view.groups.some((g) => g.source === "outer" && g.target === "outer"),
  );
  assert.equal(
    project(nested).groups.flatMap((g) => g.edges).length,
    nested.edges.length,
  );
  const cyclic = {
    ...nested,
    regions: [
      ...nested.regions,
      { ownerId: "leaf", initialStateId: "outer", stateIds: ["outer"] },
    ],
  };
  assert.ok(
    project(cyclic, new Set(["outer", "inner", "leaf"])).nodes.some(
      (n) => n.id === "outer",
    ),
  );
});

test("duplicate names and self loops retain stable identities and finite paths", () => {
  assert.equal(
    project(nested).nodes.filter((n) => n.name === "Duplicate label").length,
    2,
  );
  const view = project(small),
    geometry = layout(view.nodes, view.groups);
  for (const g of view.groups) {
    const path = route(
      geometry.positions.get(g.source),
      geometry.positions.get(g.target),
      0,
    );
    assert.ok(Number.isFinite(path.x) && Number.isFinite(path.y));
    assert.ok(!/NaN|Infinity/.test(path.d));
  }
  assert.equal(
    view.groups.find((g) => g.source === g.target).edges[0].id,
    "conversation:conversation:2",
  );
});
