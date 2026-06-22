const state = {
  adminToken: null,
  agentTypes: [],
  accessCodes: [],
  accessCodePresets: [],
  selectedAccessCodeId: null,
  selectedAgents: [],
  agentTypeFilter: "",
  expandedAgentTypePackages: new Set(),
  presetMenuOpen: false,
  activeAccessCodePresetKey: null,
};

const ADMIN_TOKEN_STORAGE_KEY = "prometheus.valerianAdmin.adminToken";
const ADMIN_TOKEN_HEADER = "X-Prometheus-Admin-Token";
const GENERATED_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

window.addEventListener("load", init);

function init() {
  wireUi();
  renderAccessCodes();
  renderAgentTypes();
  renderInstances();
  renderPresetMenu();
  const storedToken = sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY);
  if (storedToken) {
    document.getElementById("admin_token_input").value = storedToken;
    openAdminSession(storedToken, { fromStorage: true });
  } else {
    showWorkspace(false);
    setAdminStatus("");
  }
}

function wireUi() {
  document.getElementById("submit_admin_token").addEventListener("click", submitAdminToken);
  document.getElementById("admin_token_input").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      submitAdminToken();
    }
  });
  document.getElementById("forget_admin_token").addEventListener("click", forgetAdminToken);
  document.getElementById("access_code_preset_menu_button").addEventListener("click", togglePresetMenu);
  document.getElementById("close_access_code_preset_modal").addEventListener("click", closePresetModal);
  document.getElementById("cancel_access_code_preset").addEventListener("click", closePresetModal);
  document.getElementById("create_access_code_preset").addEventListener("click", createAccessCodePreset);
  document.getElementById("access_code_preset_modal").addEventListener("click", (event) => {
    if (event.target.id === "access_code_preset_modal") {
      closePresetModal();
    }
  });
  document.addEventListener("click", (event) => {
    const wrap = document.querySelector(".preset-menu-wrap");
    if (state.presetMenuOpen && wrap && !wrap.contains(event.target)) {
      state.presetMenuOpen = false;
      renderPresetMenu();
    }
  });
  document.getElementById("refresh_admin_data").addEventListener("click", refreshAdminData);
  document.getElementById("generate_access_code").addEventListener("click", generateAccessCode);
  document.getElementById("create_access_code").addEventListener("click", createAccessCode);
  document.getElementById("save_agent_type_assignment").addEventListener("click", saveAgentTypeAssignments);
  document.getElementById("refresh_instances").addEventListener("click", loadSelectedInstances);
  document.getElementById("agent_type_filter").addEventListener("input", (event) => {
    state.agentTypeFilter = event.target.value || "";
    renderAgentTypes();
  });
}

async function submitAdminToken() {
  await openAdminSession(document.getElementById("admin_token_input").value);
}

async function openAdminSession(token, options = {}) {
  if (!token) {
    setAdminStatus("Admin token required.", "error");
    return false;
  }
  state.adminToken = token;
  setAdminStatus("Checking admin token.");
  try {
    await loadAdminData();
    sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token);
    showWorkspace(true);
    setAdminStatus(options.fromStorage ? "" : "Admin token accepted.", "success");
    setConnectionState("Admin Connected", "active");
    return true;
  } catch (error) {
    state.adminToken = null;
    sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY);
    showWorkspace(false);
    setConnectionState("Admin Token Required", "unknown");
    setAdminStatus(error.message === "unauthorized" ? "Admin token rejected." : error.message, "error");
    return false;
  }
}

function forgetAdminToken() {
  state.adminToken = null;
  state.agentTypes = [];
  state.accessCodes = [];
  state.accessCodePresets = [];
  state.selectedAccessCodeId = null;
  state.selectedAgents = [];
  state.agentTypeFilter = "";
  state.expandedAgentTypePackages = new Set();
  state.presetMenuOpen = false;
  state.activeAccessCodePresetKey = null;
  sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY);
  document.getElementById("admin_token_input").value = "";
  document.getElementById("agent_type_filter").value = "";
  showWorkspace(false);
  setConnectionState("Admin Token Required", "unknown");
  setAdminStatus("");
  renderAccessCodes();
  renderAgentTypes();
  renderInstances();
  renderPresetMenu();
  closePresetModal();
}

