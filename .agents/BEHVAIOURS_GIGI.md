# GIGI Physical Behaviours

Use this note when creating PROMETHEUS agents for the Valerian cockpit and the
GIGI robot. PROMETHEUS emits `resp.behaviour_plan` events. The event `payload`
must be a JSON string containing a `BehaviourPlan`.

## Embodiment and Persona Identity

Scoped agent creation persists one embodiment for the instance:

- `COCKPIT` uses the public persona name **Valerian**;
- `ROBOT` uses the public persona name **Gigi**.

Valerian Cockpit exposes this choice when an instance is created. Prompt
assembly resolves existing Valerian or Gigi identity references to the selected
persona, so an agent definition does not need duplicate prompts for the two
embodiments. Omitting the value through the API defaults to `COCKPIT` for
compatibility. Embodiment selects identity; it does not change the
`BehaviourPlan` shape or automatically add robot motion.

## BehaviourPlan Shape

```json
{
  "speech": "Text to say/render in cockpit.",
  "nonVerbal": {
    "gesture": "EXPLAIN",
    "facialExpression": {"type": "warmNeutral", "intensity": 0.4},
    "gaze": {"direction": "toward_user", "focus": "older_adult"},
    "motion": {"stillness": 0.9, "energy": 0.1}
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
- Use `nonVerbal.facialExpression`, `nonVerbal.gaze`, and
  `nonVerbal.motion` as semantic descriptors only.
- Use `nonVerbal.motion` for small expressive qualities such as stillness and
  energy, not locomotion.
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
| `ROCK` | playing rock in rock-scissor-paper | `rock` |
| `SCISSOR` | playing scissor in rock-scissor-paper | `scissor` |
| `PAPER` | playing paper in rock-scissor-paper | `paper` |
| `NONE` | no robot gesture should run | no gesture |

Do not emit robot-server IDs such as `open_question_gesture`,
`explanatory_sweep_gesture`, `uncertainty_shrug_gesture`,
`acknowledgement_close_hands_gesture`, or `polite_apology_gesture` directly in
`nonVerbal.gesture`.

## Facial Expression, Gaze, and Nonverbal Motion

Use these fields as semantic descriptors that Valerian can render, inspect, or
map later. Keep them small and socially safe.

Recommended `nonVerbal.facialExpression` shape:

```json
{"type": "warmNeutral", "intensity": 0.4}
```

Recommended care-safe `type` values:
- `warmNeutral`
- `gentleSmile`
- `attentive`
- `thoughtful`
- `concernedCalm`

Recommended `nonVerbal.gaze` shape:

```json
{"direction": "toward_user", "focus": "older_adult"}
```

Recommended care-safe `direction` values:
- `toward_user`
- `briefly_aside`
- `soft_down`
- `toward_group`
- `forward`

Recommended care-safe `focus` values:
- `older_adult`
- `group`
- `shared_space`
- `none`

Recommended `nonVerbal.motion` shape:

```json
{"stillness": 0.9, "energy": 0.1}
```

Rules:
- Prefer high `stillness` and low `energy` in care, coaching, reminder, and
  delicate conversation.
- Do not emit nested locomotion fields such as `nonVerbal.motion.move` or
  `nonVerbal.motion.turn`.
- Do not use `nonVerbal.motion` for hand signs. Use top-level
  `motion.handSign`.

## Hand Signs

Use top-level `motion.handSign` for hand-sign output. During a rock-scissor-paper
reveal, also emit the matching `ROCK`, `SCISSOR`, or `PAPER` semantic label in
`nonVerbal.gesture` so Valerian dispatches the gesture.

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
  "nonVerbal": { "gesture": "PAPER" },
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

For RPS reveals, use `ROCK`, `SCISSOR`, or `PAPER` as `nonVerbal.gesture` and
emit the matching lower-case value in `motion.handSign`.

## Test Checklist

For Valerian-facing agents, add or update tests proving:
- generated behaviour plans use only known gesture labels;
- RPS agents emit matching semantic gestures and `motion.handSign` values;
- no agent emits unsupported locomotion such as `motion.move` or `motion.turn`;
- `resp.behaviour_plan` payloads remain valid JSON strings.
