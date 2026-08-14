const THEME_STORAGE_KEY = "sira.participate.theme";

const slotLabels = {
  "2026-08-17-morning": "Montag, 17. August 2026, 09:00 bis 13:00",
  "2026-08-17-afternoon": "Montag, 17. August 2026, 13:00 bis 17:00",
  unavailable: "Ich will gerne teilnehmen, aber diese Termine passen mir nicht"
};

const canvas = document.querySelector("[data-hero-canvas]");
const context = canvas.getContext("2d");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
let animationFrame = 0;
let pointer = { x: 0.66, y: 0.42 };
let savedRegistration = null;
let participantSession = {
  registered: false,
  signupOpen: true
};

function currentTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

function setTheme(theme, options = {}) {
  const nextTheme = theme === "dark" ? "dark" : "light";
  document.documentElement.dataset.theme = nextTheme;
  if (options.persist !== false) {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch (error) {
      // The visible theme can still change when storage is unavailable.
    }
  }
  updateThemeToggle(nextTheme);
  drawScene(performance.now());
}

function updateThemeToggle(theme) {
  const dark = theme === "dark";
  const label = dark ? "In den hellen Modus wechseln" : "In den dunklen Modus wechseln";
  document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
    button.title = label;
    button.setAttribute("aria-label", label);
    button.setAttribute("aria-pressed", dark ? "true" : "false");
    const symbol = button.querySelector(".theme-symbol");
    if (symbol) {
      symbol.textContent = dark ? "☼" : "◐";
    }
  });
}

function showAlert(message) {
  const alert = document.getElementById("status_alert");
  if (!alert) {
    return;
  }
  alert.textContent = message;
  alert.hidden = false;
  window.clearTimeout(showAlert.timeout);
  showAlert.timeout = window.setTimeout(() => {
    alert.hidden = true;
  }, 5000);
}

function openDialog(dialog) {
  if (!dialog) {
    return;
  }
  if (typeof dialog.showModal === "function") {
    dialog.showModal();
  } else {
    dialog.setAttribute("open", "");
  }
}

function closeDialog(dialog) {
  if (!dialog) {
    return;
  }
  if (typeof dialog.close === "function") {
    dialog.close();
  } else {
    dialog.removeAttribute("open");
  }
}

async function apiRequest(path, options = {}) {
  const headers = {
    Accept: "application/json",
    ...(options.headers || {})
  };
  if (options.body && !headers["Content-Type"]) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(path, {
    credentials: "same-origin",
    ...options,
    headers
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    payload = null;
  }

  if (!response.ok) {
    const apiError = new Error(payload?.message || "Die Anfrage konnte nicht verarbeitet werden.");
    apiError.status = response.status;
    apiError.payload = payload;
    throw apiError;
  }

  return payload || {};
}

function formValues() {
  const form = document.querySelector("[data-registration-form]");
  const data = new FormData(form);
  return {
    fullName: String(data.get("fullName") || "").trim(),
    dateOfBirth: String(data.get("dateOfBirth") || "").trim(),
    email: String(data.get("email") || "").trim(),
    slotPreference: String(data.get("slotPreference") || "").trim()
  };
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  return new Date(`${value}T00:00:00`).toLocaleDateString("de-CH", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  });
}

function summaryItems(values) {
  return [
    { label: "Name", value: values.fullName || "-" },
    { label: "Geburtsdatum", value: formatDate(values.dateOfBirth) },
    { label: "E-Mail", value: values.email || "-" },
    {
      label: "Terminpräferenz",
      value: values.slotPreferenceLabel || slotLabels[values.slotPreference] || "-",
      full: true
    }
  ];
}

