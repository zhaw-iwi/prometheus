# Valerian forum playbook

Use a fresh **Valerian Core – Coordination Agent** with the digital robot visible. Type or speak the **bold** lines, one turn at a time. In GPT-Live, wait for the end of the reply. Use **Current State** and the robot’s hand to check what happened. Live’s wording can vary.

## Start in Conversation

| Say or do | What to expect |
| --- | --- |
| **Who are you?** | Valerian introduces itself as a digital agent from ZHAW’s SIRA Lab. |
| **What are you able to do?** | It describes abilities such as recognizing supplied cues, showing gestures and agreeing a task with you. |
| **What can you see right now?** | With camera delivery off, expect it to say what evidence is available. To give it something to report, enable **Hand sign** and **Emit camera observations**, start the camera and hold up scissors. Ask **What hand sign do you see now?** Expect scissors if recognized, or an explanation that it needs a fresh observation. |
| **Make a thumbs-up sign with your right hand.** | The digital right hand shows a thumbs-up. This is an immediate request. The state stays **Conversation**. |

## Teach one finger to trigger a count

| Say or do | Expected state and behaviour |
| --- | --- |
| **When I raise only my right index finger, count to three on your right hand. Start with your thumb, then add index and middle. Half a second per number, one count sequence in total. Let me demonstrate first, then ask before starting. I’ll judge the result.** | **Conversation → Preparation** once the terms are clear. If it enters **Agreement**, answer the missing question. Keep your hand down until asked to demonstrate. |
| Hold up your **right index only**, with your thumb and other fingers folded. Hold steady until recognized, then lower your hand. | **Preparation → Awaiting acceptance.** Valerian reviews the cue, the count and the one-action limit. Its hand stays still during rehearsal. |
| After the review, say **I accept the current proposal.** Wait for the start confirmation. | **Awaiting acceptance → Execution.** The robot waits for a fresh cue. If Live heard only part of your consent and the state still says **Awaiting acceptance**, say **Start now.** |
| Raise the same index again. Watch the robot, then lower your hand and try the cue once more. | **Execution → Finishing → Reflection.** The robot counts on its fingers once. The second cue gets no second count. **Finishing** can be brief while the renderer confirms playback. |
| **I saw all three numbers. What did you record?** Then **Return to conversation.** | In **Reflection**, compare your observation with the recorded action. The explicit return takes you to **Conversation** and preserves the history. Switch off **Emit camera observations** between visitors. |

## Steer it or try a variant

- **Check or change state.** Ask **Which state are we in?** at any point. After acceptance, with your hand down, say **Pause the task** → **Paused**, then **Resume the task** → **Execution**. To finish early, **Stop the task** → **Reflection**. To go straight back to the opening state, say **Return to conversation**.
- **Revise the agreement.** In Reflection, say **Let’s change it. Same cue, but hold each number for one second. Ask me before starting.** Expect a new draft, any required rehearsal, and a fresh acceptance. If the state shows **Uncertain**, ask **What happened?** before choosing whether to retry.
- **Try clarification or pointing.** In Conversation, say **Make an OK sign.** Expect a choice between thumbs-up and the diver’s ring, with motion withheld. Answer **Make the diver’s OK ring with your right hand.** For a directional command, try **Point to your own left with your left hand.**
- **Camera-free count.** In Conversation, say **Count one through five on your right hand, starting with your thumb. Hold each number for one second.** Click **Stop hand sequence** when you see two. The count stops and you stay in **Conversation**.
- **Camera-free task.** Say **Propose one short welcome line for forum visitors. I’ll judge it. Wait for my approval before giving it.** At **Awaiting acceptance**, say **Start now.** Expect one welcome line, then **Reflection**. Tell Valerian whether you would use it.

[Original poster interaction](TRANSCRIPT.md) · [Forum smoke tests](../../../tests/gptlive/README.md#forum-playbook-baseline) · [Hand demonstration prompts](../../../tests/gptlive/hand-cases.json) · [Recorded counting and pointing results, attempts 119 and 121](../../../.agents/HAND_INTERACTION_RESULTS.md#ordered-hi-12-acceptance-under-allocation-18)
