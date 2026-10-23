/* Moves the existing state view; the cockpit remains its only data/session owner. */
window.PrometheusStateView = {
  create() {
    const panel = document.getElementById("state_view");
    const home = document.getElementById("state_view_home");
    const placeholder = document.getElementById("state_view_placeholder");
    const handle = panel.querySelector("[data-state-drag]");
    const status = panel.querySelector("[data-state-status]");
    const maximize = panel.querySelector('[data-state-action="maximize"]');
    let mode = "docked", popup = null, popupTimer = null, drag = null;
    let position = null, maximized = false;

    function positionPanel() {
      if (mode !== "floating" || maximized) return;
      const bounds = panel.getBoundingClientRect();
      position = position || { x: window.innerWidth - bounds.width - 24, y: 100 };
      position.x = Math.max(8, Math.min(position.x, window.innerWidth - bounds.width - 8));
      position.y = Math.max(8, Math.min(position.y, window.innerHeight - bounds.height - 8));
      panel.style.left = `${position.x}px`;
      panel.style.top = `${position.y}px`;
    }

    function updateMode() {
      panel.dataset.stateMode = mode;
      panel.classList.toggle("is-maximized", maximized);
      placeholder.hidden = mode === "docked";
      handle.tabIndex = mode === "floating" ? 0 : -1;
      handle.title = mode === "floating" ? "Drag to move; use arrow keys when focused" : "";
      if (mode === "docked") {
        panel.removeAttribute("role");
        panel.removeAttribute("aria-modal");
      } else {
        panel.setAttribute("role", "dialog");
        panel.setAttribute("aria-modal", "false");
      }
      const label = maximized ? "Restore state view" : "Maximise state view";
      maximize.title = label;
      maximize.setAttribute("aria-label", label);
      maximize.setAttribute("aria-pressed", String(maximized));
      maximize.firstElementChild.className = `bi ${maximized ? "bi-fullscreen-exit" : "bi-fullscreen"}`;
      status.textContent = "";
      positionPanel();
    }

    function closePopup() {
      const previous = popup;
      popup = null;
      clearInterval(popupTimer);
      popupTimer = null;
      if (previous && !previous.closed) previous.close();
    }

    function dock(reveal = false) {
      // Reclaim the nodes before closing their document, preserving listeners and state.
      home.appendChild(panel);
      mode = "docked";
      maximized = false;
      drag = null;
      panel.style.left = panel.style.top = "";
      closePopup();
      updateMode();
      if (reveal) {
        const drawer = document.getElementById("diagnostics_drawer");
        window.bootstrap.Offcanvas.getOrCreateInstance(drawer).show();
        window.bootstrap.Tab.getOrCreateInstance(document.getElementById("diagnostics_drawer_tab")).show();
        panel.querySelector('[data-state-action="float"]').focus();
      }
    }

    function hideDrawer() {
      const drawer = document.getElementById("diagnostics_drawer");
      const focus = () => {
        if (mode === "floating") handle.focus();
        else if (mode === "popup") popup?.focus();
      };
      if (drawer.classList.contains("show")) {
        drawer.addEventListener("hidden.bs.offcanvas", focus, { once: true });
        window.bootstrap.Offcanvas.getOrCreateInstance(drawer).hide();
      } else focus();
    }

    function float() {
      document.body.appendChild(panel);
      mode = "floating";
      closePopup();
      updateMode();
      hideDrawer();
    }

    function syncTheme() {
      if (!popup || popup.closed) return;
      popup.document.documentElement.dataset.theme = document.documentElement.dataset.theme;
      popup.document.documentElement.dataset.bsTheme = document.documentElement.dataset.bsTheme;
    }

    function popOut() {
      if (popup && !popup.closed) { popup.focus(); return; }
      const opened = window.open("/valerian/state-view-window.html", "_blank", "popup,width=440,height=520");
      if (!opened) {
        status.textContent = "Window blocked. Allow popups for this site, then try again.";
        return;
      }
      popup = opened;
      // Native window close is not guaranteed to dispatch pagehide in every browser.
      popupTimer = setInterval(() => { if (opened.closed && popup === opened) dock(); }, 500);
      popup.focus();
      hideDrawer();
    }

    function mountPopup(opened) {
      const doc = popup.document;
      doc.documentElement.lang = document.documentElement.lang || "en";
      doc.body.hidden = true;
      // Copy styles only: no cockpit scripts, credentials, streams or media in the popup.
      const styles = [...document.querySelectorAll('style, link[rel="stylesheet"]')].map(source => {
        const copy = doc.createElement(source.tagName.toLowerCase());
        let ready = Promise.resolve(true);
        if (source.tagName === "LINK") {
          for (const attribute of source.attributes) copy.setAttribute(attribute.name, attribute.value);
          copy.href = source.href;
          ready = new Promise(resolve => {
            copy.addEventListener("load", () => resolve(true), { once: true });
            copy.addEventListener("error", () => resolve(false), { once: true });
          });
        } else copy.textContent = source.textContent;
        doc.head.appendChild(copy);
        return ready;
      });
      doc.body.className = "state-view-window";
      doc.body.replaceChildren(panel);
      mode = "popup";
      maximized = false;
      updateMode();
      syncTheme();
      Promise.all(styles).then(loaded => {
        if (popup !== opened || opened.closed) return;
        doc.body.hidden = false;
        if (loaded.includes(false)) status.textContent = "Some styles could not load. Dock back and retry if needed.";
      });
      popup.addEventListener("pagehide", () => { if (popup === opened) dock(); }, { once: true });
      popup.focus();
    }

    window.addEventListener("message", event => {
      if (popup && !popup.closed && event.origin === window.location.origin && event.source === popup &&
          event.data === "prometheus-state-view-ready" && mode !== "popup") mountPopup(popup);
    });

    function act(action) {
      if (action === "float") float();
      else if (action === "popup") popOut();
      else if (action === "dock") dock(true);
      else if (action === "close") {
        dock();
        document.getElementById("open_diagnostics").focus();
      } else if (action === "maximize") {
        maximized = !maximized;
        updateMode();
      }
    }
    panel.addEventListener("click", event => {
      const button = event.target.closest("[data-state-action]");
      if (button) act(button.dataset.stateAction);
    });
    placeholder.addEventListener("click", event => {
      const button = event.target.closest("[data-state-action]");
      if (button) act(button.dataset.stateAction);
    });
    handle.addEventListener("pointerdown", event => {
      if (mode !== "floating" || maximized || event.button !== 0) return;
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
      if (mode !== "floating" || maximized || !steps[event.key]) return;
      event.preventDefault();
      position.x += steps[event.key][0];
      position.y += steps[event.key][1];
      positionPanel();
    });
    panel.addEventListener("keydown", event => {
      if (event.key === "Escape" && mode !== "docked") {
        event.preventDefault();
        event.stopPropagation();
        act("close");
      }
    });
    window.addEventListener("resize", positionPanel);
    new ResizeObserver(positionPanel).observe(panel);
    new MutationObserver(syncTheme).observe(document.documentElement, {
      attributes: true, attributeFilter: ["data-theme", "data-bs-theme"],
    });
    updateMode();
    return {
      current: panel.querySelector("#diagnostics_current_state"),
      list: panel.querySelector("#diagnostics_state_list"),
      dock,
    };
  },
};
