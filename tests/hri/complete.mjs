import assert from 'node:assert/strict';
/** Full procedure using private fixture answers, never sent through the model route. */
export async function completeAssessment(api,{missing=false}={}) {
  let page=await api('/api/study/page');assert.equal(page.session.stage,'ASSESSMENT');assert.equal(page.debrief,null);
  const answers=Object.fromEntries(page.fields.map(i=>[i.id,missing?'Not applicable.':'PRIVATE_ASSESSMENT_FIXTURE: compare the saved roles, conditions and requested revision.']));
  await api('/api/study/forms/understanding',answers);await api('/api/study/forms/understanding',answers);
  page=await api('/api/study/page');assert.equal(page.session.stage,'POST');assert.equal(page.debrief,null);
  await api('/api/study/forms/post',Object.fromEntries(page.fields.map(i=>[i.id,i.kind==='rating7'?(missing?'SKIP':'4'):''])));
  page=await api('/api/study/page');assert.equal(page.session.stage,'DEBRIEF');assert.ok(page.debrief);
  assert.equal((await api('/api/study/complete',{})).stage,'COMPLETE');
}
