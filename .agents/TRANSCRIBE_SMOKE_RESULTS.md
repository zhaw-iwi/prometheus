# Live-transcription acoustic smoke record

This record compares the former combined Realtime speech session with the
transcription-first PROMETHEUS pipeline. Never infer a physical result from an
automated test. Use `PASS`, `FAIL`, or `NOT RUN` and attach concise evidence.

## Fixed decisions

- Input model: `gpt-live-transcribe`.
- Agent acknowledgement: `FULL_PLAN`.
- Agent playback policy: microphone turns gated while agent speech plays; no
  automatic barge-in in this migration.
- Default provider capture: `far_field`.
- Default browser capture: echo cancellation, noise suppression, and automatic
  gain control enabled; voice isolation disabled.
- Turn detection: `local_vad` with 1.5 seconds silence by default, plus manual
  commit as an operator option.
- Language default: selected agent language, with validated additional
  languages selectable.
- Audible output: canonical persisted behaviour speech, one active Valerian
  playback owner, no history/replay speech.

## Phrase corpus

Speak each phrase naturally. Do not read punctuation. Record the exact provider
transcript and highlight incorrect critical tokens.

| ID | Language | Expected phrase | Critical tokens |
| --- | --- | --- | --- |
| EN-1 | English | Prometheus, please summarize the appointment for Thursday at fourteen thirty. | Prometheus; Thursday; fourteen thirty |
| EN-2 | English | Valerian heard three people near the fountain despite the traffic noise. | Valerian; three people; fountain; traffic |
| EN-3 | English | Maya moved forty-four blue folders from room fourteen to room forty. | Maya; forty-four; blue; fourteen; forty |
| EN-4 | English | The wireless microphone is five meters away from the Bluetooth speaker. | wireless microphone; five meters; Bluetooth speaker |
| DE-1 | German | Prometheus, fasse den Termin am Donnerstag um vierzehn Uhr dreissig zusammen. | Prometheus; Donnerstag; vierzehn Uhr dreissig |
| DE-2 | German | Valerian hoert drei Personen beim Brunnen trotz des Verkehrslaerms. | Valerian; drei Personen; Brunnen; Verkehrslaerm |
| DE-3 | German | Maja bringt vierundvierzig blaue Mappen aus Raum vierzehn in Raum vierzig. | Maja; vierundvierzig; blaue; vierzehn; vierzig |
| DE-4 | German | Das drahtlose Mikrofon ist fuenf Meter vom Bluetooth-Lautsprecher entfernt. | drahtlose Mikrofon; fuenf Meter; Bluetooth-Lautsprecher |
| AR-1 | Arabic | بروميثيوس، لخص من فضلك موعد يوم الخميس الساعة الثانية والنصف بعد الظهر. | بروميثيوس؛ الخميس؛ الثانية والنصف |
| AR-2 | Arabic | سمع فاليريان ثلاثة أشخاص بالقرب من النافورة رغم ضوضاء المرور. | فاليريان؛ ثلاثة أشخاص؛ النافورة؛ المرور |
| AR-3 | Arabic | نقلت مايا أربعة وأربعين ملفاً أزرق من الغرفة الرابعة عشرة إلى الغرفة الأربعين. | مايا؛ أربعة وأربعين؛ أزرق؛ الرابعة عشرة؛ الأربعين |
| AR-4 | Arabic | يبعد الميكروفون اللاسلكي خمسة أمتار عن مكبر الصوت الذي يعمل بالبلوتوث. | الميكروفون اللاسلكي؛ خمسة أمتار؛ البلوتوث |

Use the written ASCII spellings only for comparison in this repository. The
speaker may pronounce normal German umlauts and `ss` sounds.

## Acceptance targets

Hard reliability targets apply to every scenario:

- Every finalized user turn is acknowledged exactly once.
- Agent playback produces zero acknowledged self-transcripts.
- Initial SSE hydration and reconnect replay produce zero audible responses.
- Stop, provider failure, and reconnect always restore a usable microphone
  state without refreshing the page.
- A second Valerian tab produces zero duplicate audible responses.

Transcription targets, calculated case-insensitively after punctuation removal:

