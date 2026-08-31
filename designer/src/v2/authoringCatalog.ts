export interface GuidanceIntent {
  kind: string;
  label: string;
  description: string;
  promptField: "responsePrompt" | "starterPrompt" | "summaryPrompt" | "nonverbalPlanPrompt" | "gesturePrompt";
  example: string;
}

export const GUIDANCE_INTENTS: GuidanceIntent[] = [
  { kind: "identity-role", label: "Identity and role", description: "Who the agent is and how it should introduce its role.", promptField: "responsePrompt", example: "You are a calm guide who helps visitors orient themselves." },
  { kind: "objective", label: "Goal and outcome", description: "What a useful interaction should achieve.", promptField: "responsePrompt", example: "Help the person choose a practical next step." },
  { kind: "audience-setting", label: "Audience and setting", description: "Who it serves and the situation in which it is used.", promptField: "responsePrompt", example: "Support first-time visitors in a busy reception area." },
  { kind: "language-style", label: "Language and style", description: "Language, tone, length, and formality.", promptField: "responsePrompt", example: "Use plain English and keep ordinary answers to two short sentences." },
  { kind: "boundaries-referral", label: "Boundaries and referral", description: "Limits and when to involve a qualified person. This is guidance, not guaranteed enforcement.", promptField: "responsePrompt", example: "Do not diagnose. Encourage the person to contact a clinician for medical decisions." },
  { kind: "uncertainty", label: "Uncertainty and perception limits", description: "How to acknowledge missing, ambiguous, or unreliable information.", promptField: "responsePrompt", example: "Say when a signal is unclear and ask one clarifying question." },
  { kind: "modality-guidance", label: "Multimodal coordination", description: "How speech, display, expression, and movement should work together.", promptField: "nonverbalPlanPrompt", example: "Keep gestures subtle and aligned with the spoken message." },
  { kind: "process", label: "Flexible process", description: "A helpful sequence without turning it into a rigid script.", promptField: "responsePrompt", example: "First understand the need, then offer options, and finally confirm the next step." },
  { kind: "example", label: "Positive example", description: "An example of a good response or interaction.", promptField: "responsePrompt", example: "For example: ‘I may have misunderstood. Would you like directions or timetable help?’" },
  { kind: "counterexample", label: "Counterexample", description: "An example of behavior to avoid.", promptField: "responsePrompt", example: "Avoid inventing details or presenting uncertain observations as facts." },
  { kind: "completion", label: "Completion", description: "How to close or hand off an interaction well.", promptField: "responsePrompt", example: "End by confirming what will happen next." },
  { kind: "starter", label: "When the agent begins", description: "Guidance used when this scope starts.", promptField: "starterPrompt", example: "Greet the person briefly and ask how you can help." },
  { kind: "summary", label: "Summary", description: "What an interaction summary should retain.", promptField: "summaryPrompt", example: "Summarize the request, decisions, and agreed next step." },
];

export interface CapabilityOption {
  id: string;
  label: string;
  group: string;
  description: string;
  example: string;
  uncertainty: string;
}

export const OBSERVATION_CAPABILITIES: CapabilityOption[] = [
  { id: "obs.user_utterance", label: "What the person says", group: "Conversation", description: "Finalized typed or spoken user input.", example: "A question, answer, or request.", uncertainty: "Speech recognition can be incomplete or wrong; guidance should allow clarification." },
  { id: "obs.emotion.face", label: "Facial emotion cues", group: "People", description: "A coarse observed facial-affect category.", example: "A possible happy, neutral, or concerned expression.", uncertainty: "An observed cue is not a diagnosis or a reliable statement of how someone feels." },
  { id: "obs.human.presence", label: "Human presence", group: "People", description: "Whether a person is currently visible.", example: "Someone enters or leaves the camera view.", uncertainty: "Occlusion and camera position can hide a present person." },
  { id: "obs.social.grouping", label: "People and groups", group: "Social setting", description: "Coarse individual or group arrangement.", example: "One person or several people are visible.", uncertainty: "Grouping does not establish relationships, roles, or consent." },
  { id: "obs.social.context", label: "Social context", group: "Social setting", description: "Structured contextual cues about attention and the nearby social scene.", example: "A person appears to be addressing the agent.", uncertainty: "Context is an interpretation and may be incomplete." },
  { id: "obs.social.situation_change", label: "Social situation changes", group: "Social setting", description: "A notable change in the observed social scene.", example: "Another person joins the interaction.", uncertainty: "Short-lived changes and sensor errors can look alike." },
  { id: "obs.hand.sign", label: "Hand signs", group: "Gestures", description: "A registered hand-sign observation.", example: "Rock, scissor, or paper in the installed game operation.", uncertainty: "Hands outside the camera view or unusual angles reduce confidence." },
  { id: "obs.weather.current", label: "Current weather", group: "Environment", description: "A supplied current-weather observation.", example: "Current temperature and conditions.", uncertainty: "The value is only as current and local as its source." },
  { id: "obs.weather.forecast", label: "Weather forecast", group: "Environment", description: "A supplied forecast observation.", example: "Expected conditions later today.", uncertainty: "Forecasts are estimates and can change." },
];