function renderItems(target, items) {
  if (!target) {
    return;
  }
  target.textContent = "";
  items.forEach((entry) => {
    const item = document.createElement("div");
    item.className = `summary-item${entry.full ? " full" : ""}`;
    if (entry.field) {
      item.dataset.assignmentField = entry.field;
    }

    const labelElement = document.createElement("span");
    labelElement.className = "metric-label";
    labelElement.textContent = entry.label;

    const valueElement = document.createElement("strong");
    valueElement.textContent = entry.value ?? "-";

    item.append(labelElement, valueElement);
    if (entry.copy && entry.value) {
      const copyButton = document.createElement("button");
      copyButton.className = "copy-button";
      copyButton.type = "button";
      copyButton.textContent = "Kopieren";
      copyButton.setAttribute("aria-label", `${entry.label} kopieren`);
      copyButton.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(String(entry.value));
          showAlert("Der Zugangscode wurde kopiert.");
        } catch (error) {
          showAlert(`Zugangscode: ${entry.value}`);
        }
      });
      item.append(copyButton);
    }
    target.append(item);
  });
}

function renderSummary(target, values) {
  renderItems(target, summaryItems(values));
}

function assignmentItems(assignment, phase) {
  const items = [
    { label: "Teilnehmenden-ID", value: assignment.participantId, field: "participantId" },
    { label: "Halbtag", value: assignment.halfDaySlot, field: "halfDaySlot" },
    { label: "Zeitfenster", value: assignment.timeSlot, field: "timeSlot" },
    { label: "Datum", value: assignment.date, field: "date" }
  ];
  if (phase >= 3) {
    items.push(
      { label: "Zugangscode", value: assignment.accessCode, field: "accessCode", copy: true },
      { label: "Rolle", value: assignment.role, field: "role" },
      { label: "Team-ID", value: assignment.teamId, field: "teamId" },
      { label: "Raum", value: assignment.room, field: "room" }
    );
  }
  return items;
}

