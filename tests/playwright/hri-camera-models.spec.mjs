import {test,expect} from '@playwright/test';
import fs from 'node:fs/promises';
// Opt-in network smoke: real detector bundles/weights, generated frames, no physical webcam.
test('real detector assets load and process generated camera frames without uploading media',async({page,request},info)=>{
  test.setTimeout(180000);
  const admin={'X-Prometheus-Admin-Token':process.env.PROMETHEUS_ADMIN_TOKEN};
  const r=await request.post('/admin/study/attempts',{headers:admin,data:{mode:'SYNTHETIC',scenario:'guess'}});expect(r.ok()).toBeTruthy();const i=await r.json();
  expect(i.session.materialsVersion).toBe('hri-text-v3-draft');const headers={'X-Prometheus-Study-Token':i.token};
  const materials=JSON.parse(await fs.readFile('src/main/resources/hri-study/materials-v3.json','utf8'));
  for(const [path,data] of [['consent',{version:'synthetic-test-v1'}],['forms/pre',Object.fromEntries(materials.pre.map(x=>[x.id,x.kind==='rating7'?'4':'None']))],['forms/intention',{intention:'Synthetic model-load check'}],['forms/private',{private:'Synthetic test'}]])
    expect((await request.post('/api/study/'+path,{headers,data})).ok()).toBeTruthy();
  const requests=[],errors=[];page.on('request',r=>requests.push({url:r.url().split('?')[0],method:r.method(),bytes:r.postDataBuffer()?.length??0}));page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
    navigator.mediaDevices.getUserMedia=async constraints=>{
      if(constraints.audio!==false)throw Error('Microphone must remain off');
      const c=document.createElement('canvas');c.width=640;c.height=480;const ctx=c.getContext('2d');window.modelStream=c.captureStream(5);
      setInterval(()=>{ctx.fillStyle='#b7d1db';ctx.fillRect(0,0,640,480);},150);return window.modelStream;
    };
  });
  await page.goto('/hri-study/#token='+i.token);
  try{
    await expect(page.locator('#camera-status')).toContainText('all three detectors',{timeout:120000});
    await expect(page.locator('#detector-status')).toContainText('People: 0',{timeout:30000});
    await expect.poll(async()=>JSON.parse((await (await request.get('/api/study/activity',{headers})).json()).scene??'{}').humanCount,{timeout:15000}).toBe(0);
    const origin=new URL(page.url()).origin;expect(requests.filter(r=>!r.url.startsWith(origin)&&r.method!=='GET')).toEqual([]);expect(errors).toEqual([]);
    await page.screenshot({path:info.outputPath('real-models.png'),fullPage:true});
    await page.locator('#camera-stop').click();expect(await page.evaluate(()=>window.modelStream.getTracks().every(t=>t.readyState==='ended'))).toBe(true);
  }finally{await fs.writeFile(info.outputPath('model-network.json'),JSON.stringify({requests,errors},null,2));}
});
