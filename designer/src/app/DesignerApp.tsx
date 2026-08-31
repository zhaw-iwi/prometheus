import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  ADMIN_TOKEN_STORAGE_KEY,
  type ComponentDefinition,
  DesignerApiError,
  fetchDesignerWorkspace,
  importDefinitionDraft,
  type DefinitionSummary,
  type RequestFunction,
} from "../api/designerApi";
import { DesignerDefinitionEditor } from "../v2/DesignerDefinitionEditor";
import type { AgentDefinitionV1 } from "../model/agentDefinition";
import { designerRouteHash, parseDesignerRoute, type DesignerRoute } from "../routing/designerRoute";

interface DesignerAppProps {
  request?: RequestFunction;
}

type CatalogState =
  | { kind: "locked" }
  | { kind: "loading" }
  | { kind: "ready"; definitions: DefinitionSummary[]; components: ComponentDefinition[] }
  | { kind: "error"; message: string };

function storedAdminToken(): string {
  try {
    return sessionStorage.getItem(ADMIN_TOKEN_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

function storeAdminToken(token: string): void {
  try {
    sessionStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token);
  } catch {
    // The current tab can still use the token when storage is unavailable.
  }
}

function removeAdminToken(): void {
  try {
    sessionStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY);
  } catch {
    // Nothing else needs cleanup when storage is unavailable.
  }
}

export function DesignerApp({ request = fetch }: DesignerAppProps) {
  const [adminToken, setAdminToken] = useState(storedAdminToken);
  const [tokenDraft, setTokenDraft] = useState("");
  const [route, setRoute] = useState<DesignerRoute>(() => parseDesignerRoute(window.location.hash));
  const [editorDirty, setEditorDirty] = useState(false);
  const [catalog, setCatalog] = useState<CatalogState>(() => adminToken
    ? { kind: "loading" }
    : { kind: "locked" });

  const rawNavigate = useCallback((next: DesignerRoute) => {
    const hash = designerRouteHash(next);
    if (window.location.hash === hash) {
      setRoute(next);
    } else {
      window.location.hash = hash;
    }
  }, []);

  const navigate = useCallback((next: DesignerRoute) => {
    if (editorDirty && !window.confirm("Discard unsaved designer changes?")) return;
    setEditorDirty(false);
    rawNavigate(next);
  }, [editorDirty, rawNavigate]);

  useEffect(() => {
    const onHashChange = () => setRoute(parseDesignerRoute(window.location.hash));
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const loadCatalog = useCallback(async (token: string, background = false) => {
    if (!background) setCatalog({ kind: "loading" });
    try {
      const workspace = await fetchDesignerWorkspace(token, request);
      setCatalog({ kind: "ready", ...workspace });
    } catch (error) {
      const message = error instanceof DesignerApiError
        ? error.message
        : "The designer catalog could not be loaded. Check the application and try again.";
      setCatalog({ kind: "error", message });
    }
  }, [request]);

  useEffect(() => {
    if (adminToken) {
      void loadCatalog(adminToken);
    }
  }, [adminToken, loadCatalog]);

  const enterDesigner = (event: FormEvent) => {
    event.preventDefault();
    const token = tokenDraft.trim();
    if (!token) {
      return;
    }
    storeAdminToken(token);
    setAdminToken(token);
    setTokenDraft("");
  };

  const forgetToken = () => {
    if (editorDirty && !window.confirm("Discard unsaved designer changes?")) return;
    removeAdminToken();
    setAdminToken("");
    setCatalog({ kind: "locked" });
    setEditorDirty(false);
    rawNavigate({ kind: "catalog" });
  };

  if (!adminToken) {
    return (
      <main className="access-page" data-testid="designer-token-panel">
        <div className="brand-lockup">
          <span className="brand-mark" aria-hidden="true">V</span>
          <div><span>Valerian</span><strong>Designer</strong></div>
        </div>
        <section className="access-card">
          <span className="eyebrow">Administrative authoring</span>
          <h1>Design agents with intention.</h1>
          <p>Enter the existing PROMETHEUS admin token to open the definition catalog and guided editor.</p>
          <form onSubmit={enterDesigner}>
            <label htmlFor="designer-admin-token">Admin token</label>
            <div className="token-row">
              <input id="designer-admin-token" type="password" autoComplete="current-password"
                value={tokenDraft} onChange={(event) => setTokenDraft(event.target.value)}
                data-testid="designer-token-input" />
              <button className="button primary" type="submit" disabled={!tokenDraft.trim()}
                data-testid="submit-designer-token">Enter designer</button>
            </div>
          </form>
          <p className="access-note">The token stays in session storage for this browser tab.</p>
        </section>
      </main>
    );
  }

  return (
    <div className="designer-shell" data-testid="designer-shell">
      <header className="topbar">
        <button className="brand-lockup compact" type="button" onClick={() => navigate({ kind: "catalog" })}
          aria-label="Open definition catalog">
          <span className="brand-mark" aria-hidden="true">V</span>
          <div><span>Valerian</span><strong>Designer</strong></div>
        </button>
        <div className="topbar-actions">
          <span className="connection-pill"><span aria-hidden="true"></span>Admin connected</span>
          <button className="button quiet" type="button" onClick={forgetToken}
            data-testid="forget-designer-token">Forget token</button>
        </div>
      </header>
      <main className="workspace">
        {catalog.kind === "loading" && <CatalogLoading />}
        {catalog.kind === "error" && (
          <CatalogError message={catalog.message} onRetry={() => void loadCatalog(adminToken)} />
        )}
        {catalog.kind === "ready" && route.kind === "catalog" && (
          <Catalog definitions={catalog.definitions} adminToken={adminToken} request={request}
            onNavigate={navigate} onImported={(key, revision) => {
              rawNavigate({ kind: "editor", key, revision });
              void loadCatalog(adminToken, true);
            }} />
        )}
        {catalog.kind === "ready" && route.kind !== "catalog" && (
          <Editor route={route} definitions={catalog.definitions} components={catalog.components}
            adminToken={adminToken} request={request} onNavigate={navigate}
            onDirtyChange={setEditorDirty}
            onSaved={(key, revision) => {
              setEditorDirty(false);
              rawNavigate({ kind: "editor", key, revision });
              void loadCatalog(adminToken, true);
            }}
            onWorkspaceChanged={(target) => {
              if (target) {
                setEditorDirty(false);
                rawNavigate({ kind: "editor", ...target });
              }
              void loadCatalog(adminToken, true);
            }} />
        )}
      </main>
    </div>
  );
}

function CatalogLoading() {
  return (
    <section className="catalog-page" aria-busy="true" data-testid="catalog-loading">
      <PageHeading />
      <div className="catalog-grid">
        {[0, 1, 2].map((item) => <div className="definition-card skeleton" key={item}></div>)}
      </div>
      <span className="sr-only">Loading definition catalog</span>
    </section>
  );
}

function CatalogError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <section className="center-state" role="alert" data-testid="catalog-error">
      <span className="state-icon" aria-hidden="true">!</span>
      <h1>Catalog unavailable</h1>
      <p>{message}</p>
      <button className="button primary" type="button" onClick={onRetry} data-testid="retry-catalog">
        Try again
      </button>
    </section>
  );
}

function PageHeading({ onCreate, onImport }: { onCreate?: () => void; onImport?: () => void }) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">Declarative agents</span>
        <h1>Definition catalog</h1>
        <p>Open an existing revision or begin a new guided agent design.</p>
      </div>
      {(onCreate || onImport) && <div className="button-row">
        {onImport && <button className="button secondary" type="button" onClick={onImport}
          data-testid="show-import-definition">Import JSON</button>}
        {onCreate && <button className="button primary" type="button" onClick={onCreate}
          data-testid="create-definition">Create agent</button>}
      </div>}
    </div>
  );
}