function formatTimestamp(value) {
  if (!value) {
    return "";
  }
  const parsed = new Date(String(value).replace(" ", "T"));
  if (Number.isNaN(parsed.getTime())) {
    return String(value);
  }
  return parsed.toLocaleString("de-CH", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function renderParticipantUi(session) {
  participantSession = {
    registered: false,
    signupOpen: true,
    ...session
  };
  savedRegistration = participantSession.registered ? participantSession.registration : null;

  const section = document.querySelector("[data-local-summary-section]");
  const target = document.querySelector("[data-local-summary]");
  const heading = document.querySelector("[data-participant-heading]");
  const panelTitle = document.querySelector("[data-participant-panel-title]");
  const phaseLabel = document.querySelector("[data-participant-phase-label]");
  const note = document.querySelector("[data-participant-note]");
  const interestForm = document.querySelector("[data-results-interest-form]");
  const interestCheckbox = interestForm?.elements.resultsInterest;
  const interestStatus = document.querySelector("[data-interest-status]");
  const signupButton = document.querySelector("[data-signup-action]");
  const signupLabel = document.querySelector("[data-signup-label]");
  const signupIcon = document.querySelector("[data-signup-icon]");
  const signupStatus = document.querySelector("[data-signup-status]");
  const registered = participantSession.registered === true;

  if (signupButton) {
    signupButton.hidden = !registered && participantSession.signupOpen === false;
  }
  if (signupLabel) {
    signupLabel.textContent = registered ? "Zu meinen Informationen" : "Mitmachen";
  }
  if (signupIcon) {
    signupIcon.textContent = registered ? "→" : "+";
  }
  document.querySelectorAll("[data-open-recovery]").forEach((button) => {
    button.hidden = registered;
  });
  if (signupStatus) {
    signupStatus.hidden = registered || participantSession.signupOpen !== false;
  }

  if (!section || !target) {
    return;
  }
  section.hidden = !registered;
  if (!registered) {
    target.textContent = "";
    return;
  }

  const phase = Number(participantSession.phase?.number || 1);
  phaseLabel.textContent = `Phase ${phase} · ${participantSession.phase?.label || "Anmeldung"}`;
  target.hidden = phase === 4;
  interestForm.hidden = phase !== 4;

  if (phase === 1) {
    heading.textContent = "Deine Anmeldung";
    panelTitle.textContent = "Gespeicherte Zusammenfassung";
    renderSummary(target, participantSession.registration);
    note.innerHTML = "Du kannst deine Anmeldung auf diesem oder einem anderen Gerät mit deiner E-Mail-Adresse und deinem Geburtsdatum wieder aufrufen. Bei Fragen kontaktiere bitte <a href=\"mailto:alexandre.despindler@zhaw.ch\">alexandre.despindler@zhaw.ch</a>.";
  } else if (phase === 2) {
    heading.textContent = "Dein Termin";
    panelTitle.textContent = "Terminübersicht";
    renderItems(target, assignmentItems(participantSession.assignment || {}, phase));
    note.textContent = "Weitere Informationen zu Zugangscode, Rolle, Team und Raum werden hier angezeigt, sobald deine vollständige Zuteilung freigegeben ist.";
  } else if (phase === 3) {
    heading.textContent = "Deine Zuteilung";
    panelTitle.textContent = "Vollständige Teilnahmeinformationen";
    renderItems(target, assignmentItems(participantSession.assignment || {}, phase));
    note.textContent = "Bitte halte insbesondere deinen Zugangscode für die Teilnahme bereit.";
  } else {
    heading.textContent = "Vielen Dank für Deine Teilnahme";
    panelTitle.textContent = "Ergebnisinformationen";
    target.textContent = "";
    if (interestCheckbox) {
      interestCheckbox.checked = participantSession.resultsInterest === true;
    }
    if (interestStatus) {
      if (participantSession.resultsInterest === null) {
        interestStatus.textContent = "Noch keine Auswahl gespeichert.";
      } else {
        const choice = participantSession.resultsInterest ? "Ja" : "Nein";
        const changed = formatTimestamp(participantSession.resultsInterestUpdatedAt);
        interestStatus.textContent = `Gespeichert: ${choice}${changed ? ` · ${changed}` : ""}`;
      }
    }
    note.textContent = "Du kannst diese Auswahl jederzeit ändern und erneut speichern.";
  }
}

async function refreshRegistrationUi() {
  try {
    const payload = await apiRequest("api/registration.php");
    renderParticipantUi(payload);
  } catch (error) {
    renderParticipantUi({ registered: false, signupOpen: true });
  }
}

async function submitRegistration(values) {
  const payload = await apiRequest("api/register.php", {
    method: "POST",
    body: JSON.stringify(values)
  });
  renderParticipantUi(payload);
  return payload;
}

function initWizard() {
  const form = document.querySelector("[data-registration-form]");
  const dialog = document.querySelector("[data-registration-dialog]");
  const steps = Array.from(form.querySelectorAll("[data-step-panel]"));
  const tabs = Array.from(form.querySelectorAll("[data-step-target]"));
  const alert = form.querySelector("[data-validation-alert]");
  let current = 0;
  let submitting = false;

  function clearAlert() {
    alert.hidden = true;
    alert.textContent = "";
  }

  function showValidation(message) {
    alert.textContent = message;
    alert.hidden = false;
    alert.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  function showStep(index) {
    current = Math.max(0, Math.min(index, steps.length - 1));
    steps.forEach((step, stepIndex) => {
      step.classList.toggle("active", stepIndex === current);
    });
    tabs.forEach((tab, tabIndex) => {
      const active = tabIndex === current;
      tab.classList.toggle("active", active);
      if (active) {
        tab.setAttribute("aria-current", "step");
      } else {
        tab.removeAttribute("aria-current");
      }
    });
    if (current === 2) {
      renderSummary(form.querySelector("[data-review-summary]"), formValues());
    }
    clearAlert();
  }

  function validateStep(index) {
    const values = formValues();
    if (index === 0) {
      if (!values.fullName) {
        showValidation("Bitte gib deinen vollständigen Namen ein.");
        form.elements.fullName.focus();
        return false;
      }
      if (!values.dateOfBirth) {
        showValidation("Bitte gib dein Geburtsdatum ein.");
        form.elements.dateOfBirth.focus();
        return false;
      }
      if (!form.elements.email.validity.valid) {
        showValidation("Bitte gib eine gültige E-Mail-Adresse ein.");
        form.elements.email.focus();
        return false;
      }
    }
    if (index === 1 && !values.slotPreference) {
      showValidation("Bitte wähle eine Terminpräferenz aus.");
      form.querySelector('input[name="slotPreference"]').focus();
      return false;
    }
    return true;
  }

  function validateAll() {
    for (let index = 0; index < steps.length - 1; index += 1) {
      if (!validateStep(index)) {
        showStep(index);
        validateStep(index);
        return false;
      }
    }
    return true;
  }

  form.querySelectorAll("[data-next]").forEach((button) => {
    button.addEventListener("click", () => {
      if (validateStep(current)) {
        showStep(current + 1);
      }
    });
  });

  form.querySelectorAll("[data-prev]").forEach((button) => {
    button.addEventListener("click", () => showStep(current - 1));
  });

  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => showStep(index));
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (!validateAll()) {
      return;
    }
    const values = formValues();
    const submitButton = form.querySelector('.wizard-step.active button[type="submit"]');
    submitting = true;
    if (submitButton) {
      submitButton.disabled = true;
      submitButton.textContent = "Wird gesendet...";
    }
    try {
      await submitRegistration(values);
      closeDialog(dialog);
      form.reset();
      showStep(0);
      showAlert("Deine Anmeldung ist eingegangen. Weitere Informationen werden an deine E-Mail-Adresse gesendet.");
    } catch (error) {
      showValidation(error.message);
    } finally {
      submitting = false;
      if (submitButton) {
        submitButton.disabled = false;
        submitButton.textContent = "Teilnahmeanfrage absenden";
      }
    }
  });

  document.querySelectorAll("[data-open-registration]").forEach((button) => {
    button.addEventListener("click", () => {
      if (savedRegistration) {
        document.querySelector("[data-local-summary-section]")?.scrollIntoView({ behavior: "smooth" });
        return;
      }
      showStep(0);
      openDialog(dialog);
    });
  });

  document.querySelectorAll("[data-close-registration]").forEach((button) => {
    button.addEventListener("click", () => closeDialog(dialog));
  });

  showStep(0);
}