async function loadAdminData() {
  if (!state.adminToken) {
    throw new Error("Admin token required.");
  }
  const [agentTypes, accessCodes, accessCodePresets] = await Promise.all([
    adminJson("/admin/agent-types"),
    adminJson("/admin/access-codes"),
    adminJson("/admin/access-code-presets"),
  ]);
  state.agentTypes = Array.isArray(agentTypes) ? agentTypes : [];
  state.accessCodes = Array.isArray(accessCodes) ? accessCodes : [];
  state.accessCodePresets = Array.isArray(accessCodePresets) ? accessCodePresets : [];
  if (!selectedAccessCode()) {
    state.selectedAccessCodeId = state.accessCodes.length > 0 ? state.accessCodes[0].id : null;
  }
  renderAccessCodes();
  renderAgentTypes();
  renderPresetMenu();
  await loadSelectedInstances();
}

async function refreshAdminData() {
  setAdminStatus("Refreshing admin data.");
  try {
    await loadAdminData();
    setAdminStatus("Admin data refreshed.", "success");
  } catch (error) {
    setAdminStatus(error.message === "unauthorized" ? "Admin token rejected." : "Refresh failed.", "error");
  }
}

async function createAccessCode() {
  const code = document.getElementById("new_access_code_input").value.trim();
  if (!/^[A-Za-z0-9]{5}$/.test(code)) {
    setCreateStatus("Use exactly five letters or digits.", "error");
    return;
  }
  setCreateStatus("Creating access code.");
  try {
    const created = await adminJson("/admin/access-codes", {
      method: "POST",
      body: JSON.stringify({ code, enabled: true }),
    });
    state.accessCodes = mergeAccessCodes(state.accessCodes, [created]);
    state.selectedAccessCodeId = created.id;
    document.getElementById("new_access_code_input").value = "";
    setCreateStatus(`Created ${created.code}.`, "success");
    renderAccessCodes();
    renderAgentTypes();
    await loadSelectedInstances();
  } catch (error) {
    setCreateStatus(error.message === "conflict" ? "Access code already exists." : "Create failed.", "error");
  }
}

function generateAccessCode() {
  const cryptoApi = window.crypto || window.msCrypto;
  let code = "";
  if (cryptoApi && cryptoApi.getRandomValues) {
    const values = new Uint32Array(5);
    cryptoApi.getRandomValues(values);
    code = Array.from(values, (value) => GENERATED_CODE_CHARS[value % GENERATED_CODE_CHARS.length]).join("");
  } else {
    for (let index = 0; index < 5; index += 1) {
      code += GENERATED_CODE_CHARS[Math.floor(Math.random() * GENERATED_CODE_CHARS.length)];
    }
  }
  document.getElementById("new_access_code_input").value = code;
  setCreateStatus("Generated code.", "success");
}

async function toggleAccessCode(accessCodeId, enabled) {
  try {
    const updated = await adminJson(`/admin/access-codes/${encodeURIComponent(accessCodeId)}`, {
      method: "PATCH",
      body: JSON.stringify({ enabled }),
    });
    state.accessCodes = mergeAccessCodes(state.accessCodes, [updated]);
    renderAccessCodes();
    setCreateStatus(`${updated.code} ${updated.enabled ? "enabled" : "disabled"}.`, "success");
  } catch (error) {
    setCreateStatus("Enabled state update failed.", "error");
    await loadAdminData();
  }
}

async function saveAgentTypeAssignments() {
  const selected = selectedAccessCode();
  if (!selected) {
    setAssignmentStatus("Select an access code first.", "error");
    return;
  }
  const agentTypeKeys = Array.from(document.querySelectorAll("[data-agent-type-checkbox]:checked"))
    .map((input) => input.value);
  setAssignmentStatus("Saving assignments.");
  try {
    const updated = await adminJson(`/admin/access-codes/${encodeURIComponent(selected.id)}/agent-types`, {
      method: "PUT",
      body: JSON.stringify({ agentTypeKeys }),
    });
    state.accessCodes = mergeAccessCodes(state.accessCodes, [updated]);
    renderAccessCodes();
    renderAgentTypes();
    setAssignmentStatus("Assignments saved.", "success");
  } catch (error) {
    setAssignmentStatus("Assignment update failed.", "error");
  }
}