function Catalog({ definitions, adminToken, request, onNavigate, onImported }: {
  definitions: DefinitionSummary[];
  adminToken: string;
  request: RequestFunction;
  onNavigate: (route: DesignerRoute) => void;
  onImported: (key: string, revision: number) => void;
}) {
  const [showImport, setShowImport] = useState(false);
  const [importJson, setImportJson] = useState("");
  const [importMessage, setImportMessage] = useState("");
  const [importing, setImporting] = useState(false);

  const importDefinition = async () => {
    let parsed: AgentDefinitionV1;
    try {
      parsed = JSON.parse(importJson) as AgentDefinitionV1;
    } catch (error) {
      setImportMessage(`Import JSON could not be parsed: ${error instanceof Error ? error.message : "invalid JSON"}`);
      return;
    }
    setImporting(true);
    setImportMessage("");
    try {
      const imported = await importDefinitionDraft(parsed, adminToken, request);
      setImportMessage(`Imported ${imported.key} revision ${imported.revision} as an editable draft.`);
      onImported(imported.key, imported.revision);
    } catch (error) {
      setImportMessage(error instanceof DesignerApiError && error.status === 409
        ? "That key and revision already exist. Change the imported key or revision and try again."
        : error instanceof Error ? error.message : "The definition could not be imported.");
    } finally {
      setImporting(false);
    }
  };

  return (
    <section className="catalog-page" data-testid="definition-catalog">
      <PageHeading onCreate={() => onNavigate({ kind: "new" })} onImport={() => setShowImport((shown) => !shown)} />
      {showImport && <section className="import-panel" aria-labelledby="import-title" data-testid="import-panel">
        <div className="section-heading"><div><span className="eyebrow">Canonical JSON</span><h2 id="import-title">Import a definition draft</h2></div></div>
        <p>Import uses the backend schema and canonicalizer. Existing key/revision identities are never overwritten.</p>
        <label className="field-stack">Definition JSON<textarea value={importJson}
          onChange={(change) => setImportJson(change.target.value)} data-testid="import-definition-json" /></label>
        <div className="button-row"><button className="button primary" type="button" disabled={importing || !importJson.trim()}
          onClick={() => void importDefinition()} data-testid="import-definition">{importing ? "Importing…" : "Import draft"}</button></div>
        {importMessage && <p className="inline-message" role="status" data-testid="import-message">{importMessage}</p>}
      </section>}
      {definitions.length === 0 ? (
        <div className="center-state compact-state" data-testid="catalog-empty">
          <span className="state-icon" aria-hidden="true">+</span>
          <h2>No definitions yet</h2>
          <p>Create the first guided agent draft.</p>
          <button className="button primary" type="button" onClick={() => onNavigate({ kind: "new" })}>
            Create agent
          </button>
        </div>
      ) : (
        <div className="catalog-grid" data-testid="catalog-populated">
          {definitions.map((definition) => {
            const latest = definition.revisions.at(-1);
            const revision = latest?.status === "DRAFT"
              ? latest.revision
              : definition.activeRevision ?? latest?.revision ?? 1;
            return (
              <article className="definition-card" key={definition.key}>
                <div className="card-meta">
                  <span>{definition.categoryPath.join(" / ") || "Uncategorized"}</span>
                  <span className="status-badge">{latest?.status ?? "DRAFT"}</span>
                </div>
                <h2>{definition.displayName || definition.key}</h2>
                <p>{definition.description || "No description has been added."}</p>
                <code>{definition.key}</code>
                <div className="card-footer">
                  <span>Revision {revision}</span>
                  <button className="button secondary" type="button"
                    data-testid={`open-definition-${definition.key}`}
                    onClick={() => onNavigate({ kind: "editor", key: definition.key, revision })}>
                    Open
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function Editor({ route, definitions, components, adminToken, request, onNavigate, onDirtyChange, onSaved,
  onWorkspaceChanged }: {
  route: Exclude<DesignerRoute, { kind: "catalog" }>;
  definitions: DefinitionSummary[];
  components: ComponentDefinition[];
  adminToken: string;
  request: RequestFunction;
  onNavigate: (route: DesignerRoute) => void;
  onDirtyChange: (dirty: boolean) => void;
  onSaved: (key: string, revision: number) => void;
  onWorkspaceChanged: (target?: { key: string; revision: number }) => void;
}) {
  const definition = useMemo(() => route.kind === "editor"
    ? definitions.find((candidate) => candidate.key === route.key)
    : undefined, [definitions, route]);
  const selectedRevision = route.kind === "editor"
    ? definition?.revisions.find((candidate) => candidate.revision === route.revision)
    : undefined;
  const title = route.kind === "new" ? "New agent draft" : definition?.displayName ?? route.key;
  const subtitle = route.kind === "new"
    ? "Start with a focused purpose. The designer will reveal complexity only when it becomes useful."
    : `${route.key} · Revision ${route.revision}`;

  return (
    <section className="editor-page" data-testid="designer-editor">
      <button className="back-link" type="button" onClick={() => onNavigate({ kind: "catalog" })}
        data-testid="back-to-catalog">← Definition catalog</button>
      <div className="editor-heading">
        <div>
          <span className="eyebrow">Guided agent design</span>
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <span className="draft-pill">{route.kind === "new" || selectedRevision?.status === "DRAFT"
          ? "Draft workspace" : selectedRevision?.status === "PUBLISHED" ? "Published revision"
            : selectedRevision?.status === "ARCHIVED" ? "Archived revision" : "Revision workspace"}</span>
      </div>
      <DesignerDefinitionEditor key={route.kind === "new" ? "new" : `${route.key}:${route.revision}`}
        route={route} components={components} definitionSummary={definition} adminToken={adminToken} request={request}
        onDirtyChange={onDirtyChange} onSaved={onSaved} onWorkspaceChanged={onWorkspaceChanged} />
    </section>
  );
}
