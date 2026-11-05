/* Move the live report itself, keeping camera ownership and rendering in the cockpit. */
window.PrometheusHandReport = {
  create() {
    const panel = document.getElementById("hand_report");
    const home = document.getElementById("hand_report_home");
    const placeholder = document.getElementById("hand_report_placeholder");
    const handle = panel.querySelector("[data-hand-report-drag]");
    const expandButton = panel.querySelector('[data-hand-report-action="expand"]');
    let floating = false, position = null, drag = null;

    function positionPanel() {
      if (!floating) return;
      const bounds = panel.getBoundingClientRect();
      position.x = Math.max(8, Math.min(position.x, window.innerWidth - bounds.width - 8));
      position.y = Math.max(8, Math.min(position.y, window.innerHeight - bounds.height - 8));
      panel.style.left = `${position.x}px`;
      panel.style.top = `${position.y}px`;
    }

    function updateMode() {
      panel.dataset.handReportMode = floating ? "floating" : "docked";
      placeholder.hidden = !floating;
      expandButton.setAttribute("aria-expanded", String(floating));
      handle.tabIndex = floating ? 0 : -1;
      handle.title = floating ? "Drag to move; use arrow keys when focused" : "";
      if (floating) {
        panel.setAttribute("role", "dialog");
        panel.setAttribute("aria-modal", "false");
      } else {
        panel.removeAttribute("role");
        panel.removeAttribute("aria-modal");
      }
    }

    function arrangeBesideCamera() {
      const camera = document.getElementById("camera_video");
      // Scroll after moving the report so scroll anchoring cannot hide the camera.
      camera.scrollIntoView({ block: "start", behavior: "instant" });
      window.scrollBy({ top: -16, behavior: "instant" });
      const bounds = panel.getBoundingClientRect();
      position = {
        x: window.innerWidth - bounds.width - 24,
        y: window.innerWidth <= 991 ? window.innerHeight - bounds.height - 8 : camera.getBoundingClientRect().top,
      };
      positionPanel();
    }

    function expand() {
      if (floating || panel.hidden) return;
      floating = true;
      document.body.appendChild(panel);
      updateMode();
      arrangeBesideCamera();
      handle.focus({ preventScroll: true });
    }

    function dock(reveal = false) {
      if (!floating) return;
      floating = false;
      drag = null;
      home.appendChild(panel);
      // Remove user-resized dimensions before returning to the narrow Sensing column.
      panel.style.left = panel.style.top = panel.style.width = panel.style.height = "";
      updateMode();
      if (reveal) {
        const collapse = home.closest(".accordion-collapse");
        const focus = () => expandButton.focus();
        if (collapse && !collapse.classList.contains("show")) {
          collapse.addEventListener("shown.bs.collapse", focus, { once: true });
          window.bootstrap.Collapse.getOrCreateInstance(collapse, { toggle: false }).show();
        } else focus();
      }
    }

    [panel, placeholder].forEach(element => element.addEventListener("click", event => {
      if (event.target.closest('[data-hand-report-action="dock"]')) dock(true);
    }));
    handle.addEventListener("pointerdown", event => {
      if (!floating || event.button !== 0) return;
      drag = { id: event.pointerId, x: event.clientX - position.x, y: event.clientY - position.y };
      handle.setPointerCapture(event.pointerId);
      handle.focus();
      event.preventDefault();
    });
    handle.addEventListener("pointermove", event => {
      if (!drag || drag.id !== event.pointerId) return;
      position = { x: event.clientX - drag.x, y: event.clientY - drag.y };
      positionPanel();
    });
    ["pointerup", "pointercancel", "lostpointercapture"].forEach(type => {
      handle.addEventListener(type, () => { drag = null; });
    });
    handle.addEventListener("keydown", event => {
      const steps = { ArrowLeft: [-20, 0], ArrowRight: [20, 0], ArrowUp: [0, -20], ArrowDown: [0, 20] };
      if (!floating || !steps[event.key]) return;
      event.preventDefault();
      position.x += steps[event.key][0];
      position.y += steps[event.key][1];
      positionPanel();
    });
    panel.addEventListener("keydown", event => {
      if (floating && event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        dock(true);
      }
    });
    window.addEventListener("resize", () => { if (floating) arrangeBesideCamera(); });
    new ResizeObserver(positionPanel).observe(panel);
    updateMode();
    return { expand, dock };
  },
};