async function loadSelectedInstances() {
  const selected = selectedAccessCode();
  if (!selected || !state.adminToken) {
    state.selectedAgents = [];
    renderInstances();
    return;
  }
  setInstanceStatus("Loading instances.");
  try {
    const agents = await adminJson(`/admin/access-codes/${encodeURIComponent(selected.id)}/agents`);
    state.selectedAgents = Array.isArray(agents) ? agents : [];
    renderInstances();
    setInstanceStatus(`${state.selectedAgents.length} instance${state.selectedAgents.length === 1 ? "" : "s"}.`);
  } catch (error) {
    state.selectedAgents = [];
    renderInstances();
    setInstanceStatus("Instance load failed.", "error");
  }
}

function togglePresetMenu(event) {
  if (event) {
    event.stopPropagation();
  }
  state.presetMenuOpen = !state.presetMenuOpen;
  renderPresetMenu();
}

function renderPresetMenu() {
  const button = document.getElementById("access_code_preset_menu_button");
  const menu = document.getElementById("access_code_preset_menu");
  button.disabled = !state.adminToken || state.accessCodePresets.length === 0;
  menu.hidden = !state.presetMenuOpen || button.disabled;
  menu.replaceChildren();
  if (state.accessCodePresets.length === 0) {
    menu.appendChild(emptyPanel("No presets."));
    return;
  }
  for (const preset of state.accessCodePresets) {
    const item = document.createElement("button");
    item.className = "preset-menu-item";
    item.type = "button";
    item.dataset.testid = "access-code-preset-menu-item";
    item.innerHTML = `<strong>${escapeHtml(preset.displayName || preset.key)}</strong><div class="metric-label">${presetEntryCount(preset)} code${presetEntryCount(preset) === 1 ? "" : "s"}</div>`;
    item.addEventListener("click", () => {
      state.presetMenuOpen = false;
      openPresetModal(preset.key);
      renderPresetMenu();
    });
    menu.appendChild(item);
  }
}

function openPresetModal(presetKey) {
  const preset = accessCodePresetByKey(presetKey);
  if (!preset) {
    setAdminStatus("Preset not found.", "error");
    return;
  }
  state.activeAccessCodePresetKey = preset.key;
  document.getElementById("access_code_preset_modal_title").innerHTML =
    `<i class="bi bi-lightning-charge me-2"></i>${escapeHtml(preset.displayName || preset.key)}`;
  renderPresetEntries(preset);
  setPresetStatus("");
  const modal = document.getElementById("access_code_preset_modal");
  modal.hidden = false;
  modal.classList.remove("d-none");
}

function closePresetModal() {
  state.activeAccessCodePresetKey = null;
  const modal = document.getElementById("access_code_preset_modal");
  modal.hidden = true;
  modal.classList.add("d-none");
  setPresetStatus("");
}

