# Valerian Designer UX Specification

## Product intent

`/valerian-design/` helps a domain expert create a sound agent without first
learning the persistence model, Java, JSON Schema, or state-machine jargon. It
uses progressive disclosure while preserving full access to the canonical JSON
for expert authors.

The central authoring sentence is:

```text
This agent senses ... and can behave ...
When ... happens in this situation, it decides ... then does ...
and may move to another situation.
```

The UI is minimal, calm, and self-explaining. It does not expose database
entities or present every possible field at once.

## Information architecture

### Catalog screen

The entry screen provides:

- search and category/status filters;
- definition key, display name, active revision, draft status, provenance, and
  language;
- create, import, clone, open, export, archive, and activate actions according
  to lifecycle rules;
- clear distinction between active published revision and current draft;
- validation/publication status without implying that a draft is deployed.

Destructive or lifecycle-changing actions require explicit confirmation with
the exact key/revision. A published revision referenced by instances offers
archive, not delete.

### Editor screen

The editor has:

- a compact header with definition identity, revision/status, dirty indicator,
  Save draft, Preview, and lifecycle actions;
- the horizontal/vertical process stepper from `STEPPER.md`;
- one active step panel;
- a persistent validation summary reachable by keyboard;
- optional advanced JSON view;
- an unsaved-change warning when navigating away.

Stepper navigation changes panels but does not publish and does not silently
save. Saving a structurally parseable draft is explicit. Publication requires
complete validation and successful compilation.

## Six-step authoring flow

Keep titles short and use these captions unless user testing supports a clearer
equivalent.

| Step | Title | Caption | User outcome |
| ---: | --- | --- | --- |
| 1 | Purpose | Define its role and goal. | Identity, language, purpose, context, persona, boundaries |
| 2 | Sensing | Choose what it perceives. | Supported observations and interpretation guidance |
| 3 | Behaviour | Choose how it responds. | Modalities, response strategy, style, and fallback |
| 4 | Reactions | Connect inputs to outputs. | Guided sensing-to-behaviour mappings |
| 5 | State flow | Add conversational situations. | State graph, containment, entry, and transitions |
| 6 | Review | Validate, test, and publish. | Summary, JSON/prompt preview, disposable runtime preview |

Users may navigate directly to any step. The stepper is navigation, not a
percentage-complete meter. Follow every responsive and accessibility rule in
`STEPPER.md`, adapting implementation code to the selected frontend framework
without changing the interaction contract.

## Step 1: Purpose

Lead with ordinary questions:

- What should this agent be called?
- What is it intended to accomplish?
- Who or what should it represent?
- In which language should it interact?
- In what setting will it be used?
- What important boundaries must it respect?

Generate a valid key suggestion from category and name, but require the author
to confirm it before first save. Explain that a published key is stable. Keep
schema version, revision number, hash, provenance, and repository IDs out of the
ordinary form; show them in a compact technical-details disclosure.

Purpose prompt elements include persona, objective, context, roles, language,
tone, grounding, and boundaries. They remain separate ordered sections in JSON.

## Step 2: Sensing

Present observations as plain-language selectable cards with icon, concise
example, and optional details. Examples:

- **What the user says** - text or finalized speech transcription.
- **Facial emotion** - an observed facial-expression category.
- **Social situation** - presence, grouping, or richer social context.
- **Hand sign** - a recognized rock, paper, or scissors sign.
- **Weather** - current conditions or forecast.

Selecting a family reveals its concrete observation types. Do not select
capabilities merely because they are common. Show where each selected signal is
used by a reaction or prompt. An unused selected capability is a helpful warning
with a link to Reactions, not an automatic deletion.

Sensing-specific prompt elements guide how observations should be interpreted,
when the agent may react proactively, and what uncertainty means. The event
payload itself is supplied by the runtime; prompt examples must not encourage
invented sensor data.

## Step 3: Behaviour

Present modalities as a second capability palette:

- speech;
- gesture;
- facial expression;
- gaze;
- body/nonverbal motion;
- hand-sign motion;
- display content.

For each selected modality, show only compatible strategies from the component
catalog. A speech strategy may be prompt-generated, deterministic exact text,
or a future knowledge-constrained component. Deterministic components expose
typed fields, not prompt boxes.

Prompt guidance covers response objective, tone, length, question frequency,
coordination across modalities, suppression, and fallback. A live read-only
summary explains what the agent is currently able to emit.

## Step 4: Reactions

Reactions are a guided view over policies and transitions, not a second runtime
model. Each card reads like:

```text
When [observation]
in [current situation]
if [optional conditions]
then [respond and/or update memory]
and [stay or move].
```

Initially every reaction belongs to the generated `main` atomic state. The
author can:

- select only previously declared sensing inputs;
- choose relevant event/history selection;
- add ordered decisions;
- add ordered storage actions;
- select compatible behaviour strategies/modalities;
- stay in the current state or select a target;
- reorder reactions when first-match priority matters.

The simple card hides component envelopes and selector syntax. An Advanced
disclosure exposes component-specific configuration with schema-derived help.
When an author chooses an undeclared observation or modality through an
advanced path, offer to add it to the corresponding capability step and explain
the effect.

## Step 5: State flow

Do not force graph design on a one-state agent. Start with one explicit JSON
state that the UI describes as the default situation. The state-flow step first
asks whether behavior differs across phases or contexts.

When multiple situations are needed, provide a visual graph workspace:

- atomic, composite, and final node types;
- stable ID plus editable display name;
- visible initial state and composite initial child;
- transition edges with source priority/order;
- self-transitions and cycles;
- distinct visual containment for composite states;
- an inspector for policy, selector, entry mode, oblivious behavior, decisions,
  and actions;
