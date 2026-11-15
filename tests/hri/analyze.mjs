import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
export function summarize(dataset){
  assert.equal(dataset.schema,'hri-dataset-v1');const ids=new Set(),people=new Set(),stages={},assignments={};
  for(const a of dataset.attempts){const s=a.session;assert.ok(!ids.has(s.id));ids.add(s.id);people.add(s.participantId);assert.equal(s.mode,dataset.mode);
    stages[s.stage]=(stages[s.stage]??0)+1;if(!s.predecessorId)assignments[s.scenario]=(assignments[s.scenario]??0)+1;
    assert.equal(a.assessment.acceptedAgreements,a.agreements.length);
    for(const row of a.answers)assert.equal(row.materialsVersion,s.materialsVersion);
    assert.ok(a.records.some(r=>r.kind==='METADATA'));
  }
  const byId=new Map(dataset.attempts.map(a=>[a.session.id,a]));
  for(const a of dataset.attempts)if(a.session.predecessorId){const prior=byId.get(a.session.predecessorId);assert.ok(prior,'Replacement predecessor must remain in export');
    assert.equal(prior.session.participantId,a.session.participantId);assert.equal(prior.session.scenario,a.session.scenario);assert.equal(prior.session.stage,'REPLACED');}
  assert.equal(dataset.counts.attempts,ids.size);assert.equal(dataset.counts.participants,people.size);
  assert.deepEqual(dataset.counts.attemptStages,stages);assert.deepEqual(dataset.counts.initialAssignments,assignments);
  return {attempts:ids.size,participants:people.size,stages,assignments,acceptedSnapshots:dataset.attempts.reduce((n,a)=>n+a.agreements.length,0),
    incompleteRecordings:dataset.attempts.filter(a=>!a.manifest.recordingComplete).length,
    missingUmux:dataset.attempts.filter(a=>!a.assessment.umuxLite.complete).length};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const root=process.argv[2],files=await fs.readdir(root,{recursive:true});const completed=[];let dataset;
  for(const file of files){if(file.endsWith('completed-export.json'))completed.push(JSON.parse(await fs.readFile(path.join(root,file),'utf8')));
    if(file.endsWith('dataset.json'))dataset=JSON.parse(await fs.readFile(path.join(root,file),'utf8'));}
  assert.equal(completed.length,5,'All three scenarios and both failed journeys required');assert.ok(dataset);
  for(const a of completed){assert.equal(a.session.stage,'COMPLETE');assert.equal(a.manifest.recordingComplete,true);assert.equal(a.manifest.procedureComplete,true);
    const success=a.session.finishReason==='ACTIVITY_ENDED';assert.equal(a.agreements.length,success?2:0);assert.equal(a.assessment.umuxLite.complete,success);
    assert.equal(a.answers.filter(r=>r.form==='understanding').length,3);assert.equal(a.answers.filter(r=>r.form==='post').length,8);}
  const report={passed:true,completedJourneys:completed.map(a=>({scenario:a.session.scenario,ending:a.session.finishReason,agreements:a.agreements.length})),dataset:summarize(dataset)};
  await fs.writeFile(path.join(root,'analysis.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report));
}