function renderPresetEntries(preset) {
  const list = document.getElementById("access_code_preset_entries");
  list.replaceChildren();
  const byKey = agentTypeMap();
  for (const entry of preset.entries || []) {
    const details = document.createElement("details");
    details.className = "preset-entry";
    details.open = true;
    details.dataset.testid = "access-code-preset-entry";

    const summary = document.createElement("summary");
    summary.innerHTML = `<strong class="mono">${escapeHtml(entry.code)}</strong><span class="metric-label ms-2">${(entry.agentTypeKeys || []).length} type${(entry.agentTypeKeys || []).length === 1 ? "" : "s"}</span>`;
    details.appendChild(summary);

    const body = document.createElement("div");
    body.className = "preset-entry-body";
    for (const key of entry.agentTypeKeys || []) {
      const agentType = byKey.get(key);
      const item = document.createElement("label");
      item.className = "agent-type-item";
      item.dataset.testid = "access-code-preset-agent-type";

      const input = document.createElement("input");
      input.className = "form-check-input me-2";
      input.type = "checkbox";
      input.value = key;
      input.checked = true;
      input.dataset.presetAgentCheckbox = "true";
      input.dataset.presetAccessCode = entry.code;
      input.dataset.testid = "access-code-preset-agent-checkbox";

      const title = document.createElement("span");
      title.innerHTML = `<strong>${escapeHtml(prometheusFacingText(agentType ? agentType.displayName : key))}</strong>`;

      const description = document.createElement("div");
      description.className = "metric-label ms-4";
      description.textContent = prometheusFacingText(agentType ? agentType.description || "" : key);

      item.appendChild(input);
      item.appendChild(title);
      item.appendChild(description);
      body.appendChild(item);
    }
    details.appendChild(body);
    list.appendChild(details);
  }
}

async function createAccessCodePreset() {
  const preset = accessCodePresetByKey(state.activeAccessCodePresetKey);
  if (!preset) {
    setPresetStatus("Preset not found.", "error");
    return;
  }
  const entries = (preset.entries || []).map((entry) => ({
    code: entry.code,
    agentTypeKeys: Array.from(document.querySelectorAll(`[data-preset-access-code="${cssEscape(entry.code)}"][data-preset-agent-checkbox]:checked`))
      .map((input) => input.value),
  }));
  setPresetStatus("Creating access codes.");
  try {
    const created = await adminJson(`/admin/access-code-presets/${encodeURIComponent(preset.key)}/apply`, {
      method: "POST",
      body: JSON.stringify({ entries }),
    });
    state.accessCodes = mergeAccessCodes(state.accessCodes, Array.isArray(created) ? created : []);
    if (Array.isArray(created) && created.length > 0) {
      state.selectedAccessCodeId = created[0].id;
    }
    closePresetModal();
    renderAccessCodes();
    renderAgentTypes();
    await loadSelectedInstances();
    const createdCount = Array.isArray(created) ? created.length : 0;
    setCreateStatus(`Created ${createdCount} access code${createdCount === 1 ? "" : "s"}.`, "success");
  } catch (error) {
    setPresetStatus(error.message === "conflict" ? "One or more access codes already exist." : "Preset creation failed.", "error");
  }
}

function selectAccessCode(accessCodeId) {
  state.selectedAccessCodeId = accessCodeId;
  state.expandedAgentTypePackages = new Set();
  renderAccessCodes();
  renderAgentTypes();
  loadSelectedInstances();
}

function renderAccessCodes() {
  const list = document.getElementById("access_code_list");
  list.replaceChildren();
  const sorted = [...state.accessCodes].sort((a, b) => (a.code || "").localeCompare(b.code || ""));
  if (sorted.length === 0) {
    list.appendChild(emptyPanel("No access codes."));
    return;
  }
  for (const accessCode of sorted) {
    const row = document.createElement("div");
    row.className = `code-row${accessCode.id === state.selectedAccessCodeId ? " is-selected" : ""}`;
    row.dataset.accessCodeId = accessCode.id;
    row.dataset.testid = "access-code-row";

    const summary = document.createElement("button");
    summary.className = "btn btn-link text-start p-0 text-decoration-none";
    summary.type = "button";
    summary.dataset.testid = "select-access-code";
    summary.addEventListener("click", () => selectAccessCode(accessCode.id));
    summary.innerHTML = `<strong class="mono">${escapeHtml(accessCode.code)}</strong><div class="metric-label">${accessCode.enabled ? "Enabled" : "Disabled"} - ${assignmentCount(accessCode)} type${assignmentCount(accessCode) === 1 ? "" : "s"}</div>`;

    const toggleWrap = document.createElement("div");
    toggleWrap.className = "form-check form-switch m-0";
    const toggle = document.createElement("input");
    toggle.className = "form-check-input";
    toggle.type = "checkbox";
    toggle.checked = !!accessCode.enabled;
    toggle.dataset.testid = "access-code-enabled-toggle";
    toggle.addEventListener("change", () => toggleAccessCode(accessCode.id, toggle.checked));
    toggleWrap.appendChild(toggle);

    row.appendChild(summary);
    row.appendChild(toggleWrap);
    list.appendChild(row);
  }
}

