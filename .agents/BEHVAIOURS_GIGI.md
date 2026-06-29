# GIGI Physical Behaviours

Use this note when creating PROMETHEUS agents for the Valerian cockpit and the
GIGI robot. PROMETHEUS emits `resp.behaviour_plan` events. The event `payload`
must be a JSON string containing a `BehaviourPlan`.

## BehaviourPlan Shape

```json
{
  "speech": "Text to say/render in cockpit.",
  "nonVerbal": {
    "gesture": "EXPLAIN"
  },
  "motion": {
    "handSign": "rock"
  },
  "display": null
}
```

Rules:
- Emit semantic labels in `nonVerbal.gesture`, not robot-server command IDs.
- Unknown gesture labels are not dispatched by Valerian.
- Do not emit locomotion fields such as `motion.move` or `motion.turn`.
- Use `motion.handSign` only for hand signs such as Schere-Stein-Papier.
- Keep `payload` as a valid JSON string for `resp.behaviour_plan`.

## Safe Gesture Labels

These labels are safe for `nonVerbal.gesture`:

| Label | Use when | Valerian maps to |
| --- | --- | --- |
| `OPEN_QUESTION` | inviting an answer, asking a question, opening the floor | `open_question_gesture` |
| `EXPLAIN` | explaining, instructing, describing context, presenting information | `explanatory_sweep_gesture` |
| `UNCERTAIN` | uncertain, hedging, saying the answer is unknown | `uncertainty_shrug_gesture` |
| `ACKNOWLEDGE` | confirming, accepting input, closing a step, saying OK | `acknowledgement_close_hands_gesture` |
| `POLITE` | apologies, polite refusals, soft corrections, socially careful responses | `polite_apology_gesture` |
| `NONE` | no robot gesture should run | no gesture |

Do not emit robot-server IDs such as `open_question_gesture`,
`explanatory_sweep_gesture`, `uncertainty_shrug_gesture`,
`acknowledgement_close_hands_gesture`, or `polite_apology_gesture` directly in
`nonVerbal.gesture`.

## Hand Signs

Use top-level `motion.handSign`, not `nonVerbal.gesture`, for hand output.

Canonical values:
- `rock`
- `scissor`
- `paper`

Use singular `scissor`, not `scissors`. Avoid German hand-sign labels in
agent output unless a downstream contract explicitly asks for them.

Example RPS reveal:

```json
{
  "speech": "Ich waehle Papier.",
  "nonVerbal": { "gesture": "ACKNOWLEDGE" },
  "motion": { "handSign": "paper" },
  "display": null
}
```

## Robot-Server IDs

These robot-server command IDs are available on fake robot-server and G1:

- `open_question_gesture`
- `explanatory_sweep_gesture`
- `uncertainty_shrug_gesture`
- `acknowledgement_close_hands_gesture`
- `polite_apology_gesture`
- `right_hand_up`
- `face_wave`
- `left_kiss`
- `hands_up`
- `release_arm`
- `rock`
- `scissor`
- `paper`
- `idle_pose`

PROMETHEUS agents should not emit these snake_case IDs directly in
`nonVerbal.gesture` unless Valerian cockpit has been extended to map them.

## Possible Future Semantic Labels

If Valerian is extended later, these semantic mappings are recommended:

- `RIGHT_HAND_UP` -> `right_hand_up`
- `FACE_WAVE` -> `face_wave`
- `LEFT_KISS` -> `left_kiss`
- `HANDS_UP` -> `hands_up`
- `RELEASE_ARM` -> `release_arm`
- `IDLE_POSE` -> `idle_pose`

Do not use `ROCK`, `SCISSOR`, or `PAPER` as `nonVerbal.gesture` for RPS unless
there is a deliberate cockpit mapping change. Use `motion.handSign` instead.

## Test Checklist

For Valerian-facing agents, add or update tests proving:
- generated behaviour plans use only known gesture labels;
- RPS agents emit `motion.handSign` with `rock`, `scissor`, or `paper`;
- no agent emits unsupported locomotion such as `motion.move` or `motion.turn`;
- `resp.behaviour_plan` payloads remain valid JSON strings.
