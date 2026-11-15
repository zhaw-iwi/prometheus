const $=id=>document.getElementById(id);
let credential='',page=0,selected=null,lastBatch=null,invitations=[],busy=false,pendingAssistance=null;
function notice(text=''){$('operator-notice').textContent=text;$('operator-notice').hidden=!text;}
function element(tag,text){const e=document.createElement(tag);if(text!=null)e.textContent=text;return e;}
async function api(path,body,raw=false){const response=await fetch('/admin/study/'+path,{method:body===undefined?'GET':'POST',headers:{'X-Prometheus-Admin-Token':credential,'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',signal:AbortSignal.timeout(60000)});
  if(!response.ok)throw Error(response.status===401?'The operator credential was not accepted.':response.status===409?'The requested action is unavailable. Human collection may be closed, or the attempt/request has changed.':`The request failed (${response.status}). Check the same request before creating another.`);
  return response.status===204?null:raw?response.text():response.json();}
function download(name,text,type='application/json'){const url=URL.createObjectURL(new Blob([text],{type})),a=element('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function showInvitations(values){invitations=values;$('invitations').hidden=!values.some(i=>i.token);$('invitation-links').value=values.filter(i=>i.token).map(i=>`${i.session.id} ${i.session.scenario}: ${location.origin}/hri-study/#token=${i.token}`).join('\n');}
async function refresh(){const listing=await api(`attempts?mode=${$('dataset-mode').value}&page=${page}&size=50`);$('attempt-rows').replaceChildren();$('listing-count').textContent=`${listing.total} attempt(s). Page ${page+1}. Replacements retain participant identity and task.`;
  $('previous-page').disabled=page===0;$('next-page').disabled=(page+1)*50>=listing.total;
  for(const row of listing.attempts){const session=row.session,tr=element('tr');
    for(const value of [session.id,session.scenario,session.stage,session.finishReason??'—',row.agreements,row.unresolved,row.assistance])tr.append(element('td',String(value)));
    const actions=element('td'),assist=element('button','Record help'),replace=element('button','Replace attempt'),revoke=element('button','Revoke');
    for(const b of [assist,replace,revoke]){b.className='secondary';b.type='button';actions.append(b);}assist.addEventListener('click',()=>{selected=session;pendingAssistance=null;$('selected-attempt').textContent=session.id;$('assistance-panel').hidden=false;$('assistance-note').focus();});
    replace.disabled=session.stage==='REPLACED';replace.addEventListener('click',async()=>{if(busy)return;busy=true;try{showInvitations([await api('attempts',{mode:session.mode,predecessor:session.id})]);notice('Replacement created with the same participant identity and task. Save its invitation.');await refresh();}catch(error){notice(error.message);}finally{busy=false;}});
    revoke.disabled=['COMPLETE','WITHDRAWN','REPLACED','REVOKED'].includes(session.stage);revoke.addEventListener('click',async()=>{try{await api(`attempts/${session.id}/revoke`,{});notice('Attempt revoked. Its record is retained.');await refresh();}catch(error){notice(error.message);}});
    tr.append(actions);$('attempt-rows').append(tr);
  }
}
async function provision(retry=false){if(busy)return;busy=true;$('provision').disabled=true;notice();
  if(!retry)lastBatch={requestId:crypto.randomUUID(),mode:$('dataset-mode').value,count:Number($('batch-count').value)};
  try{const result=await api('batches',lastBatch);showInvitations(result.invitations);notice(result.replay?'This batch already exists. Its credentials were returned only on creation. Use explicit replacements for lost invitations.':'Invitations created. Save the invitation file before leaving this page.');$('retry-batch').hidden=true;await refresh();}
  catch(error){notice(error.message);$('retry-batch').hidden=false;}finally{busy=false;$('provision').disabled=false;}}
$('operator-login').addEventListener('submit',async event=>{event.preventDefault();credential=$('admin-credential').value;$('admin-credential').value='';try{await refresh();$('operations').hidden=false;notice();}catch(error){credential='';$('operations').hidden=true;notice(error.message);}});
$('batch-form').addEventListener('submit',event=>{event.preventDefault();void provision();});$('retry-batch').addEventListener('click',()=>void provision(true));
$('dataset-mode').addEventListener('change',()=>{page=0;void refresh().catch(error=>notice(error.message));});$('refresh-attempts').addEventListener('click',()=>void refresh().catch(error=>notice(error.message)));
$('previous-page').addEventListener('click',()=>{page--;void refresh().catch(error=>notice(error.message));});$('next-page').addEventListener('click',()=>{page++;void refresh().catch(error=>notice(error.message));});
$('save-invitations').addEventListener('click',()=>download('study-invitations.json',JSON.stringify(invitations,null,2)));
for(const format of ['json','csv'])$('export-'+format).addEventListener('click',async()=>{try{const mode=$('dataset-mode').value;const value=await api((format==='json'?'export':'assessments.csv')+'?mode='+mode,undefined,format==='csv');download(`study-${mode.toLowerCase()}.${format}`,format==='json'?JSON.stringify(value,null,2):value,format==='csv'?'text/csv':'application/json');notice('Dataset exported, including failed and replacement attempts.');}catch(error){notice(error.message);}});
$('assistance-form').addEventListener('submit',async event=>{event.preventDefault();if(!selected||busy)return;busy=true;
  const value={kind:$('assistance-kind').value,note:$('assistance-note').value};
  if(pendingAssistance&&(pendingAssistance.kind!==value.kind||pendingAssistance.note!==value.note)){notice('Retry the unchanged assistance note to resolve its saved status first.');busy=false;return;}
  pendingAssistance??={id:crypto.randomUUID(),...value};
  try{await api(`attempts/${selected.id}/assistance`,pendingAssistance);pendingAssistance=null;$('assistance-note').value='';notice('Assistance recorded separately from the conversation.');await refresh();}catch(error){notice(error.message);}finally{busy=false;}});