function renderAgentTypes() {
  const selected = selectedAccessCode();
  const label = document.getElementById("selected_code_label");
  label.textContent = selected ? selected.code : "No code";

  const list = document.getElementById("agent_type_list");
  list.replaceChildren();
  const saveButton = document.getElementById("save_agent_type_assignment");
  const filterInput = document.getElementById("agent_type_filter");
  saveButton.disabled = !selected;
  filterInput.disabled = !selected;
  if (!selected) {
    list.appendChild(emptyPanel("Select or create an access code."));
    return;
  }
  const allowed = new Set(selected.allowedAgentTypeKeys || []);
  const filterText = state.agentTypeFilter.trim().toLowerCase();
  const visibleAgentTypes = filterAgentTypes(state.agentTypes, filterText);
  if (state.agentTypes.length === 0) {
    list.appendChild(emptyPanel("No registered agent types."));
    return;
  }
  if (visibleAgentTypes.length === 0) {
    list.appendChild(emptyPanel("No agent types match the filter."));
    return;
  }
  const tree = buildAgentTypeTree(visibleAgentTypes);
  expandAssignedPackages(tree, allowed);
  if (filterText) {
    expandAllPackages(tree);
  }
  renderAgentTypeChildren(list, tree, allowed, filterText);
}

function buildAgentTypeTree(agentTypes) {
  const root = createPackageNode("", []);
  for (const agentType of [...agentTypes].sort((a, b) => agentTypeSortKey(a).localeCompare(agentTypeSortKey(b)))) {
    const path = packagePathOf(agentType);
    let node = root;
    for (const segment of path) {
      if (!node.children.has(segment)) {
        node.children.set(segment, createPackageNode(segment, [...node.path, segment]));
      }
      node = node.children.get(segment);
    }
    node.agentTypes.push(agentType);
  }
  return root;
}

function createPackageNode(name, path) {
  return {
    name,
    path,
    children: new Map(),
    agentTypes: [],
  };
}

function renderAgentTypeChildren(container, node, allowed, filterText) {
  for (const child of sortedPackageChildren(node)) {
    container.appendChild(renderPackageNode(child, allowed, filterText));
  }
  for (const agentType of node.agentTypes) {
    container.appendChild(renderAgentTypeItem(agentType, allowed));
  }
}

function renderPackageNode(node, allowed, filterText) {
  const pathKey = packagePathKey(node.path);
  const expanded = filterText || state.expandedAgentTypePackages.has(pathKey);
  const selectedCount = selectedAgentTypeCount(node, allowed);
  const totalCount = totalAgentTypeCount(node);

  const wrapper = document.createElement("div");
  wrapper.className = "agent-package";
  wrapper.dataset.packagePath = pathKey;
  wrapper.dataset.testid = "admin-agent-package";

  const row = document.createElement("button");
  row.type = "button";
  row.className = "agent-package-row";
  row.dataset.testid = "admin-agent-package-toggle";
  row.setAttribute("aria-expanded", expanded ? "true" : "false");
  row.addEventListener("click", () => {
    if (state.expandedAgentTypePackages.has(pathKey)) {
      state.expandedAgentTypePackages.delete(pathKey);
    } else {
      state.expandedAgentTypePackages.add(pathKey);
    }
    renderAgentTypes();
  });
  row.innerHTML = `
    <i class="bi bi-chevron-${expanded ? "down" : "right"}"></i>
    <span class="text-truncate"><strong>${escapeHtml(packageLabel(node.name))}</strong></span>
    <span class="badge text-bg-light">${selectedCount}/${totalCount}</span>
  `;

  const children = document.createElement("div");
  children.className = "agent-package-children";
  children.hidden = !expanded;
  if (expanded) {
    renderAgentTypeChildren(children, node, allowed, filterText);
  }

  wrapper.appendChild(row);
  wrapper.appendChild(children);
  return wrapper;
}