- an accessible non-canvas list/table representation for keyboard and screen
  reader use.

Moving or assigning a reaction changes its source state in the same canonical
document. There is no duplicate reaction model to synchronize.

Use plain-language labels in the primary UI:

- state -> situation;
- transition -> move/change when;
- decision -> condition;
- action -> memory update or operation;
- final -> finished.

Technical terms may appear in help and the advanced view.

## Step 6: Review

Review provides four coordinated views:

1. **Plain-language summary** of purpose, sensing, behaviour, reactions, and
   state flow.
2. **Validation** grouped by step, with errors before warnings and direct links
   to fields/nodes.
3. **Preview** for disposable event-driven execution with active state, storage,
   events, and emitted behaviour.
4. **Technical details** containing canonical JSON and read-only composed prompt
   previews.

Save draft remains available with incomplete semantic content. Publish is
enabled only when the backend reports no errors and successful compilation.
Activate is offered only for a published revision and explicitly states that it
affects newly created instances, not existing ones.

## Prompt guidance

### Field pattern

Every prompt element has:

- a question-oriented label;
- one-sentence explanation of its runtime purpose;
- a multiline content field;
- one or more collapsed examples appropriate to the component and language;
- an explicit **Use as starting point** action;
- character/size feedback where limits apply;
- a preview of its position in the composed prompt.

Do not use placeholder text as the only example: it disappears while typing and
is frequently mistaken for saved content.

### Example ownership

Examples come from component schema metadata and a versioned designer example
catalog. They are not definition defaults unless explicitly identified as safe
defaults. Clicking an example copies it into the field and marks the draft
dirty; merely viewing it changes nothing.

Examples should demonstrate useful structure without prescribing factual
claims. Domain examples must be visibly illustrative and must not imply medical
or organizational approval.

### Composition

The UI edits typed sections. The backend is authoritative for deterministic
composition. Show the composed result as read-only so an expert can verify
ordering and boundaries without creating a second editable source.

## Advanced JSON view

The JSON view is an alternate editor for the same in-memory document:

- opening it shows the current complete specification;
- applying changes parses and structurally validates before replacing form
  state;
- parse errors preserve the last valid form state and identify line/column;
- semantic diagnostics use backend JSON Pointers;
- switching views never silently discards edits;
- JSON examples or formatted output never contain secrets;
- repository lifecycle metadata is shown separately and cannot be forged in
  specification JSON.

Pretty-printing is deterministic. Import/export round trips through the same
backend canonicalizer.

## Validation behavior

Follow the stepper pattern: direct navigation is never blocked. Validation is
progressive:

- lightweight local feedback for required values and syntax;
- authoritative backend diagnostics on Save, Validate, Preview, and Publish;
- a persistent alert summary grouped by step;
- focus and scroll to the first selected diagnostic;
- visible node/edge markers for graph errors;
- warnings remain distinguishable from blocking errors;
- stale diagnostics clear or refresh when their field changes.

Diagnostics must remain understandable without exposing exception messages or
stack traces. Keep stable diagnostic codes available in a technical disclosure.

## Draft and concurrency behavior

- The editor records the draft's optimistic version.
- Save replaces the complete draft only when that version still matches.
- A conflict does not overwrite either version. Offer reload and export/copy of
  local JSON; do not attempt opaque automatic graph merges.
- Maintain a visible dirty indicator.
- Warn before browser navigation with unsaved edits.
- Publishing or activating requires a deliberate button and confirmation.

## Preview interaction

Preview offers event templates for selected sensing capabilities plus an
advanced JSON event editor. It shows an ordered transcript/log with:

- submitted event;
- resulting active state path;
- storage changes;
- emitted behaviour plan;
- transition/decision/action trace at a safe diagnostic level;
- reset and close controls.

Preview sessions are disposable and visually labeled **Preview**. They cannot
be mistaken for access-code agents. Automated tests use deterministic runtime
components and fake model gateways.

## Visual and responsive language

The designer should feel related to Valerian without copying cockpit density:

- restrained neutral surfaces and the teal accent from `STEPPER.md`;
- generous whitespace and short labels;
- one clear primary action per context;
- cards for capability selection and reactions;
- the graph receives the largest available area;
- desktop, narrow desktop, tablet, and mobile remain usable;
- light and dark themes follow the existing Valerian theme preference when
  practical.

At mobile width the stepper stacks as specified. The state graph may use a
focused full-screen mode, but every operation remains available in the
accessible list representation.

## Accessibility

In addition to `STEPPER.md`:

- all form fields have persistent labels and described help;
- capability cards use native checkbox/radio semantics;
- graph actions have keyboard-accessible equivalents;
- focus order follows the visible workflow;
- dialogs trap and restore focus correctly;
- errors use text/icon/state in addition to color;
- live validation does not create disruptive announcement loops;
- motion respects reduced-motion preference;
- touch targets and contrast meet WCAG AA expectations.

## Frontend implementation boundary

Use TypeScript and Vite. A maintained graph library may be selected after
checking its current support, accessibility limitations, bundle impact, and
license. Framework/library selection is an engineering choice within this
contract and does not require product approval unless it changes the specified
experience or deployment model.

Keep source separate from generated Spring static resources. Provide one
documented deterministic build command, integrate it into CI/deployment, and do
not hand-edit generated bundles. The Spring application serves the result at
the trailing-slash path `/valerian-design/`.

## Out of scope

- Regulation controls
- Collaborative simultaneous editing
- User/role administration beyond the existing admin token
- Arbitrary component installation or executable scripting
- Prompt optimization or automatic agent generation
- Production rollout/deployment orchestration
- Migration UI for old runtime instances
- `agents`-branch definitions
