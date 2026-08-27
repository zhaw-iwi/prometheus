const THEME_STORAGE_KEY = "sira.participate.theme";

const columns = [
  { key: "id", label: "ID", type: "number" },
  { key: "effective_phase", label: "Phase", type: "number" },
  { key: "missing_assignment_data", label: "Fehlende Daten" },
  { key: "created_at", label: "Eingegangen" },
  { key: "updated_at", label: "Geändert" },
  { key: "full_name", label: "Name" },
  { key: "date_of_birth", label: "Geburtsdatum" },
  { key: "email", label: "E-Mail" },
  { key: "slot_preference_label", label: "Terminpräferenz" },
  { key: "slot_starts_at", label: "Slot Start" },
  { key: "slot_ends_at", label: "Slot Ende" },
  { key: "slot_capacity", label: "Kapazität", type: "number" },
  { key: "status", label: "Status" },
  { key: "half_day_slot", label: "Halbtag" },
  { key: "time_slot", label: "Zeitfenster" },
  { key: "access_code", label: "Zugangscode" },
  { key: "participant_role", label: "Rolle" },
  { key: "team_id", label: "Team-ID" },
  { key: "room", label: "Raum" },
  { key: "results_interest_label", label: "Ergebnisinfo" },
  { key: "results_interest_updated_at", label: "Ergebnisinfo geändert" },
  { key: "ip_address", label: "IP" },
  { key: "user_agent", label: "User-Agent" }
];

const dataElement = document.getElementById("registration_data");
const rows = dataElement ? JSON.parse(dataElement.textContent || "[]") : [];
const phaseSettingsElement = document.getElementById("phase_settings_data");
const phaseSettings = phaseSettingsElement
  ? JSON.parse(phaseSettingsElement.textContent || "{}")
  : { defaultPhase: 1, phaseLabels: {} };
const state = {
  search: "",
  sortKey: "created_at",
  sortDirection: "desc"
};

function currentTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
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
      symbol.textContent = dark ? "☼" : "●";
    }
  });
}

function setTheme(theme) {
  const nextTheme = theme === "dark" ? "dark" : "light";
  document.documentElement.dataset.theme = nextTheme;
  try {
    localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
  } catch (error) {
    // Theme switching still works without persistence.
  }
  updateThemeToggle(nextTheme);
}

function valueFor(row, key) {
  const value = row[key];
  return value === null || value === undefined || value === "" ? "" : String(value);
}

function searchableText(row) {
  return [
    ...columns.map((column) => valueFor(row, column.key)),
    valueFor(row, "phase_summary")
  ].join(" ").toLocaleLowerCase("de-CH");
}

function filteredRows() {
  const query = state.search.trim().toLocaleLowerCase("de-CH");
  const visible = query
    ? rows.filter((row) => searchableText(row).includes(query))
    : [...rows];

  const column = columns.find((entry) => entry.key === state.sortKey);
  visible.sort((left, right) => {
    const leftValue = valueFor(left, state.sortKey);
    const rightValue = valueFor(right, state.sortKey);

    let result = 0;
    if (column?.type === "number") {
      result = (Number(leftValue) || 0) - (Number(rightValue) || 0);
    } else {
      result = leftValue.localeCompare(rightValue, "de-CH", {
        numeric: true,
        sensitivity: "base"
      });
    }
    return state.sortDirection === "asc" ? result : -result;
  });

  return visible;
}

function updateMetrics() {
  const counts = {
    total: rows.length,
    "phase-1": 0,
    "phase-2": 0,
    "phase-3": 0,
    "phase-4": 0
  };

  rows.forEach((row) => {
    const key = `phase-${row.effective_phase}`;
    if (Object.prototype.hasOwnProperty.call(counts, key)) {
      counts[key] += 1;
    }
  });

  Object.entries(counts).forEach(([key, value]) => {
    const metric = document.querySelector(`[data-metric="${key}"]`);
    if (metric) {
      metric.textContent = String(value);
    }
  });
}

function formatCell(row, column) {
  if (column.key === "effective_phase") {
    return valueFor(row, "phase_summary") || "Phase 1 · Anmeldung";
  }
  const value = valueFor(row, column.key);
  return value || "-";
}