function renderAgentTypeItem(agentType, allowed) {
  const item = document.createElement("label");
  item.className = "agent-type-item";
  item.dataset.testid = "admin-agent-type-option";

  const input = document.createElement("input");
  input.className = "form-check-input me-2";
  input.type = "checkbox";
  input.value = agentType.key;
  input.checked = allowed.has(agentType.key);
  input.dataset.agentTypeCheckbox = "true";
  input.dataset.testid = "admin-agent-type-checkbox";

  const title = document.createElement("span");
  title.innerHTML = `<strong>${escapeHtml(prometheusFacingText(agentType.displayName || agentType.key))}</strong>`;

  const description = document.createElement("div");
  description.className = "metric-label ms-4";
  description.textContent = prometheusFacingText(agentType.description || "");

  item.appendChild(input);
  item.appendChild(title);
  item.appendChild(description);
  return item;
}

function filterAgentTypes(agentTypes, filterText) {
  if (!filterText) {
    return [...agentTypes];
  }
  return (agentTypes || []).filter((agentType) => {
    const searchable = [
      agentType.key,
      agentType.displayName,
      agentType.description,
      packagePathOf(agentType).join("."),
    ].join(" ").toLowerCase();
    return searchable.includes(filterText);
  });
}

function packagePathOf(agentType) {
  if (Array.isArray(agentType.packagePath)) {
    return agentType.packagePath
      .map((segment) => String(segment || "").trim())
      .filter((segment) => segment.length > 0);
  }
  const keyParts = String(agentType.key || "").split(".").filter((part) => part.length > 0);
  return keyParts.length > 1 ? keyParts.slice(0, -1) : [];
}

function sortedPackageChildren(node) {
  return Array.from(node.children.values())
    .sort((a, b) => packagePathKey(a.path).localeCompare(packagePathKey(b.path)));
}

function totalAgentTypeCount(node) {
  let count = node.agentTypes.length;
  for (const child of node.children.values()) {
    count += totalAgentTypeCount(child);
  }
  return count;
}

function selectedAgentTypeCount(node, allowed) {
  let count = node.agentTypes.filter((agentType) => allowed.has(agentType.key)).length;
  for (const child of node.children.values()) {
    count += selectedAgentTypeCount(child, allowed);
  }
  return count;
}

function expandAssignedPackages(node, allowed) {
  for (const child of node.children.values()) {
    expandAssignedPackages(child, allowed);
  }
  if (node.path.length > 0 && selectedAgentTypeCount(node, allowed) > 0) {
    state.expandedAgentTypePackages.add(packagePathKey(node.path));
  }
}

function expandAllPackages(node) {
  for (const child of node.children.values()) {
    state.expandedAgentTypePackages.add(packagePathKey(child.path));
    expandAllPackages(child);
  }
}

function renderInstances() {
  const list = document.getElementById("instance_list");
  list.replaceChildren();
  const button = document.getElementById("refresh_instances");
  button.disabled = !selectedAccessCode();
  if (!selectedAccessCode()) {
    list.appendChild(emptyPanel("Select an access code."));
    return;
  }
  if (state.selectedAgents.length === 0) {
    list.appendChild(emptyPanel("No instances for this code."));
    return;
  }
  for (const agent of state.selectedAgents) {
    const id = agentIdOf(agent);
    const item = document.createElement("div");
    item.className = "instance-item";
    item.dataset.testid = "admin-instance-item";
    item.innerHTML = `
      <div class="d-flex justify-content-between gap-2">
        <strong>${escapeHtml(prometheusFacingText(agent.name || "Agent"))}</strong>
        <span class="badge text-bg-${agent.active ? "success" : "secondary"}">${agent.active ? "Active" : "Inactive"}</span>
      </div>
      <div class="mono small mt-1">${escapeHtml(id || "-")}</div>
      <div class="metric-label mt-1">${escapeHtml(prometheusFacingText(agent.description || ""))}</div>
    `;
    list.appendChild(item);
  }
}

