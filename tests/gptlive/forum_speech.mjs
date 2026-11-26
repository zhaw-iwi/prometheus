// Flag an explicit denial of an observed capability. Other wording needs no prescribed transcript.
export function deniesRenderedHand(reply = '') {
  const text = reply.toLowerCase().replaceAll('’', "'");
  const denial = /\b(?:can't|cannot|unable to)\s+(?:actually\s+)?(?:move|make|show|perform|display|render)\b[^.!?]{0,65}\b(?:hands?|signs?|gestures?|thumbs?)/;
  const description = /\b(?:can|could)\s+(?:only\s+|just\s+|help\s+)*(?:describe|plan|explain|talk you through)\b/;
  const digitalAlternative = /\b(?:can|will|i'll)\s+(?:still\s+)?(?:show|make|render|display|perform|move)\b[^.!?]{0,100}\b(?:digital|virtual|screen|avatar|renderer)/;
  return denial.test(text) && description.test(text) && !digitalAlternative.test(text);
}

// The scripted visitor speaks an explicit acceptance. A timing word alone cannot establish it.
export function capturesConsent(text = '') {
  const words = text.toLowerCase().replaceAll('’', "'").replace(/[.!?,]+$/g, '').trim();
  if (/\b(?:not|don't|stop|cancel|wait)\b|\b(?:the|current)\s*$/.test(words)) return false;
  return /\b(?:accept|approve|start|begin|go ahead|let's do it|yes)\b/.test(words);
}
