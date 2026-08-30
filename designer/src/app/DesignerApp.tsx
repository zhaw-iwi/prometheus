import { type FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import {
  ADMIN_TOKEN_STORAGE_KEY,
  DesignerApiError,
  fetchDefinitionCatalog,
  type DefinitionSummary,
  type RequestFunction,
} from "../api/designerApi";
import { designerRouteHash, parseDesignerRoute, type DesignerRoute } from "../routing/designerRoute";
import { DesignerStepper } from "../stepper/DesignerStepper";

interface DesignerAppProps {
  request?: RequestFunction;
}

type CatalogState =
  | { kind: "locked" }
  | { kind: "loading" }
  | { kind: "ready"; definitions: DefinitionSummary[] }
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
  const [catalog, setCatalog] = useState<CatalogState>(() => adminToken
    ? { kind: "loading" }
    : { kind: "locked" });

  const navigate = useCallback((next: DesignerRoute) => {
    const hash = designerRouteHash(next);
    if (window.location.hash === hash) {
      setRoute(next);
    } else {
      window.location.hash = hash;
    }
  }, []);

  useEffect(() => {
    const onHashChange = () => setRoute(parseDesignerRoute(window.location.hash));
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const loadCatalog = useCallback(async (token: string) => {
    setCatalog({ kind: "loading" });
    try {
      const definitions = await fetchDefinitionCatalog(token, request);
      setCatalog({ kind: "ready", definitions });
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
    removeAdminToken();
    setAdminToken("");
    setCatalog({ kind: "locked" });
    navigate({ kind: "catalog" });
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
          <Catalog definitions={catalog.definitions} onNavigate={navigate} />
        )}
        {catalog.kind === "ready" && route.kind !== "catalog" && (
          <Editor route={route} definitions={catalog.definitions} onNavigate={navigate} />
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

function PageHeading({ onCreate }: { onCreate?: () => void }) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">Declarative agents</span>
        <h1>Definition catalog</h1>
        <p>Open an existing revision or begin a new guided agent design.</p>
      </div>
      {onCreate && <button className="button primary" type="button" onClick={onCreate}
        data-testid="create-definition">Create agent</button>}
    </div>
  );
}

function Catalog({ definitions, onNavigate }: {
  definitions: DefinitionSummary[];
  onNavigate: (route: DesignerRoute) => void;
}) {
  return (
    <section className="catalog-page" data-testid="definition-catalog">
      <PageHeading onCreate={() => onNavigate({ kind: "new" })} />
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
            const revision = definition.activeRevision ?? latest?.revision ?? 1;
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

function Editor({ route, definitions, onNavigate }: {
  route: Exclude<DesignerRoute, { kind: "catalog" }>;
  definitions: DefinitionSummary[];
  onNavigate: (route: DesignerRoute) => void;
}) {
  const definition = useMemo(() => route.kind === "editor"
    ? definitions.find((candidate) => candidate.key === route.key)
    : undefined, [definitions, route]);
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
        <span className="draft-pill">Draft workspace</span>
      </div>
      {route.kind === "editor" && !definition ? (
        <div className="center-state compact-state" data-testid="definition-missing">
          <h2>Definition not found</h2>
          <p>Return to the catalog and choose an available revision.</p>
        </div>
      ) : <DesignerStepper />}
    </section>
  );
}
