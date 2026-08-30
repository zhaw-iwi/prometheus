import type { DesignerStepId } from "../stepper/DesignerStepper";

export interface CapabilityOption {
  id: string;
  label: string;
  description: string;
  family: string;
}

export interface PromptFieldDefinition {
  id: string;
  kind: string;
  stepId: Extract<DesignerStepId, "purpose" | "sensing" | "behaviour">;
  label: string;
  help: string;
  example: string;
}

export const AUTHORING_CATALOG_VERSION = 1;

export const OBSERVATION_OPTIONS: CapabilityOption[] = [
  { id: "obs.user_utterance", label: "What the user says", description: "Text or finalized speech transcription.", family: "Conversation" },
  { id: "obs.emotion.face", label: "Facial emotion", description: "An observed facial-expression category and affect.", family: "People" },
  { id: "obs.human.presence", label: "Human presence", description: "Whether people are currently visible.", family: "Social situation" },
  { id: "obs.social.grouping", label: "Social grouping", description: "Observed individuals and groups.", family: "Social situation" },
  { id: "obs.social.context", label: "Rich social context", description: "Attention and other structured social context.", family: "Social situation" },
  { id: "obs.social.situation_change", label: "Social situation change", description: "A meaningful change in the observed social scene.", family: "Social situation" },
  { id: "obs.hand.sign", label: "Hand sign", description: "A recognized rock, scissor, or paper sign.", family: "Gesture" },
  { id: "obs.weather.current", label: "Current weather", description: "Current structured weather conditions.", family: "Weather" },
  { id: "obs.weather.forecast", label: "Weather forecast", description: "Structured forecast information.", family: "Weather" },
];

export const MODALITY_OPTIONS: CapabilityOption[] = [
  { id: "speech", label: "Speech", description: "Spoken or displayed conversational text.", family: "Verbal" },
  { id: "nonVerbal.gesture", label: "Gesture", description: "A named nonverbal gesture.", family: "Nonverbal" },
  { id: "nonVerbal.facialExpression", label: "Facial expression", description: "A named facial expression.", family: "Nonverbal" },
  { id: "nonVerbal.gaze", label: "Gaze", description: "A gaze target or direction.", family: "Nonverbal" },
  { id: "nonVerbal.motion", label: "Body motion", description: "A body or platform motion instruction.", family: "Motion" },
  { id: "motion.handSign", label: "Hand-sign motion", description: "A rock, scissor, or paper hand sign.", family: "Motion" },
  { id: "display", label: "Display content", description: "Structured content for a screen or panel.", family: "Visual" },
];

export const PROMPT_FIELDS: PromptFieldDefinition[] = [
  { id: "purpose.persona", kind: "persona", stepId: "purpose", label: "Who should the agent be?", help: "Defines the role or persona represented during interaction.", example: "You are a calm, practical coaching assistant." },
  { id: "purpose.objective", kind: "objective", stepId: "purpose", label: "What should it accomplish?", help: "States the durable goal behind individual responses.", example: "Help the user identify one achievable next step." },
  { id: "purpose.context", kind: "context", stepId: "purpose", label: "In what setting is it used?", help: "Supplies the interaction setting without inventing live sensor facts.", example: "The conversation takes place during a brief planning session." },
  { id: "purpose.roles", kind: "roles", stepId: "purpose", label: "Who participates?", help: "Clarifies the agent and user roles.", example: "The user owns the decision; the agent helps structure the options." },
  { id: "purpose.language", kind: "language", stepId: "purpose", label: "How should language be handled?", help: "Adds language guidance beyond the catalog language code.", example: "Reply in clear English and explain uncommon terms." },
  { id: "purpose.tone", kind: "tone", stepId: "purpose", label: "What tone should it use?", help: "Sets the enduring interpersonal style.", example: "Be warm, direct, and never patronizing." },
  { id: "purpose.grounding", kind: "grounding", stepId: "purpose", label: "What may it rely on?", help: "Defines grounding limits and how to handle missing information.", example: "Use only the conversation and declared observations; acknowledge uncertainty." },
  { id: "purpose.boundaries", kind: "constraint", stepId: "purpose", label: "Which boundaries matter?", help: "Records important safety, scope, and refusal boundaries.", example: "Do not claim professional authority or make decisions for the user." },
  { id: "sensing.interpretation", kind: "observation-interpretation", stepId: "sensing", label: "How should observations be interpreted?", help: "Explains how selected signals inform a response.", example: "Treat observations as fallible context, not proof of a person's intent." },
  { id: "sensing.proactive", kind: "proactive-trigger", stepId: "sensing", label: "When may it react proactively?", help: "Defines when sensor changes warrant an unsolicited response.", example: "React only when a change is sustained and relevant to the current goal." },
  { id: "sensing.uncertainty", kind: "uncertainty", stepId: "sensing", label: "What does uncertainty mean?", help: "Prevents the prompt from turning ambiguous sensor data into facts.", example: "If confidence is low, ask or stay silent rather than guessing." },
  { id: "behaviour.objective", kind: "response-objective", stepId: "behaviour", label: "What should each response achieve?", help: "Focuses individual responses within the overall purpose.", example: "Move the conversation toward one concrete, user-owned choice." },
  { id: "behaviour.length", kind: "response-length", stepId: "behaviour", label: "How long should responses be?", help: "Sets a practical response-length expectation.", example: "Use at most three short sentences unless the user asks for detail." },
  { id: "behaviour.questions", kind: "question-frequency", stepId: "behaviour", label: "How often should it ask questions?", help: "Controls question frequency and avoids interrogation.", example: "Ask at most one focused question per turn." },
  { id: "behaviour.coordination", kind: "multimodal-coordination", stepId: "behaviour", label: "How should modalities coordinate?", help: "Keeps speech and nonverbal output coherent.", example: "Use gaze and gesture only when they reinforce the spoken response." },
  { id: "behaviour.suppression", kind: "suppression", stepId: "behaviour", label: "When should output be suppressed?", help: "Defines cases where silence or fewer modalities are preferable.", example: "Suppress nonverbal output when it would distract from sensitive content." },
  { id: "behaviour.fallback", kind: "fallback", stepId: "behaviour", label: "What is the fallback?", help: "Defines a safe response when no confident strategy applies.", example: "State what is missing and ask for one clarifying detail." },
];
