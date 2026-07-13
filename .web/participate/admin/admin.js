const THEME_STORAGE_KEY = "sira.participate.theme";

const columns = [
  { key: "id", label: "ID", type: "number" },
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
  { key: "ip_address", label: "IP" },
  { key: "user_agent", label: "User-Agent" }
];

const dataElement = document.getElementById("registration_data");
const rows = dataElement ? JSON.parse(dataElement.textContent || "[]") : [];
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
  return columns.map((column) => valueFor(row, column.key)).join(" ").toLocaleLowerCase("de-CH");
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
    "2026-08-17-morning": 0,
    "2026-08-17-afternoon": 0,
    unavailable: 0
  };

  rows.forEach((row) => {
    if (Object.prototype.hasOwnProperty.call(counts, row.slot_preference_key)) {
      counts[row.slot_preference_key] += 1;
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
        if (["id", "created_at", "updated_at", "date_of_birth", "slot_starts_at", "slot_ends_at", "slot_capacity", "status", "ip_address"].includes(column.key)) {
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
      actionCell.className = "nowrap";
      const deleteButton = document.createElement("button");
      deleteButton.className = "button table-action-button danger";
      deleteButton.type = "button";
      deleteButton.textContent = "Löschen";
      deleteButton.setAttribute("aria-label", `${valueFor(registration, "full_name") || "Anmeldung"} löschen`);
      deleteButton.addEventListener("click", () => deleteRegistration(registration));
      actionCell.append(deleteButton);
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

updateThemeToggle(currentTheme());
renderTable();
