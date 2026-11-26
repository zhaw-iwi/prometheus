// Pure, bounded interpretation of the existing public BehaviourPlan contract.
const object = value => value && typeof value === 'object' && !Array.isArray(value) ? value : {};
const token = value => typeof value === 'string' ? value.trim().replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase().replace(/[ -]+/g, '_') : '';
const lookup = (map, key) => Object.hasOwn(map, key) ? map[key] : undefined;
export const unit = (value, fallback = 0) => typeof value === 'number' && Number.isFinite(value)
  ? Math.max(0, Math.min(1, value)) : fallback;
const signs = new Set(['rock', 'paper', 'scissor']);
const gestures = new Set(['OPEN_QUESTION', 'EXPLAIN', 'UNCERTAIN', 'ACKNOWLEDGE', 'RIGHT_HAND_UP', 'NONE']);
const faces = {
  neutral: 'neutral', warm_neutral: 'neutral', attentive: 'attentive', focused: 'attentive', curious: 'curious',
  happy: 'happy', smile: 'happy', smiling: 'happy', warm_smile: 'happy', friendly: 'happy',
  sad: 'sad', concerned: 'concerned', empathetic: 'concerned', surprised: 'surprised',
  uncertain: 'uncertain', confused: 'uncertain', angry: 'angry', serious: 'attentive',
};
const gazes = {
  toward_user: [0, 0], at_user: [0, 0], user: [0, 0], speaker: [0, 0], forward: [0, 0],
  toward_group: [0.2, 0], group: [0.2, 0], left: [-0.45, 0], right: [0.45, 0],
  toward_left: [-0.45, 0], toward_right: [0.45, 0], down: [0, 0.25], up: [0, -0.25],
  away: [0.55, 0.12], averted: [0.55, 0.12], downward: [0, 0.25], upward: [0, -0.25],
};
export function normalizePlan(plan, modalities = null) {
  plan = object(plan);
  const allowed = name => modalities === null || modalities.some(m => name === m || name.startsWith(m + '.'));
  const nv = object(plan.nonVerbal), motion = allowed('motion.handSign') ? object(plan.motion) : {};
  const display = allowed('display') && plan.display != null ? plan.display : null;
  const d = object(display), warnings = [];
  let gesture = allowed('nonVerbal.gesture') ? token(nv.gesture).toUpperCase() || 'NONE' : 'NONE';
  const parseSign = value => {
    const label = token(value);
    const sign = lookup({rock:'rock',stein:'rock',paper:'paper',papier:'paper',scissor:'scissor',scissors:'scissor',schere:'scissor'}, label);
    if (label && !sign) warnings.push('Unmapped hand sign: ' + label);
    return sign || '';
  };
  let sign = parseSign(d.agentSign) || parseSign(motion.handSign);
  if (!sign && signs.has(gesture.toLowerCase())) sign = gesture.toLowerCase();
  if (!gestures.has(gesture)) {
    if (!signs.has(gesture.toLowerCase())) warnings.push('Unmapped gesture: ' + gesture);
    gesture = 'NONE';
  }
  const face = allowed('nonVerbal.facialExpression') ? nv.facialExpression : null;
  const faceLabel = token(typeof face === 'string' ? face : face?.type || face?.expression);
  if (faceLabel && !lookup(faces, faceLabel)) warnings.push('Unmapped expression: ' + faceLabel);
  const gaze = allowed('nonVerbal.gaze') ? nv.gaze : null;
  const gazeLabel = token(typeof gaze === 'string' ? gaze : gaze?.direction || gaze?.focus);
  if (gazeLabel && !lookup(gazes, gazeLabel)) warnings.push('Unmapped gaze: ' + gazeLabel);
  const expressive = allowed('nonVerbal.motion') ? object(nv.motion) : {};
  const energy = unit(motion.energy ?? expressive.energy, 0.35);
  const stillness = unit(motion.stillness ?? expressive.stillness, 0.75);
  const effector = token(motion.effector) || 'right_hand';
  const hand = ['left_hand', 'right_hand', 'both_hands'].includes(effector) ? effector : '';
  if (sign && !hand) warnings.push('Unmapped effector: ' + effector);
  return {
    gesture, face: lookup(faces, faceLabel) || 'neutral', faceLabel,
    intensity: faceLabel ? unit(object(face).intensity, 0.55) : 0,
    gaze: lookup(gazes, gazeLabel) || [0, 0], gazeLabel, focus: typeof gaze?.focus === 'string' ? gaze.focus : '',
    energy, stillness, sign: hand ? sign : '', hand, display, warnings,
  };
}

// The cockpit already deduplicates event identity. This class admits transient
// motion only when the event is fresh AND its view is visible at receipt time.
export class EmbodimentState {
  constructor() { this.clear(); this.visible = false; this.reduced = false; }
  clear() { this.pose = null; this.started = null; }
  visibility(visible, reduced = this.reduced) {
    this.visible = visible; this.reduced = reduced; this.started = null;
  }
  receive(plan, options = {}, now = 0) {
    if (options.preserveNonSpeech) return;
    this.pose = normalizePlan(plan, options.modalities ?? null);
    const fresh = ['live', 'acknowledgement'].includes(options.delivery) && !options.fromHistory;
    this.started = fresh && this.visible && !this.reduced ? now : null;
  }
  settle() { this.started = null; }
  sample(now) {
    if (!this.pose) return { pose: null, gesture: 0, ambient: 0, active: false };
    const duration = 2800 - this.pose.energy * 900;
    const elapsed = this.started === null ? null : Math.max(0, now - this.started);
    const active = elapsed !== null && elapsed < duration && this.visible && !this.reduced;
    // Static previews show a representative pose; completed gestures return home.
    const gesture = elapsed === null ? 1 : active ? Math.sin(Math.PI * elapsed / duration) : 0;
    return { pose: this.pose, gesture, active, transition: elapsed === null ? 1 : Math.min(1, elapsed / 450),
      ambient: this.visible && !this.reduced ? this.pose.energy * (1 - this.pose.stillness) : 0 };
  }
}