- Near-field quiet: at least 95% word accuracy and all critical tokens correct.
- Far-field quiet: at least 90% word accuracy and all intent-changing critical
  tokens correct.
- Background/outdoor noise: at least 85% word accuracy and no changed number,
  date, time, place, or person name.
- Multiple conversational speakers: at least 85% word accuracy for
  non-overlapping turns and correct turn count. Overlapping speech is recorded
  but is not a diarization requirement.

Operational targets:

- Final transcript observed within 4 seconds of the configured end of turn.
- First synthesized audio observed within 10 seconds of the final transcript;
  also record the raw value because language-model and network latency vary.
- A transient network interruption or input-device replacement returns to a
  listening state within 20 seconds or presents an actionable terminal error.

## Hardware and runtime metadata

| Field | Baseline value | Transcription-first value |
| --- | --- | --- |
| Date/time | NOT RUN | NOT RUN |
| Operator | NOT RUN | NOT RUN |
| Commit | `bb70b8f` | NOT RUN |
| OS | Windows host; exact build NOT RUN | NOT RUN |
| Browser/version | NOT RUN | NOT RUN |
| Microphone/model | NOT RUN | NOT RUN |
| Speaker/model | NOT RUN | NOT RUN |
| Input/output device IDs | NOT RUN | NOT RUN |
| OpenAI model/settings | Combined Realtime defaults; live request NOT RUN | NOT RUN |
| Agent definition/language | NOT RUN | NOT RUN |

## Environment matrix

Complete one row per phrase or attach a referenced raw results table. `Distance`
is mouth-to-microphone distance. Latencies start at detected speech end and
final-transcript arrival respectively.

| Pipeline | Scenario | Phrase IDs | Distance | Noise/source | Transcript/accuracy | Missed or duplicate turns | Self-transcription | Final latency | First-audio latency | Recovery | Result | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Combined baseline | Near-field quiet | EN-1..4, DE-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical audio unavailable to coding environment. |
| Combined baseline | Far-field quiet | EN-1..4, DE-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical audio unavailable to coding environment. |
| Combined baseline | Outdoor/background noise | EN-1..4, DE-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical audio unavailable to coding environment. |
| Combined baseline | Wireless mic plus Bluetooth playback | EN-4, DE-4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical audio unavailable to coding environment. |
| Combined baseline | Two or more speakers, non-overlapping then overlapping | EN-1..4, DE-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Speaker identity is not expected. |
| Transcription-first | Near-field quiet | EN-1..4, DE-1..4, AR-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical run required. |
| Transcription-first | Far-field quiet | EN-1..4, DE-1..4, AR-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical run required. |
| Transcription-first | Outdoor/background noise | EN-1..4, DE-1..4, AR-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical run required. |
| Transcription-first | Wireless mic plus Bluetooth playback | EN-4, DE-4, AR-4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Physical run required. |
| Transcription-first | Two or more speakers, non-overlapping then overlapping | EN-1..4, DE-1..4, AR-1..4 | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | NOT RUN | Speaker identity is not expected. |

## Resilience checklist

| Check | Baseline | Transcription-first | Evidence/notes |
| --- | --- | --- | --- |
| Session expiry and reissue | NOT RUN | NOT RUN | |
| Transient network loss | NOT RUN | NOT RUN | |
| Microphone unplug/replug | NOT RUN | NOT RUN | |
| Output-device change | NOT RUN | NOT RUN | |
| Page refresh during listening | NOT RUN | NOT RUN | |
| Agent switch, reset, and delete | NOT RUN | NOT RUN | |
| Hidden tab | NOT RUN | NOT RUN | |
| Second-tab microphone conflict | NOT RUN | NOT RUN | |
| Second-tab playback conflict | Not applicable to combined baseline | NOT RUN | |
| Stop during synthesis/playback | NOT RUN | NOT RUN | |

## Result summary

- Combined Realtime baseline: `NOT RUN` in the coding environment because real
  acoustic hardware input is unavailable.
- Transcription-first implementation: automated contracts pass; physical
  English, German, and Arabic acoustic acceptance remains `NOT RUN`.
- Accepted deviations: none.

