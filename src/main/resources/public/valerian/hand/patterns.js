// Shared finger semantics for geometric recognition and digital demonstration.
// On input, folded thumb means palm opposition, independently of joint curvature.
// On output, the adapter couples thumb opposition to the requested bend.
const pattern=(thumb,index,middle,ring,little)=>Object.freeze({thumb,index,middle,ring,little});
export const fingerPatterns=Object.freeze({
  fist:pattern(1,1,1,1,1),peace:pattern(1,0,0,1,1),open_palm:pattern(0,0,0,0,0),
  thumbs_up:pattern(0,1,1,1,1),thumbs_down:pattern(0,1,1,1,1),index_up:pattern(1,0,1,1,1),
  shaka:pattern(0,1,1,1,0),horns:pattern(1,0,1,1,0),middle_finger:pattern(1,1,0,1,1),
  ok_ring:pattern(.7,.735,0,0,0),
});
export const derivedSigns=['shaka','horns','middle_finger'];
export function derivedSign(hand, states) {
  return derivedSigns.find(sign=>Object.entries(fingerPatterns[sign]).every(([finger,bend])=>
    Number.isFinite(hand?.fingers?.[finger])&&states?.[finger]===(bend?'bent':'straight')))??null;
}