function renderTable() {
  const body = document.querySelector("[data-table-body]");
  const count = document.querySelector("[data-row-count]");
  if (!body || !count) {
    return;
  }
  updateMetrics();

  const visible = filteredRows();
  body.textContent = "";

  if (visible.length === 0) {
    const row = document.createElement("tr");
    row.className = "empty-row";
    const cell = document.createElement("td");
    cell.colSpan = columns.length + 1;
    cell.textContent = "Keine passenden Einträge";
    row.append(cell);
    body.append(row);
  } else {
    visible.forEach((registration) => {
      const row = document.createElement("tr");
      columns.forEach((column) => {
        const cell = document.createElement("td");
        cell.textContent = formatCell(registration, column);
        if (["id", "effective_phase", "created_at", "updated_at", "date_of_birth", "slot_starts_at", "slot_ends_at", "slot_capacity", "status", "participant_role", "team_id", "room", "results_interest_label", "results_interest_updated_at", "ip_address"].includes(column.key)) {
          cell.classList.add("nowrap");
        }
        if (column.key === "user_agent") {
          cell.classList.add("clip");
          cell.title = formatCell(registration, column);
        }
        if (!valueFor(registration, column.key)) {
          cell.classList.add("muted");
        }
        row.append(cell);
      });
      const actionCell = document.createElement("td");
      actionCell.className = "nowrap table-actions";
      const editButton = document.createElement("button");
      editButton.className = "button table-action-button";
      editButton.type = "button";
      editButton.textContent = "Bearbeiten";
      editButton.setAttribute("aria-label", `${valueFor(registration, "full_name") || "Anmeldung"} bearbeiten`);
      editButton.addEventListener("click", () => openParticipantEditor(registration));
      const deleteButton = document.createElement("button");
      deleteButton.className = "button table-action-button danger";
      deleteButton.type = "button";
      deleteButton.textContent = "Löschen";
      deleteButton.setAttribute("aria-label", `${valueFor(registration, "full_name") || "Anmeldung"} löschen`);
      deleteButton.addEventListener("click", () => deleteRegistration(registration));
      actionCell.append(editButton, deleteButton);
      row.append(actionCell);
      body.append(row);
    });
  }

  count.textContent = `${visible.length} von ${rows.length} Einträgen`;

  document.querySelectorAll("[data-sort]").forEach((button) => {
    if (button.dataset.sort === state.sortKey) {
      button.dataset.sortActive = state.sortDirection;
    } else {
      delete button.dataset.sortActive;
    }
  });
}