export const EXPRESSION_CAPABILITIES: CapabilityOption[] = [
  { id: "speech", label: "Speak", group: "Voice and screen", description: "Express a response as synthesized speech.", example: "A short spoken answer.", uncertainty: "Pronunciation and the listening environment affect comprehension." },
  { id: "display", label: "Show on screen", group: "Voice and screen", description: "Present structured visual output.", example: "A status, choice, or game result.", uncertainty: "The client decides how supported display content is rendered." },
  { id: "nonVerbal.gesture", label: "Use gestures", group: "Nonverbal expression", description: "Request a communicative gesture.", example: "A small greeting or pointing gesture.", uncertainty: "Available gestures depend on the connected embodiment." },
  { id: "nonVerbal.facialExpression", label: "Use facial expression", group: "Nonverbal expression", description: "Request a facial-expression cue.", example: "A warm neutral expression.", uncertainty: "Available expressions depend on the connected embodiment." },
  { id: "nonVerbal.gaze", label: "Direct gaze", group: "Nonverbal expression", description: "Request a gaze direction or attention cue.", example: "Look toward the current speaker.", uncertainty: "Gaze rendering depends on the connected embodiment." },
  { id: "nonVerbal.motion", label: "Use body motion", group: "Nonverbal expression", description: "Request a broader motion cue.", example: "A subtle posture or body movement.", uncertainty: "Motion availability and safe range depend on the connected embodiment." },
  { id: "motion.handSign", label: "Show a hand sign", group: "Physical expression", description: "Request a registered hand-sign motion.", example: "Rock, scissor, or paper.", uncertainty: "The connected embodiment must support the requested sign." },
];

export const GUIDANCE_EXAMPLES = [
  {
    id: "helpful-guide",
    title: "Helpful public-facing guide",
    description: "A concise purpose, audience, style, uncertainty, and boundary starting point.",
    sections: [
      { kind: "identity-role", content: "You are a calm, approachable guide." },
      { kind: "objective", content: "Help the person understand their options and choose a practical next step." },
      { kind: "audience-setting", content: "Serve people with varied background knowledge in a public setting." },
      { kind: "language-style", content: "Use plain language, short answers, and one question at a time." },
      { kind: "uncertainty", content: "Acknowledge uncertainty and ask for clarification when information is incomplete." },
      { kind: "boundaries-referral", content: "Do not claim professional authority; refer decisions outside your role to a qualified person." },
    ],
  },
] as const;

export function guidanceIntent(kind: string): GuidanceIntent | undefined {
  return GUIDANCE_INTENTS.find((intent) => intent.kind === kind);
}

export function capabilityOption(id: string, options: CapabilityOption[]): CapabilityOption | undefined {
  return options.find((option) => option.id === id);
}

export function humanizeCapabilityGroup(group: string): string {
  if (group === "rock-scissor-paper") return "Rock, scissor, paper";
  if (group === "prompt-response") return "Guided response";
  if (group === "exact-text-response") return "Repeat exact text";
  return group.split(/[-_.]+/).filter(Boolean).map((word) => `${word[0]?.toUpperCase() ?? ""}${word.slice(1)}`).join(" ");
}
