# Human test: Coordination on Heroku

Start with one controlled 5–7 minute run in the Heroku-served Valerian Cockpit.
Check three things: accurate use of sensor evidence, correct task progression,
and complete audible delivery. This is a physical acceptance procedure, not a
record of a passed test.

## Prepare the Cockpit

- Refresh the Heroku-served Cockpit, then create a **new "Valerian Core –
  Coordination Agent"**, using **Cockpit** embodiment. Existing instances retain
  their older saved graphs.
- Use one browser tab, keep it visible, and select your usual microphone and
  audio output.
- Start the camera. Enable **Emit camera observations**, **Face emotion**,
  **Social context**, and **Hand sign**. Leave thresholds and intervals unchanged.
- Start **GPT-Live**. Keep the sensor reports and Diagnostics state accessible.
- Note the agent name, start time and timezone, served build if shown, and audio
  device. Allow each response to finish before speaking again.

## Follow this interaction

| Step | What to do and say | What to look for |
| --- | --- | --- |
| 1. Persona | Sit facing the camera, hands lowered. Say: **"Who are you?"** | It identifies itself consistently as Valerian and gives an appropriate account of its persona. |
| 2. Capabilities | **"What are you able to do, and what information can you receive from this cockpit?"** | It describes supported inputs and outputs accurately, distinguishing available capabilities from currently received observations. |
| 3. Face | Hold a neutral expression briefly, then smile naturally for about five seconds. Check the facial report, then ask: **"What does my facial expression look like right now?"** | Its answer should broadly match the fresh report and express uncertainty appropriately. It should not claim to know your actual feelings. If the detector itself misses the smile, record that separately. |
| 4. Social | Remain alone and visible. Ask: **"What can you tell about the people currently visible to the camera?"** | Its answer should match the social report. It should avoid inventing identities or relationships. |
| 5. Recognise scissors | Hold up a clear V/scissors sign. Wait for the hand report to show **Scissor** and a recent **Emitted** time. While holding it, ask: **"Can you see that sign? Tell me what it is."** | An answer such as **"I see a scissors sign."** There is no joke task yet. Note both the detected confidence and whether emission succeeded. |
| 6. Negotiate | Lower your hand for at least three seconds. Say: **"How about you tell me a joke when I show this scissor sign? Let's do it once."** | It proposes one joke, triggered by a subsequent scissors sign. It should not tell a sample joke or start executing during negotiation. Answer any necessary clarification; complete a recognition demonstration if explicitly requested. |
| 7. Accept | With your hands still lowered, say: **"Yes, let's start now."** | Diagnostics should move to execution/waiting for a fresh cue. **Acceptance itself must produce zero jokes.** Wait until its arming response finishes. |
| 8. Trigger | Show scissors again, without speaking. Once the report confirms emission, lower your hand. | Exactly **one joke** starts. Listen for delays, gaps, truncation or additional unsolicited speech. |
| 9. Confirm delivery boundary | Stay quiet while the entire joke plays, then allow a few seconds. | The task may briefly show **Finishing**, then reach **Reflection**. It should not complete before the joke finishes. If it remains stuck or shows **Response delivery uncertain**, record that before saying anything else. |
| 10. Reflect | Once Reflection is visible, say: **"Okay. That worked."** If it did not, describe the actual problem instead. | It records/discusses your feedback without repeating the joke or starting another run. |
| 11. Return | Say: **"Let's go back to the conversation."** Then ask: **"Where are we?"** | Diagnostics and the answer agree that you are back in Conversation. Earlier conversation remains visible and the joke task is no longer armed. |

## Optional social variation

Before step 5, have another person join you in camera view. Wait until the social
report updates, then ask, **"What changed in the scene?"** Have them leave and
repeat after the report updates. A grounded description is the target;
spontaneous arrival commentary is secondary.

## Capture the result

Use the interaction footer's flag when you notice a missed cue, unexpected
response, long pause or cutoff. Then open **Agent & Diagnostics → Telemetry →
Export JSON**, before reloading.

Keep brief notes of:

- Agent name, start/end times and timezone, served build if shown, and audio
  device.
- The first step that behaved unexpectedly, including any clarification or
  extra turns you needed.
- What the sensor report and task state showed.
- What you actually heard: telemetry excludes conversation text and audio.

Finally, stop GPT-Live and the camera. **Database quota headroom remains
unresolved**, so this first run should be bounded; avoid leaving sensing running
afterward. If requests start failing, capture the evidence and stop instead of
repeatedly retrying. The export and timestamps let us correlate the interaction
with server/database failures; telemetry alone does not establish the database
query rate or prove quota exhaustion.

## Related acceptance evidence

- [Recorded scissors interaction](../tests/gptlive/README.md#recorded-scissors-interaction)
- [Shared interaction fixture](../src/test/resources/coordination/scissors-interaction.json)
- [Coordination contract](../README.md#coordination-agent)
- [Implementation and verification evidence](../.agents/COORDINATION_RESULTS.md)