function csvEscape(value) {
  const text = value === null || value === undefined ? "" : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

async function apiRequest(path, options = {}) {
  const response = await fetch(path, {
    credentials: "same-origin",
    ...options,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    payload = null;
  }
  if (!response.ok) {
    const apiError = new Error(payload?.message || "Die Änderung konnte nicht gespeichert werden.");
    apiError.payload = payload;
    throw apiError;
  }
  return payload || {};
}

function openDialog(dialog) {
  if (typeof dialog?.showModal === "function") {
    dialog.showModal();
  } else {
    dialog?.setAttribute("open", "");
  }
}

function closeDialog(dialog) {
  if (typeof dialog?.close === "function") {
    dialog.close();
  } else {
    dialog?.removeAttribute("open");
  }
}

function participantFormField(form, name) {
  return form.elements.namedItem(name);
}

function configureParticipantEditor(mode, registration = null) {
  const dialog = document.querySelector("[data-participant-dialog]");
  const form = document.querySelector("[data-participant-form]");
  if (!dialog || !form) {
    return null;
  }

  form.reset();
  participantFormField(form, "mode").value = mode;
  participantFormField(form, "id").value = registration ? valueFor(registration, "id") : "";
  participantFormField(form, "participantId").value = registration ? valueFor(registration, "id") : "";
  participantFormField(form, "participantId").required = mode === "edit";
  participantFormField(form, "fullName").value = registration ? valueFor(registration, "full_name") : "";
  participantFormField(form, "email").value = registration ? valueFor(registration, "email") : "";
  participantFormField(form, "dateOfBirth").value = registration ? valueFor(registration, "date_of_birth") : "";
  participantFormField(form, "slotId").value = registration ? valueFor(registration, "slot_id") : "";
  participantFormField(form, "phaseOverride").value = registration?.phase_override ?? "";
  participantFormField(form, "halfDaySlot").value = registration ? valueFor(registration, "half_day_slot") : "";
  participantFormField(form, "timeSlot").value = registration ? valueFor(registration, "time_slot") : "";
  participantFormField(form, "accessCode").value = registration ? valueFor(registration, "access_code") : "";
  participantFormField(form, "role").value = registration ? valueFor(registration, "participant_role") : "";
  participantFormField(form, "teamId").value = registration ? valueFor(registration, "team_id") : "";
  participantFormField(form, "room").value = registration ? valueFor(registration, "room") : "";

  const creating = mode === "create";
  document.querySelector("[data-editor-kicker]").textContent = creating
    ? "Neue teilnehmende Person"
    : `Teilnehmenden-ID ${valueFor(registration, "id")}`;
  document.querySelector("[data-editor-title]").textContent = creating
    ? "Teilnehmende Person erstellen"
    : "Teilnehmende Person bearbeiten";
  document.querySelector("[data-default-phase-option]").textContent = creating
    ? "Phase 1 · Anmeldung (Standard für neue Teilnehmende)"
    : "Standardphase übernehmen";
  document.querySelector("[data-editor-note]").textContent = creating
    ? "Nur E-Mail-Adresse und Geburtsdatum sind erforderlich. Es wird keine Bestätigungs-E-Mail versendet."
    : "Leere optionale Felder werden als NULL gespeichert und können die sichtbare Phase begrenzen.";
  document.querySelector("[data-participant-submit]").textContent = creating
    ? "Person erstellen"
    : "Änderungen speichern";

  const alert = form.querySelector("[data-participant-alert]");
  alert.hidden = true;
  alert.textContent = "";
  return { dialog, form };
}

function openParticipantCreator() {
  const editor = configureParticipantEditor("create");
  if (editor) {
    openDialog(editor.dialog);
  }
}

function openParticipantEditor(registration) {
  const editor = configureParticipantEditor("edit", registration);
  if (editor) {
    openDialog(editor.dialog);
  }
}

async function saveParticipant(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const alert = form.querySelector("[data-participant-alert]");
  const submitButton = form.querySelector('button[type="submit"]');
  const creating = participantFormField(form, "mode").value === "create";
  const payload = {
    action: creating ? "create_participant" : "save_participant",
    id: participantFormField(form, "id").value,
    participantId: participantFormField(form, "participantId").value,
    fullName: participantFormField(form, "fullName").value,
    email: participantFormField(form, "email").value,
    dateOfBirth: participantFormField(form, "dateOfBirth").value,
    slotId: participantFormField(form, "slotId").value,
    phaseOverride: participantFormField(form, "phaseOverride").value || null,
    halfDaySlot: participantFormField(form, "halfDaySlot").value,
    timeSlot: participantFormField(form, "timeSlot").value,
    accessCode: participantFormField(form, "accessCode").value,
    role: participantFormField(form, "role").value,
    teamId: participantFormField(form, "teamId").value,
    room: participantFormField(form, "room").value
  };

  submitButton.disabled = true;
  alert.hidden = true;
  try {
    await apiRequest("update.php", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    window.location.reload();
  } catch (error) {
    alert.textContent = error.message;
    alert.hidden = false;
  } finally {
    submitButton.disabled = false;
  }
}

async function saveDefaultPhase(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const select = participantFormField(form, "defaultPhase");
  const nextPhase = Number(select.value);
  const currentPhase = Number(phaseSettings.defaultPhase || 1);
  if (nextPhase === currentPhase) {
    return;
  }

  const nextLabel = phaseSettings.phaseLabels?.[nextPhase] || "";
  const signupNotice = currentPhase === 1 && nextPhase > 1
    ? " Die Neuanmeldung wird dadurch geschlossen."
    : "";
  if (!window.confirm(`Gesamtphase wirklich auf Phase ${nextPhase} · ${nextLabel} setzen?${signupNotice}`)) {
    select.value = String(currentPhase);
    return;
  }

  const submitButton = form.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  try {
    await apiRequest("update.php", {
      method: "POST",
      body: JSON.stringify({ action: "set_default_phase", defaultPhase: nextPhase })
    });
    window.location.reload();
  } catch (error) {
    window.alert(error.message);
    select.value = String(currentPhase);
  } finally {
    submitButton.disabled = false;
  }
}

async function deleteRegistration(registration) {
  const name = valueFor(registration, "full_name") || valueFor(registration, "email") || `ID ${registration.id}`;
  if (!window.confirm(`Anmeldung von ${name} wirklich löschen? Die Person kann sich danach erneut anmelden.`)) {
    return;
  }

  const response = await fetch("delete.php", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ id: registration.id })
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (error) {
    payload = null;
  }

  if (!response.ok) {
    window.alert(payload?.message || "Die Anmeldung konnte nicht gelöscht werden.");
    return;
  }

  const index = rows.findIndex((row) => String(row.id) === String(registration.id));
  if (index >= 0) {
    rows.splice(index, 1);
  }
  renderTable();
}

function exportCsv() {
  const header = columns.map((column) => csvEscape(column.label)).join(",");
  const body = rows.map((row) => (
    columns.map((column) => csvEscape(valueFor(row, column.key))).join(",")
  ));
  const csv = [header, ...body].join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  const date = new Date().toISOString().slice(0, 10);
  link.href = URL.createObjectURL(blob);
  link.download = `sira-participation-registrations-${date}.csv`;
  document.body.append(link);
  link.click();
  URL.revokeObjectURL(link.href);
  link.remove();
}

document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
  button.addEventListener("click", () => {
    setTheme(currentTheme() === "dark" ? "light" : "dark");
  });
});

document.querySelector("[data-search]")?.addEventListener("input", (event) => {
  state.search = event.target.value;
  renderTable();
});

document.querySelectorAll("[data-sort]").forEach((button) => {
  button.addEventListener("click", () => {
    const key = button.dataset.sort;
    if (state.sortKey === key) {
      state.sortDirection = state.sortDirection === "asc" ? "desc" : "asc";
    } else {
      state.sortKey = key;
      state.sortDirection = "asc";
    }
    renderTable();
  });
});

document.querySelector("[data-export-csv]")?.addEventListener("click", exportCsv);
document.querySelector("[data-create-participant]")?.addEventListener("click", openParticipantCreator);
document.querySelector("[data-default-phase-form]")?.addEventListener("submit", saveDefaultPhase);
document.querySelector("[data-participant-form]")?.addEventListener("submit", saveParticipant);
document.querySelectorAll("[data-close-participant-dialog]").forEach((button) => {
  button.addEventListener("click", () => closeDialog(document.querySelector("[data-participant-dialog]")));
});

updateThemeToggle(currentTheme());
renderTable();
