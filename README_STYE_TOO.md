# README_STYE_TOO.md

## Purpose

This guide helps Marc run and evaluate the new **Social Initiative MVP** agent in the lab with:

- Unitree G1 robot (speech output path)
- Realtime speech-to-speech client
- Visual multifacial client (named face emotion)
- Visual social client (group/presence cues)

The target behavior follows the scripted scenario in:

- `src/main/resources/scripts/social-initiative-mvp-replay-script.json`

---

## Interaction Idea (Scenario)

The agent uses two states:

1. `ConversationHandling`
2. `SocialSituationAssessment`

Expected flow:

1. Agent starts with a neutral room greeting.
2. A named face-emotion event (Alice) appears -> transition to social assessment.
3. Agent gives proactive social greeting to Alice.
4. Social grouping event arrives -> agent stays in social assessment.
5. A direct user utterance asks for help -> transition back to conversation handling.
6. Agent gives direct task-planning response.

---

## Preconditions

1. Configure and run backend as usual (`application.properties`, `openai.properties`).
2. Seed the agent from:
   - `src/test/java/ch/zhaw/prometheus/agents/SocialInitiativeMvpAgent.java`
3. Get the seeded `agentId`:
   - `GET /agent`
4. Start the agent:
   - `POST /{agentId}/start`

PowerShell example:

```powershell
$agentId = "<PUT-UUID-HERE>"
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/$agentId/start"
```

---

## Clients To Open

Open these in separate browser tabs:

1. Realtime client  
   `http://localhost:8080/realtime/?agentId=<agentId>`
2. Visual multifacial client (named user)  
   `http://localhost:8080/visual/multifacial/?agentId=<agentId>`
3. Visual social client  
   `http://localhost:8080/visual/social/?agentId=<agentId>`
4. Monitor client  
   `http://localhost:8080/monitor/?agentId=<agentId>`

Optional:

5. Nonverbal renderer  
   `http://localhost:8080/nonverbal/?agentId=<agentId>`

---

## Lab Roles

Use at least 2 people:

1. **Operator (Marc)**: runs clients, starts/stops flows.
2. **Alice**: stands in front of multifacial camera, then speaks to agent.
3. **Optional second person**: enters scene to create grouping signals.

---

## Step-by-Step Lab Script

### Step 1: Start baseline behavior

1. Ensure agent is started (`POST /start`).
2. Confirm monitor shows state: `ConversationHandling`.
3. You should see initial speech similar to:
   - "Hello everyone. I can help if you need anything."

### Step 2: Trigger social transition with named face event

1. In multifacial client, set `User Name = Alice`.
2. Start camera and emit face observations (smile/happy expression).
3. Confirm monitor transitions to `SocialSituationAssessment`.

### Step 3: Trigger proactive social greeting

The current MVP does not auto-speak immediately on every acknowledge.  
Trigger generation manually:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/$agentId/behaviour/generate"
```

Expected style:

- "Hello Alice, nice to see you. If you want, tell me how I can help."

### Step 4: Keep social context active

1. In visual social client, emit grouping/presence event (2 people).
2. Confirm state remains `SocialSituationAssessment`.

### Step 5: Return to conversation mode via direct request

1. Alice speaks in realtime client (or microphone to robot pipeline):
   - "Can you help me plan my next tasks?"
2. Confirm transition to `ConversationHandling`.

### Step 6: Generate direct response

If needed, trigger generate manually again:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/$agentId/behaviour/generate"
```

Expected style:

- "Sure. Tell me your top priority and deadline, and we will make a short plan."

---

## What To Verify In Monitor

1. State transitions:
   - `ConversationHandling` -> `SocialSituationAssessment` -> `ConversationHandling`
2. Event history includes:
   - `obs.emotion.face` (with `userName`)
   - `obs.social.grouping`
   - `obs.user_utterance`
   - `resp.behaviour_plan`
3. Storage includes `SocialContext` updates with:
   - user name (`Alice`)
   - user count
   - direct request presence/summary

---

## Deterministic Replay (Optional)

For exact scripted behavior (test mode), run:

```powershell
.\mvnw.cmd "-Dtest=SocialInitiativeMvpReplayIntegrationTest" test
```

This uses scripted gateway responses from:

- `src/main/resources/scripts/social-initiative-mvp-replay-script.json`

---

## Notes

1. Exact wording in live OpenAI mode can differ; verify intent and transitions, not exact phrasing.
2. Current MVP has no strict deterministic greeting cooldown yet.
3. If proactive speech does not appear after visual events, call `/behaviour/generate` manually as shown above.

