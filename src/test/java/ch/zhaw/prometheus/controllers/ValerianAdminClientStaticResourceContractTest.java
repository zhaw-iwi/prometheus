package ch.zhaw.prometheus.controllers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ValerianAdminClientStaticResourceContractTest {
    private static final Path INDEX = Path.of("src/main/resources/public/valerian-admin/index.html");
    private static final Path SCRIPT = Path.of("src/main/resources/public/valerian-admin/script.js");

    @Test
    void adminClientExposesRootManagementControls() throws IOException {
        String index = Files.readString(INDEX);

        assertTrue(index.contains("<title>Prometheus Admin Cockpit</title>"));
        assertTrue(index.contains("Prometheus Admin Cockpit"));
        assertTrue(index.contains("Valerian access management"));
        assertTrue(index.contains("class=\"admin-access-screen\""));
        assertTrue(index.contains("data-testid=\"admin-token-panel\""));
        assertTrue(index.contains("data-testid=\"admin-token-input\""));
        assertTrue(index.contains("data-testid=\"submit-admin-token\""));
        assertTrue(index.contains("data-testid=\"admin-shell\""));
        assertTrue(index.contains("data-testid=\"admin-token-theme-toggle\""));
        assertTrue(index.contains("data-testid=\"admin-shell-theme-toggle\""));
        assertTrue(index.contains("data-theme-toggle"));
        assertTrue(index.contains("aria-label=\"Switch to dark mode\""));
        assertTrue(index.contains("bi bi-moon-stars"));
        assertTrue(index.contains("[data-theme=\"dark\"]"));
        assertTrue(index.contains("document.documentElement.dataset.bsTheme = theme;"));
        assertTrue(index.contains("data-testid=\"access-code-preset-menu-button\""));
        assertTrue(index.contains("data-testid=\"access-code-preset-menu\""));
        assertTrue(index.contains("data-testid=\"access-code-preset-modal\""));
        assertTrue(index.contains("data-testid=\"access-code-preset-entries\""));
        assertTrue(index.contains("data-testid=\"create-access-code-preset\""));
        assertTrue(index.contains("data-testid=\"forget-admin-token\""));
        assertTrue(index.contains("data-testid=\"admin-workspace\""));
        assertTrue(index.contains("data-testid=\"new-access-code-input\""));
        assertTrue(index.contains("data-testid=\"generate-access-code\""));
        assertTrue(index.contains("data-testid=\"create-access-code\""));
        assertTrue(index.contains("data-testid=\"access-code-list\""));
        assertTrue(index.contains("data-testid=\"admin-agent-type-list\""));
        assertTrue(index.contains("data-testid=\"admin-agent-type-filter\""));
        assertTrue(index.contains("data-testid=\"save-agent-type-assignment\""));
        assertTrue(index.contains("data-testid=\"refresh-instances\""));
        assertTrue(index.contains("data-testid=\"admin-instance-list\""));
        assertTrue(index.contains("id=\"save_agent_type_assignment\" class=\"btn btn-primary w-100\""));
        assertTrue(index.indexOf("data-testid=\"save-agent-type-assignment\"") <
                index.indexOf("data-testid=\"admin-agent-type-list\""));
        assertTrue(index.contains("Agent Type Assignment"));
        assertTrue(index.contains("Instances"));
        assertTrue(index.contains("<script src=\"script.js\"></script>"));

        assertFalse(index.toLowerCase().contains("gigi"));
        assertFalse(index.toLowerCase().contains("tdsr"));
    }

    @Test
    void adminClientUsesAdminApiAndSessionStorage() throws IOException {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("ADMIN_TOKEN_STORAGE_KEY = \"prometheus.valerianAdmin.adminToken\""));
        assertTrue(script.contains("ADMIN_TOKEN_HEADER = \"X-Prometheus-Admin-Token\""));
        assertTrue(script.contains("THEME_STORAGE_KEY = \"prometheus.valerian.theme\""));
        assertTrue(script.contains("function applyStoredTheme()"));
        assertTrue(script.contains("function toggleTheme()"));
        assertTrue(script.contains("function setTheme(theme, options = {})"));
        assertTrue(script.contains("document.documentElement.dataset.bsTheme = nextTheme;"));
        assertTrue(script.contains("localStorage.setItem(THEME_STORAGE_KEY, nextTheme);"));
        assertTrue(script.contains("button.setAttribute(\"aria-pressed\", dark ? \"true\" : \"false\");"));
        assertTrue(script.contains("iconElement.className = `bi ${icon}`;"));
        assertTrue(script.contains("sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY)"));
        assertTrue(script.contains("sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token)"));
        assertTrue(script.contains("sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY)"));
        assertTrue(script.contains("GENERATED_CODE_CHARS = \"ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789\""));
        assertTrue(script.contains("adminJson(\"/admin/agent-types\")"));
        assertTrue(script.contains("adminJson(\"/admin/access-codes\")"));
        assertTrue(script.contains("adminJson(\"/admin/access-code-presets\")"));
        assertTrue(script.contains("adminJson(\"/admin/access-codes\","));
        assertTrue(script.contains("`/admin/access-code-presets/${encodeURIComponent(preset.key)}/apply`"));
        assertTrue(script.contains("body: JSON.stringify({ code, enabled: true })"));
        assertTrue(script.contains("body: JSON.stringify({ entries })"));
        assertTrue(script.contains("body: JSON.stringify({ enabled })"));
        assertTrue(script.contains("document.querySelectorAll(\"[data-agent-type-checkbox]:checked\")"));
        assertTrue(script.contains("data-preset-agent-checkbox"));
        assertTrue(script.contains(".map((input) => input.value)"));
        assertTrue(script.contains("body: JSON.stringify({ agentTypeKeys })"));
        assertTrue(script.contains("`/admin/access-codes/${encodeURIComponent(selected.id)}/agent-types`"));
        assertTrue(script.contains("`/admin/access-codes/${encodeURIComponent(selected.id)}/agents`"));
        assertTrue(script.contains("buildAgentTypeTree"));
        assertTrue(script.contains("packagePathOf"));
        assertTrue(script.contains("renderPackageNode"));
        assertTrue(script.contains("admin-agent-package-toggle"));
        assertTrue(script.contains("collapsedAgentTypePackages"));
        assertTrue(script.contains("expandAssignedPackages(tree, allowed, state.collapsedAgentTypePackages)"));
        assertTrue(script.contains("state.collapsedAgentTypePackages.add(pathKey)"));
        assertTrue(script.contains("state.collapsedAgentTypePackages.delete(pathKey)"));
        assertTrue(script.contains("headers.set(ADMIN_TOKEN_HEADER, state.adminToken);"));
        assertTrue(script.contains("data-agent-type-checkbox"));
        assertTrue(script.contains("function prometheusFacingText"));
        assertTrue(script.contains("const shell = document.getElementById(\"admin_shell\")"));
        assertTrue(script.contains("const tokenPanel = document.getElementById(\"token_panel\")"));
        assertTrue(script.contains("tokenPanel.hidden = visible;"));
        assertTrue(script.contains("function openPresetModal"));
        assertTrue(script.contains("function createAccessCodePreset"));

        assertFalse(script.toLowerCase().contains("gigi"));
        assertFalse(script.toLowerCase().contains("tdsr"));
    }
}
