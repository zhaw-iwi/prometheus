# GPT-Live physical acceptance

Status: **NOT RUN**. Automated browser RMS, transcripts and playback callbacks
do not establish what a person heard through a physical speaker.

Use a disposable Coordination agent on the intended target build. Record build
revision, browser/version, operating system, microphone, speaker/headset, room,
volume and the cockpit's requested/applied echo-cancellation settings. Start with
the standard settings; record every change. Keep an operator beside the device.
Export the Live diagnostics after each failure, with the approximate time and
what was heard. Never include credentials in the report.

| Trial | Operator action and pass condition | Result |
| --- | --- | --- |
| Ordinary Live | Greet it, ask capabilities and ask a short planning question. Hear one prompt response each, with both speakers recorded once. | NOT RUN |
| Quiet and noise | Repeat in quiet, then with normal room noise and a short non-speech sound. Note false interruption or false user turns; do not assume that RMS identifies speech. | NOT RUN |
| Output echo | Use the target speakers at the intended volume. Remain silent during a reply. The reply must not become user input or cause unexplained self-interruption. | NOT RUN |
| Intentional interruption | Ask for several sentences, then clearly interrupt halfway through. Confirm that the assistant yields. Any retained prefix must be labelled incomplete and must not authorize task completion. | NOT RUN |
| Coordination | Propose one welcome on a fresh scissors sign, with recognition checked first. Try starting before the check, complete the check, request help, explicitly accept, then show a fresh sign. Confirm one welcome and reflection; repeating the sign must not spend another action. | NOT RUN |
| Revision | After reflection, request a changed response while retaining the other terms. Review the revised draft, verify human/agent roles, and require renewed preparation/acceptance before any new action. | NOT RUN |
| Stop and reconnect | Stop during a reply. Output must stop promptly. Reconnect and reload; recorded evidence remains once and the interrupted task contribution must not replay. | NOT RUN |
| Continuous | Switch to Continuous and repeat a short exchange. Confirm that input pauses during canonical speech and reopens after playback; no duplicate reply. | NOT RUN |

Record each result as PASS/FAIL with observations, device settings and artifact
paths. A failure stays visible after a correction; append the retest. Release
acceptance remains incomplete until this evidence is reviewed.