function initRecovery() {
  const dialog = document.querySelector("[data-recovery-dialog]");
  const form = document.querySelector("[data-recovery-form]");
  const alert = document.querySelector("[data-recovery-alert]");
  if (!dialog || !form || !alert) {
    return;
  }
  let submitting = false;

  document.querySelectorAll("[data-open-recovery]").forEach((button) => {
    button.addEventListener("click", () => {
      alert.hidden = true;
      alert.textContent = "";
      openDialog(dialog);
    });
  });
  document.querySelectorAll("[data-close-recovery]").forEach((button) => {
    button.addEventListener("click", () => closeDialog(dialog));
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting) {
      return;
    }
    const data = new FormData(form);
    const values = {
      email: String(data.get("email") || "").trim(),
      dateOfBirth: String(data.get("dateOfBirth") || "").trim()
    };
    if (!form.elements.email.validity.valid || !values.dateOfBirth) {
      alert.textContent = "Bitte gib deine E-Mail-Adresse und dein Geburtsdatum ein.";
      alert.hidden = false;
      return;
    }

    const submitButton = form.querySelector('button[type="submit"]');
    submitting = true;
    submitButton.disabled = true;
    submitButton.textContent = "Wird aufgerufen...";
    try {
      const payload = await apiRequest("api/identify.php", {
        method: "POST",
        body: JSON.stringify(values)
      });
      renderParticipantUi(payload);
      closeDialog(dialog);
      form.reset();
      document.querySelector("[data-participant-section]")?.scrollIntoView({ behavior: "smooth" });
      showAlert("Deine Anmeldung wurde auf diesem Gerät aufgerufen.");
    } catch (error) {
      alert.textContent = error.message;
      alert.hidden = false;
    } finally {
      submitting = false;
      submitButton.disabled = false;
      submitButton.textContent = "Anmeldung aufrufen";
    }
  });
}

