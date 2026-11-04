/* Read-only SVG projection. Layout depends on topology, never on current state. */
(() => {
  const SVG = "http://www.w3.org/2000/svg";
  function project(graph, collapsed = new Set()) {
    const parents = new Map(),
      regions = new Map((graph.regions || []).map((r) => [r.ownerId, r]));
    for (const region of regions.values())
      for (const id of region.stateIds) {
        if (!parents.has(id)) parents.set(id, new Set());
        parents.get(id).add(region.ownerId);
      }
    const representative = (id) => {
      let result = id,
        cursor = id;
      const seen = new Set([id]);
      while (parents.get(cursor)?.size === 1) {
        const owner = [...parents.get(cursor)][0];
        if (seen.has(owner) || cursor === graph.initialStateId) break;
        seen.add(owner);
        if (collapsed.has(owner)) result = owner;
        cursor = owner;
      }
      return result;
    };
    const nodes = graph.nodes.filter((n) => representative(n.id) === n.id),
      groups = new Map();
    for (const edge of graph.edges) {
      const source = representative(edge.sourceId),
        target = representative(edge.targetId);
      if (
        source === target &&
        (source !== edge.sourceId || target !== edge.targetId)
      )
        continue;
      const key = source + "|" + target;
      if (!groups.has(key))
        groups.set(key, { id: key, source, target, edges: [] });
      groups.get(key).edges.push(edge);
    }
    return { nodes, groups: [...groups.values()], representative, regions };
  }
  function layout(nodes, groups = []) {
    // A routing hub belongs in the centre, keeping its spokes clear of other nodes.
    const neighbours = new Map(nodes.map((n) => [n.id, new Set()]));
    for (const g of groups)
      if (g.source !== g.target) {
        neighbours.get(g.source)?.add(g.target);
        neighbours.get(g.target)?.add(g.source);
      }
    const hub =
      nodes.length > 4
        ? nodes.find((n) => n.kind !== "final" && neighbours.get(n.id).size === nodes.length - 1)
        : null;
    const ring = nodes.filter((n) => n !== hub),
      positions = new Map();
    const rx = Math.max(hub ? 280 : 180, ring.length * 42),
      ry = Math.max(120, ring.length * 31);
    const cx = rx + 165,
      cy = ry + 100;
    ring.forEach((n, i) => {
      const angle = -Math.PI / 2 + (i * 2 * Math.PI) / ring.length;
      positions.set(n.id, {
        x: cx + rx * Math.cos(angle) - 97,
        y: cy + ry * Math.sin(angle) - 38,
        width: 194,
        height: 76,
        loopSide: Math.cos(angle) < -0.01 ? -1 : 1,
      });
    });
    if (hub)
      positions.set(hub.id, { x: cx - 97, y: cy - 38, width: 194, height: 76 });
    return { positions, width: cx * 2 + 30, height: cy * 2 };
  }
  function route(a, b, index) {
    const ax = a.x + a.width / 2,
      ay = a.y + a.height / 2,
      bx = b.x + b.width / 2,
      by = b.y + b.height / 2;
    if (a === b) {
      const side = a.loopSide || 1,
        x = side < 0 ? a.x : a.x + a.width;
      return {
        d: `M ${x} ${ay - 12} C ${x + side * 68} ${ay - 90}, ${x + side * 68} ${ay + 90}, ${x} ${ay + 12}`,
        x: x + side * 42,
        y: ay,
      };
    }
    const dx = bx - ax,
      dy = by - ay,
      length = Math.hypot(dx, dy);
    const bend = 65,
      mx = (ax + bx) / 2 - (dy / length) * bend,
      my = (ay + by) / 2 + (dx / length) * bend;
    const boundary = (p, x, y) => {
      const px = p.x + p.width / 2,
        py = p.y + p.height / 2,
        vx = x - px,
        vy = y - py;
      const scale =
        1 /
        Math.max(
          Math.abs(vx) / (p.width / 2 + 3),
          Math.abs(vy) / (p.height / 2 + 3),
        );
      return { x: px + vx * scale, y: py + vy * scale };
    };
    const start = boundary(a, mx, my),
      end = boundary(b, mx, my);
    return {
      d: `M ${start.x} ${start.y} Q ${mx} ${my} ${end.x} ${end.y}`,
      x: (start.x + 2 * mx + end.x) / 4,
      y: (start.y + 2 * my + end.y) / 4,
    };
  }
  function create(root) {
    let graph = null,
      view = null,
      geometry = null,
      active = [],
      collapsed = new Set(),
      selected = null,
      camera = null,
      drag = null,
      mode = "graph";
    const html = (tag, cls, text) => {
      const e = root.ownerDocument.createElement(tag);
      if (cls) e.className = cls;
      if (text !== undefined) e.textContent = text;
      return e;
    };
    const svg = (tag, attrs = {}, text) => {
      const e = root.ownerDocument.createElementNS(SVG, tag);
      for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
      if (text !== undefined) e.textContent = text;
      return e;
    };
    const toolbar = html("div", "graph-toolbar"),
      status = html(
        "div",
        "graph-summary",
        "Graph loads when an agent is connected.",
      );
    status.setAttribute("role", "status");
    const canvas = svg("svg", {
      class: "machine-graph",
      role: "group",
      "aria-label": "State machine graph",
      tabindex: 0,
    });
    const list = html("div", "graph-state-list");
    const details = html("div", "graph-details");
    details.setAttribute("aria-label", "State and transition details");
    const search = html("input", "graph-search");
    search.type = "search";
    search.placeholder = "Find a state";
    search.setAttribute("aria-label", "Find a state");
    const legend = html(
      "p",
      "graph-legend",
      "Blue: current state · Double border: final · Dashed arrow: inner entry · ×N: grouped transitions. Arrows describe defined routes, not current eligibility.",
    );
    function button(label, fn, parent = toolbar) {
      const b = html("button", "graph-button", label);
      b.type = "button";
      b.addEventListener("click", fn);
      parent.append(b);
      return b;
    }
    const graphButton = button("Graph", () => switchMode("graph")),
      listButton = button("List", () => switchMode("list"));
    button("Zoom in", () => zoom(0.8));
    button("Zoom out", () => zoom(1.25));
    button("Fit graph", fit);
    button("Current", centreCurrent);
    toolbar.append(search);
    root.append(toolbar, status, canvas, list, details, legend);
    root.hidden = true;
    function setCamera() {
      if (camera)
        canvas.setAttribute(
          "viewBox",
          `${camera.x} ${camera.y} ${camera.w} ${camera.h}`,
        );
    }
    function fit() {
      if (!geometry) return;
      camera = { x: 0, y: 0, w: geometry.width, h: geometry.height };
      setCamera();
    }
    function zoom(factor) {
      if (!camera || !geometry) return;
      const w = Math.max(260, Math.min(geometry.width * 4, camera.w * factor)),
        h = (camera.h * w) / camera.w;
      camera = {
        x: camera.x + (camera.w - w) / 2,
        y: camera.y + (camera.h - h) / 2,
        w,
        h,
      };
      setCamera();
    }
    function centre(id) {
      const p = geometry?.positions.get(view.representative(id));
      if (!p) return;
      camera = { x: p.x - 175, y: p.y - 170, w: 550, h: 410 };
      setCamera();
    }
    function centreCurrent() {
      if (active.length) centre(active.at(-1));
    }
    function switchMode(next) {
      mode = next;
      canvas.hidden = next !== "graph";
      canvas.style.display = next === "graph" ? "block" : "none";
      list.hidden = next !== "list";
      graphButton.setAttribute("aria-pressed", String(next === "graph"));
      listButton.setAttribute("aria-pressed", String(next === "list"));
    }
    function showLines(title, lines) {
      const box = html("div");
      box.append(html("strong", "", title));
      const ul = html("ol");
      for (const line of lines) ul.append(html("li", "", line));
      box.append(ul);
      details.append(box);
    }
    function inspectNode(id) {
      selected = { kind: "node", id };
      details.replaceChildren();
      const n = graph.nodes.find((n) => n.id === id);
      if (!n) return;
      details.append(html("h6", "", n.name));
      details.append(
        html(
          "p",
          "",
          `${n.kind === "final" ? "Final state" : n.kind === "compound" ? "Contains an inner state machine" : "State"}${id === graph.initialStateId ? " · Initial state" : ""}. ${n.startsOnEntry ? "Runs its entry behavior." : "Continues processing the same event on entry."}`,
        ),
      );
      const region = view.regions.get(id);
      if (region) {
        details.append(
          html(
            "p",
            "",
            `Inner states: ${region.stateIds.map(name).join(", ")}. Entry: ${name(region.initialStateId)}.`,
          ),
        );
        button(
          collapsed.has(id) ? "Expand inner states" : "Collapse inner states",
          () => {
            collapsed.has(id) ? collapsed.delete(id) : collapsed.add(id);
            render();
            inspectNode(id);
          },
          details,
        );
      }
      const owners = (graph.regions || []).filter((r) =>
        r.stateIds.includes(id),
      );
      if (owners.length)
        details.append(
          html(
            "p",
            "",
            `${owners.length > 1 ? "Shared by" : "Inside"}: ${owners.map((r) => name(r.ownerId)).join(", ")}.`,
          ),
        );
      const outgoing = graph.edges.filter((e) => e.sourceId === id);
      details.append(
        html(
          "p",
          "",
          `${outgoing.length} outgoing transition${outgoing.length === 1 ? "" : "s"}; evaluated in priority order.`,
        ),
      );
      for (const e of outgoing)
        button(
          `#${e.priority} → ${name(e.targetId)}`,
          () => inspectEdges([e]),
          details,
        );
      mark();
    }
    function name(id) {
      return graph.nodes.find((n) => n.id === id)?.name || id;
    }
    function inspectEdges(edges) {
      selected = { kind: "edges", ids: edges.map((e) => e.id) };
      details.replaceChildren();
      details.append(
        html(
          "h6",
          "",
          edges.length === 1 ? "Transition" : "Grouped transitions",
        ),
      );
      details.append(
        html(
          "p",
          "",
          "First matching outgoing transition wins. All listed conditions must hold. Runtime results are not evaluated by this viewer.",
        ),
      );
      for (const e of edges) {
        details.append(
          html(
            "strong",
            "graph-edge-title",
            `#${e.priority}: ${name(e.sourceId)} → ${name(e.targetId)}`,
          ),
        );
        showLines(
          "Conditions",
          e.conditions.length
            ? e.conditions.map((c) => c.label)
            : ["Unconditional"],
        );
        showLines(
          "Actions",
          e.actions.length
            ? e.actions.map((a) => a.label)
            : ["No transition actions"],
        );
      }
      mark();
    }
    function mark() {
      if (!view) return;
      const current = new Set(active.map(view.representative)),
        query = search.value.toLowerCase().trim();
      for (const el of root.querySelectorAll("[data-node-id]")) {
        const id = el.dataset.nodeId,
          n = graph.nodes.find((n) => n.id === id);
        const isCurrent = el.classList.contains("graph-list-node") ? active.includes(id) : current.has(id);
        el.classList.toggle("is-current", isCurrent);
        el.classList.toggle(
          "is-selected",
          selected?.kind === "node" && selected.id === id,
        );
        el.setAttribute("aria-current", String(isCurrent));
        el.classList.toggle(
          "is-search-match",
          !!query && n.name.toLowerCase().includes(query),
        );
        if (el.classList.contains("graph-list-node")) {
          el.hidden = !!query && !n.name.toLowerCase().includes(query);
          el.textContent = n.name + (active.includes(id) ? " · current" : "");
        }
        const badge = el.querySelector(".graph-current-label");
        if (badge) badge.textContent = current.has(id) ? "CURRENT" : "";
      }
      for (const el of canvas.querySelectorAll("[data-edge-group]")) {
        const g = view.groups.find((g) => g.id === el.dataset.edgeGroup);
        const picked =
          (selected?.kind === "edges" &&
            g.edges.some((e) => selected.ids.includes(e.id))) ||
          (selected?.kind === "node" &&
            (g.source === selected.id || g.target === selected.id));
        el.classList.toggle("is-selected", !!picked);
        el.classList.toggle("is-muted", !!selected && !picked);
      }
      status.textContent = `${graph.nodes.length} states · ${graph.edges.length} transitions${view.nodes.length < graph.nodes.length ? ` · ${graph.nodes.length - view.nodes.length} inner states collapsed` : ""}${query ? ` · ${graph.nodes.filter((n) => n.name.toLowerCase().includes(query)).length} matches` : ""}`;
    }
    function render() {
      if (!graph) return;
      view = project(graph, collapsed);
      geometry = layout(view.nodes, view.groups);
      canvas.replaceChildren();
      list.replaceChildren();
      const defs = svg("defs"),
        marker = svg("marker", {
          id: "state-graph-arrow",
          viewBox: "0 0 10 10",
          refX: 9,
          refY: 5,
          markerWidth: 7,
          markerHeight: 7,
          orient: "auto-start-reverse",
        });
      marker.append(
        svg("path", { d: "M 0 0 L 10 5 L 0 10 z", fill: "context-stroke" }),
      );
      defs.append(marker);
      canvas.append(defs);
      for (const region of graph.regions || []) {
        const a = geometry.positions.get(region.ownerId),
          b = geometry.positions.get(region.initialStateId);
        if (!a || !b || a === b) continue;
        const r = route(a, b, 0),
          g = svg("g", { class: "graph-containment" });
        g.append(
          svg("path", { d: r.d, "marker-end": "url(#state-graph-arrow)" }),
          svg("text", { x: r.x, y: r.y - 8 }, "inner entry"),
        );
        canvas.append(g);
      }
      view.groups.forEach((group, i) => {
        const a = geometry.positions.get(group.source),
          b = geometry.positions.get(group.target);
        if (!a || !b) return;
        const r = route(a, b, i),
          label =
            group.edges.length > 1
              ? `${group.edges.length} transitions`
              : `#${group.edges[0].priority}`;
        const g = svg("g", {
          class: `graph-edge${group.edges.length === 1 ? " is-single" : ""}`,
          "data-edge-group": group.id,
          role: "button",
          tabindex: 0,
          "aria-label": `${name(group.source)} to ${name(group.target)}, ${label}`,
        });
        g.append(
          svg(
            "title",
            {},
            `${name(group.source)} → ${name(group.target)}. ${label}. Select to inspect.`,
          ),
        );
        g.append(
          svg("path", {
            d: r.d,
            "marker-end": "url(#state-graph-arrow)",
            class: "graph-edge-line",
          }),
          svg("path", { d: r.d, class: "graph-edge-hit" }),
        );
        const width = 34;
        g.append(
          svg("rect", {
            x: r.x - width / 2,
            y: r.y - 12,
            width,
            height: 24,
            rx: 10,
            class: "graph-edge-pill",
          }),
          svg(
            "text",
            { x: r.x, y: r.y + 4 },
            group.edges.length > 1 ? `×${group.edges.length}` : label,
          ),
        );
        g.addEventListener("click", () => inspectEdges(group.edges));
        g.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            inspectEdges(group.edges);
          }
        });
        canvas.append(g);
      });
      for (const n of view.nodes) {
        const p = geometry.positions.get(n.id),
          g = svg("g", {
            class: "graph-node",
            "data-node-id": n.id,
            role: "button",
            tabindex: 0,
            "aria-label": `State: ${n.name}`,
            transform: `translate(${p.x},${p.y})`,
          });
        g.append(
          svg("title", {}, n.name),
          svg("rect", { width: p.width, height: p.height, rx: 12 }),
        );
        if (n.kind === "final")
          g.append(
            svg("rect", {
              x: 5,
              y: 5,
              width: p.width - 10,
              height: p.height - 10,
              rx: 8,
              class: "graph-inner-border",
            }),
          );
        const words = n.name.split(/\s+/),
          lines = [""];
        for (const word of words) {
          if ((lines.at(-1) + " " + word).trim().length > 22 && lines.at(-1))
            lines.push("");
          lines[lines.length - 1] = (lines.at(-1) + " " + word).trim();
        }
        lines
          .slice(0, 2)
          .forEach((line, i) =>
            g.append(
              svg(
                "text",
                { x: p.width / 2, y: 30 + i * 19 },
                line.length > 23 ? line.slice(0, 22) + "…" : line,
              ),
            ),
          );
        if (lines.length > 2)
          g.append(svg("text", { x: p.width - 15, y: 49 }, "…"));
        g.append(
          svg(
            "text",
            { x: p.width / 2, y: 67, class: "graph-current-label" },
            "",
          ),
        );
        if (n.id === graph.initialStateId)
          g.append(
            svg(
              "text",
              { x: 12, y: -10, class: "graph-initial-label" },
              "INITIAL",
            ),
          );
        if (view.regions.has(n.id))
          g.append(
            svg(
              "text",
              { x: p.width - 15, y: 17, class: "graph-region-label" },
              collapsed.has(n.id) ? "+" : "−",
            ),
          );
        g.addEventListener("click", () => inspectNode(n.id));
        g.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            inspectNode(n.id);
          }
        });
        canvas.append(g);
      }
      for (const n of graph.nodes) {
        const b = button(
          n.name,
          () => {
            inspectNode(n.id);
            centre(n.id);
          },
          list,
        );
        b.classList.add("graph-list-node");
        b.dataset.nodeId = n.id;
      }
      fit();
      switchMode(mode);
      mark();
    }
    search.addEventListener("input", mark);
    search.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        const n = graph?.nodes.find((n) =>
          n.name.toLowerCase().includes(search.value.toLowerCase()),
        );
        if (n) {
          inspectNode(n.id);
          centre(n.id);
        }
      }
    });
    canvas.addEventListener(
      "wheel",
      (e) => {
        if (!camera) return;
        e.preventDefault();
        zoom(e.deltaY > 0 ? 1.1 : 0.9);
      },
      { passive: false },
    );
    canvas.addEventListener("pointerdown", (e) => {
      if (e.button !== 0 || !camera || e.target.closest('[role="button"]'))
        return;
      drag = { x: e.clientX, y: e.clientY, camera: { ...camera } };
      canvas.setPointerCapture(e.pointerId);
    });
    canvas.addEventListener("pointermove", (e) => {
      if (!drag) return;
      const rect = canvas.getBoundingClientRect(),
        ratio = Math.max(
          drag.camera.w / rect.width,
          drag.camera.h / rect.height,
        );
      camera = {
        ...drag.camera,
        x: drag.camera.x - (e.clientX - drag.x) * ratio,
        y: drag.camera.y - (e.clientY - drag.y) * ratio,
      };
      setCamera();
    });
    canvas.addEventListener("pointerup", () => (drag = null));
    canvas.addEventListener("pointercancel", () => (drag = null));
    canvas.addEventListener("lostpointercapture", () => (drag = null));
    canvas.addEventListener("keydown", (e) => {
      if (e.target !== canvas || !camera) return;
      const step = {
        ArrowLeft: [-40, 0],
        ArrowRight: [40, 0],
        ArrowUp: [0, -40],
        ArrowDown: [0, 40],
      }[e.key];
      if (step) {
        e.preventDefault();
        camera.x += step[0];
        camera.y += step[1];
        setCamera();
      } else if (e.key === "+" || e.key === "=") zoom(0.8);
      else if (e.key === "-") zoom(1.25);
      else if (e.key === "Home") fit();
    });
    return {
      setGraph(value, ids = value.activeStateIds || []) {
        if (
          value.schemaVersion !== 1 ||
          !Array.isArray(value.nodes) ||
          !Array.isArray(value.edges)
        )
          throw Error("Unsupported state graph");
        graph = value;
        active = ids;
        collapsed = new Set();
        selected = null;
        search.value = "";
        details.replaceChildren(
          html(
            "p",
            "",
            "Select a state or arrow to inspect it. Drag the background to pan; use the mouse wheel or zoom buttons to scale.",
          ),
        );
        root.hidden = false;
        render();
      },
      setActive(ids) {
        active = [...ids];
        mark();
      },
      clear() {
        graph = null;
        view = null;
        geometry = null;
        camera = null;
        active = [];
        selected = null;
        collapsed.clear();
        canvas.replaceChildren();
        list.replaceChildren();
        details.replaceChildren();
        root.hidden = true;
      },
    };
  }
  globalThis.PrometheusStateGraph = { project, layout, route, create };
})();
