# Valerian embodiment implementation evidence

Scope and acceptance: [PLAN_EMBODIMENT.md](PLAN_EMBODIMENT.md).

## EV-01 — complete, 2026-11-15

Created the roadmap and pure browser plan adapter/timeline. Preserves canonical
input, maps supported expressions/gaze/gestures/signs, bounds style values,
separates user signs and display content, filters declared modalities and uses
neutral fallbacks for unsupported pose values. Fresh visible events alone admit
transient animation; history, switches, native speech and reset are explicit.

Verification: `node --test tests/js/embodiment/*.test.mjs`: seven tests passed.
`git diff --check`: passed. No UI or provider behaviour changed in this milestone.
Robot asset, integration, browser/DB/provider acceptance remain EV-02/EV-03.