function initResultsInterest() {
  const form = document.querySelector("[data-results-interest-form]");
  if (!form) {
    return;
  }
  let submitting = false;
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (submitting || participantSession.phase?.number !== 4) {
      return;
    }
    const submitButton = form.querySelector('button[type="submit"]');
    const interest = form.elements.resultsInterest.checked;
    submitting = true;
    submitButton.disabled = true;
    submitButton.textContent = "Wird gespeichert...";
    try {
      const payload = await apiRequest("api/results-interest.php", {
        method: "POST",
        body: JSON.stringify({ interest })
      });
      renderParticipantUi({
        ...participantSession,
        resultsInterest: payload.resultsInterest,
        resultsInterestUpdatedAt: payload.resultsInterestUpdatedAt
      });
      showAlert("Deine Auswahl wurde gespeichert.");
    } catch (error) {
      showAlert(error.message);
    } finally {
      submitting = false;
      submitButton.disabled = false;
      submitButton.textContent = "Auswahl speichern";
    }
  });
}

function initPrivacyDialog() {
  const dialog = document.querySelector("[data-privacy-dialog]");
  document.querySelectorAll("[data-open-privacy]").forEach((button) => {
    button.addEventListener("click", () => openDialog(dialog));
  });
  document.querySelectorAll("[data-close-privacy]").forEach((button) => {
    button.addEventListener("click", () => closeDialog(dialog));
  });
}

function resizeCanvas() {
  const ratio = Math.max(1, window.devicePixelRatio || 1);
  const rect = canvas.getBoundingClientRect();
  canvas.width = Math.max(1, Math.round(rect.width * ratio));
  canvas.height = Math.max(1, Math.round(rect.height * ratio));
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  drawScene(performance.now());
}

function sceneColors() {
  const dark = currentTheme() === "dark";
  return {
    background: dark ? "#121b1e" : "#eef4f2",
    grid: dark ? "rgba(226, 232, 240, 0.12)" : "rgba(12, 22, 24, 0.11)",
    line: dark ? "rgba(20, 184, 166, 0.34)" : "rgba(0, 176, 162, 0.38)",
    axis: dark ? "rgba(255, 138, 34, 0.46)" : "rgba(255, 122, 0, 0.46)",
    text: dark ? "rgba(226, 232, 240, 0.72)" : "rgba(12, 22, 24, 0.6)",
    point: dark ? "#14b8a6" : "#00b0a2",
    pointAlt: dark ? "#ff8a22" : "#ff7a00",
    blue: dark ? "#60a5fa" : "#1d4ed8"
  };
}