async function adminJson(url, options = {}) {
  const headers = new Headers(options.headers || {});
  headers.set(ADMIN_TOKEN_HEADER, state.adminToken);
  if (options.body) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  const response = await fetch(url, { ...options, headers });
  if (response.status === 401) {
    throw new Error("unauthorized");
  }
  if (response.status === 409) {
    throw new Error("conflict");
  }
  if (!response.ok) {
    throw new Error(`request failed: ${response.status}`);
  }
  if (response.status === 204) {
    return null;
  }
  return await response.json();
}

function selectedAccessCode() {
  return state.accessCodes.find((accessCode) => accessCode.id === state.selectedAccessCodeId) || null;
}

function mergeAccessCodes(existing, additions) {
  const byId = new Map();
  for (const accessCode of [...(existing || []), ...(additions || [])]) {
    if (accessCode && accessCode.id) {
      byId.set(accessCode.id, accessCode);
    }
  }
  return Array.from(byId.values());
}

function accessCodePresetByKey(key) {
  return state.accessCodePresets.find((preset) => preset.key === key) || null;
}

function presetEntryCount(preset) {
  return Array.isArray(preset.entries) ? preset.entries.length : 0;
}

function agentTypeMap() {
  const result = new Map();
  for (const agentType of state.agentTypes || []) {
    result.set(agentType.key, agentType);
  }
  return result;
}

function assignmentCount(accessCode) {
  return Array.isArray(accessCode.allowedAgentTypeKeys) ? accessCode.allowedAgentTypeKeys.length : 0;
}

function agentIdOf(agent) {
  return agent && (agent.id || agent.ID || agent.iD);
}

function agentTypeSortKey(agentType) {
  return agentType && (agentType.displayName || agentType.key) ? (agentType.displayName || agentType.key) : "";
}

function packagePathKey(path) {
  return (path || []).join(".");
}

function packageLabel(segment) {
  return segment || "root";
}

function emptyPanel(text) {
  const panel = document.createElement("div");
  panel.className = "surface-panel metric-label";
  panel.textContent = text;
  return panel;
}

function showWorkspace(visible) {
  const shell = document.getElementById("admin_shell");
  const tokenPanel = document.getElementById("token_panel");
  const workspace = document.getElementById("admin_workspace");
  shell.hidden = !visible;
  shell.classList.toggle("d-none", !visible);
  tokenPanel.hidden = visible;
  tokenPanel.classList.toggle("d-none", visible);
  workspace.hidden = !visible;
}

function setConnectionState(text, mode) {
  const status = document.getElementById("admin_connection_state");
  status.textContent = text;
  status.className = `status-pill is-${mode || "unknown"}`;
}

function setAdminStatus(message, mode) {
  setStatus("admin_token_status", message, mode);
}

function setCreateStatus(message, mode) {
  setStatus("access_code_create_status", message, mode);
}

function setAssignmentStatus(message, mode) {
  setStatus("assignment_status", message, mode);
}

function setInstanceStatus(message, mode) {
  setStatus("instance_status", message, mode);
}

function setPresetStatus(message, mode) {
  setStatus("access_code_preset_status", message, mode);
}

function setStatus(id, message, mode) {
  const status = document.getElementById(id);
  status.textContent = message || "";
  status.className = `status-line${mode ? ` is-${mode}` : ""}`;
}

function prometheusFacingText(value) {
  if (typeof value !== "string") {
    return value || "";
  }
  const legacyAgentName = String.fromCharCode(103, 105, 103, 105);
  const legacyDomainName = String.fromCharCode(116, 100, 115, 114);
  return value
    .replace(new RegExp(`\\b${legacyAgentName} on Prometheus\\b`, "gi"), "Prometheus")
    .replace(new RegExp(`\\b${legacyAgentName}\\b`, "gi"), "Prometheus")
    .replace(new RegExp(`\\b${legacyDomainName}\\b`, "gi"), "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

function escapeHtml(value) {
  return String(value || "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function cssEscape(value) {
  if (window.CSS && typeof window.CSS.escape === "function") {
    return window.CSS.escape(String(value || ""));
  }
  return String(value || "").replace(/"/g, "\\\"");
}