function drawScene(time) {
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  if (!width || !height) {
    return;
  }
  const colors = sceneColors();
  const compact = width < 720;
  if (compact) {
    colors.grid = currentTheme() === "dark" ? "rgba(226, 232, 240, 0.08)" : "rgba(12, 22, 24, 0.07)";
    colors.line = currentTheme() === "dark" ? "rgba(20, 184, 166, 0.2)" : "rgba(0, 176, 162, 0.2)";
    colors.axis = currentTheme() === "dark" ? "rgba(255, 138, 34, 0.2)" : "rgba(255, 122, 0, 0.2)";
  }
  context.clearRect(0, 0, width, height);
  context.fillStyle = colors.background;
  context.fillRect(0, 0, width, height);

  const grid = 48;
  context.lineWidth = 1;
  context.strokeStyle = colors.grid;
  for (let x = -grid; x < width + grid; x += grid) {
    context.beginPath();
    context.moveTo(x, 0);
    context.lineTo(x + height * 0.28, height);
    context.stroke();
  }
  for (let y = 0; y < height + grid; y += grid) {
    context.beginPath();
    context.moveTo(0, y);
    context.lineTo(width, y - width * 0.12);
    context.stroke();
  }

  const centerX = width * (compact ? 0.72 : 0.68);
  const centerY = height * 0.48;
  const radius = Math.min(width, height) * (compact ? 0.22 : 0.3);
  const phase = reduceMotion.matches ? 0 : time * 0.00025;
  const labels = ["Team", "KI", "Ideen", "Entscheid", "Verständnis", "Aufgabe"];
  const nodes = labels.map((label, index) => {
    const angle = -Math.PI / 2 + index * (Math.PI * 2 / labels.length);
    const modulation = 0.72 + ((Math.sin(phase + index * 1.4) + 1) * 0.1);
    return {
      label,
      x: centerX + Math.cos(angle) * radius * modulation,
      y: centerY + Math.sin(angle) * radius * modulation,
      axisX: centerX + Math.cos(angle) * radius,
      axisY: centerY + Math.sin(angle) * radius
    };
  });

  context.strokeStyle = colors.axis;
  context.lineWidth = 1.5;
  nodes.forEach((node) => {
    context.beginPath();
    context.moveTo(centerX, centerY);
    context.lineTo(node.axisX, node.axisY);
    context.stroke();
  });

  context.strokeStyle = colors.line;
  context.lineWidth = 2;
  context.beginPath();
  nodes.forEach((node, index) => {
    if (index === 0) {
      context.moveTo(node.x, node.y);
    } else {
      context.lineTo(node.x, node.y);
    }
  });
  context.closePath();
  context.stroke();
  context.fillStyle = currentTheme() === "dark" ? "rgba(20, 184, 166, 0.1)" : "rgba(0, 176, 162, 0.1)";
  context.fill();

  if (!compact) {
    context.font = "700 12px Space Grotesk, Segoe UI, sans-serif";
    context.textAlign = "center";
    nodes.forEach((node, index) => {
      context.fillStyle = index % 3 === 0 ? colors.pointAlt : index % 3 === 1 ? colors.point : colors.blue;
      context.beginPath();
      context.rect(node.x - 4, node.y - 4, 8, 8);
      context.fill();
      context.fillStyle = colors.text;
      context.fillText(node.label, node.axisX, node.axisY + (node.axisY > centerY ? 20 : -12));
    });

    const pointerX = width * pointer.x;
    const pointerY = height * pointer.y;
    context.strokeStyle = currentTheme() === "dark" ? "rgba(255, 255, 255, 0.34)" : "rgba(12, 22, 24, 0.3)";
    context.lineWidth = 1;
    context.setLineDash([6, 8]);
    context.beginPath();
    context.moveTo(pointerX, 0);
    context.lineTo(pointerX, height);
    context.moveTo(0, pointerY);
    context.lineTo(width, pointerY);
    context.stroke();
    context.setLineDash([]);
    context.fillStyle = colors.pointAlt;
    context.fillRect(pointerX - 5, pointerY - 5, 10, 10);
    context.fillStyle = colors.text;
    context.textAlign = "left";
    context.fillText("Studienraum", pointerX + 12, pointerY - 10);
  }
}

function animate(time) {
  drawScene(time);
  if (!reduceMotion.matches) {
    animationFrame = window.requestAnimationFrame(animate);
  }
}

document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
  button.addEventListener("click", () => {
    setTheme(currentTheme() === "dark" ? "light" : "dark");
  });
});

canvas.addEventListener("pointermove", (event) => {
  const rect = canvas.getBoundingClientRect();
  pointer = {
    x: Math.min(0.92, Math.max(0.12, (event.clientX - rect.left) / rect.width)),
    y: Math.min(0.86, Math.max(0.16, (event.clientY - rect.top) / rect.height))
  };
  drawScene(performance.now());
});

window.addEventListener("resize", resizeCanvas);
reduceMotion.addEventListener("change", () => {
  window.cancelAnimationFrame(animationFrame);
  resizeCanvas();
  if (!reduceMotion.matches) {
    animationFrame = window.requestAnimationFrame(animate);
  }
});

updateThemeToggle(currentTheme());
initWizard();
initRecovery();
initResultsInterest();
initPrivacyDialog();
refreshRegistrationUi();
resizeCanvas();
animationFrame = window.requestAnimationFrame(animate);
