// Actual pinned model inference on public test images. No mocked landmarks/provider calls.
import {createServer} from 'node:http';
import {readFileSync,writeFileSync,existsSync,mkdirSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {resolve,basename} from 'node:path';
import {chromium} from '@playwright/test';

const root=resolve('target/hand-interaction-detector'); mkdirSync(root,{recursive:true});
const manifestPath=process.argv.find(arg=>arg.startsWith('--manifest='))?.slice(11)||'tests/hand-interaction/detector-corpus.json';
const manifest=JSON.parse(readFileSync(manifestPath,'utf8'));
const artifacts=resolve(root,`run-${Date.now()}`);mkdirSync(artifacts);
writeFileSync(resolve(artifacts,'manifest.json'),JSON.stringify(manifest,null,2));
const selected=process.argv.find(arg=>arg.startsWith('--case='))?.slice(7);
if(selected){manifest.cases=manifest.cases.filter(c=>c.id===selected);if(!manifest.cases.length)throw new Error('Unknown corpus case');}
for (const source of manifest.sources) {
  const file=resolve(root,basename(source.file));
  if (!existsSync(file)) {
    const response=await fetch(source.url); if(!response.ok)throw new Error(`Fixture download failed: ${source.file}`);
    writeFileSync(file,Buffer.from(await response.arrayBuffer()));
  }
  if(createHash('sha256').update(readFileSync(file)).digest('hex')!==source.sha256)throw new Error(`Fixture digest changed: ${source.file}`);
}
const server=createServer((req,res)=>{
  const file=basename(new URL(req.url,'http://localhost').pathname);
  if(!file){res.setHeader('Content-Type','text/html');res.end('<!doctype html><canvas></canvas>');return;}
  const local=new Set(['named-signs.js','finger-pose.js','patterns.js']);
  const allowed=new Set([...manifest.sources.map(s=>s.file),...local]);
  if(!allowed.has(file)){res.writeHead(404);res.end();return;}
  const path=local.has(file)?'src/main/resources/public/valerian/hand/'+file:resolve(root,file);
  res.setHeader('Content-Type',file.endsWith('.wasm')?'application/wasm':/\.(m?js)$/.test(file)?'application/javascript':'application/octet-stream');
  res.end(readFileSync(path));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));
let browser;
try {
  browser=await chromium.launch({headless:true});const page=await browser.newPage();
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  const results=await page.evaluate(async manifest=>{
    const {GestureRecognizer}=await import('/vision_bundle.mjs');
    const {createNamedSignTracker}=await import('/named-signs.js');
    const output=[];
    for(const scenario of manifest.cases){
      const recognizer=await GestureRecognizer.createFromOptions({wasmLoaderPath:'/vision_wasm_internal.js',wasmBinaryPath:'/vision_wasm_internal.wasm'},
        {baseOptions:{modelAssetPath:'/gesture_recognizer.task'},runningMode:'VIDEO',numHands:2});
      const canvas=document.querySelector('canvas');canvas.width=640;canvas.height=480;
      const ctx=canvas.getContext('2d');ctx.fillStyle='#dddddd';ctx.fillRect(0,0,640,480);
      if(scenario.image){
        const image=new Image();image.src='/'+scenario.image;await image.decode();
        const [x,y,w,h]=scenario.crop??[0,0,1,1];
        const scale=Math.min(640/(w*image.width),480/(h*image.height));
        const dw=w*image.width*scale,dh=h*image.height*scale;
        ctx.save();ctx.translate(320,240);ctx.rotate((scenario.rotate??0)*Math.PI/180);if(scenario.mirror)ctx.scale(-1,1);
        ctx.drawImage(image,x*image.width,y*image.height,w*image.width,h*image.height,-dw/2,-dh/2,dw,dh);ctx.restore();
      }
      const tracker=createNamedSignTracker(scenario.id),frames=[];let stableAt=null;
      for(let i=0;i<manifest.frames;i++){
        const at=i*manifest.frameIntervalMs,started=performance.now();
        const result=recognizer.recognizeForVideo(canvas,at+1);
        const classified=tracker.observe(result,new Date(100000+at).toISOString(),manifest.threshold,manifest.poses===true);
        const signs=classified.observation.hands.filter(h=>h.sign).map(h=>h.sign).sort();
        const poseMatch = (wanted, hands, used = new Set(), position = 0) => position === wanted.length || hands.some((hand, index) =>
          !used.has(index) && (wanted[position].side === 'any' || wanted[position].side === hand.handedness)
          && Object.entries(wanted[position].states).every(([finger,state]) => hand.fingerStates[finger] === state)
          && poseMatch(wanted,hands,new Set([...used,index]),position+1));
        const match=scenario.expectedPose ? poseMatch(scenario.expectedPose,classified.pose.hands)
          : JSON.stringify(signs)===JSON.stringify([...scenario.expected].sort());
        const stable=scenario.expectedPose?classified.poseStable:classified.stable;
        if(stable&&match&&stableAt===null)stableAt=at;
        frames.push({at,ms:performance.now()-started,stable,match,hands:classified.observation.hands,pose:classified.pose,classes:result.gestures,
          ...(manifest.diagnostics ? {landmarks:result.landmarks,worldLandmarks:result.worldLandmarks} : {})});
      }
      recognizer.close();
      const passed=stableAt!==null&&stableAt<=manifest.stableDeadlineMs&&frames.every(f=>f.match||manifest.admissionOnly===true&&!f.stable);
      output.push({id:scenario.id,expected:scenario.expected??scenario.expectedPose,stableAt,passed,frames,image:canvas.toDataURL('image/png')});
    }
    return output;
  },manifest);
  for(const result of results){writeFileSync(resolve(artifacts,`${result.id}.png`),Buffer.from(result.image.split(',')[1],'base64'));delete result.image;}
  writeFileSync(resolve(artifacts,'results.json'),JSON.stringify({sdk:manifest.sdkVersion,threshold:manifest.threshold,results},null,2));
  console.log(`Artifacts: ${artifacts}`);
  for(const r of results)console.log(`${r.passed?'PASS':'FAIL'} ${r.id}: stable=${r.stableAt} ms; ${JSON.stringify(r.frames.at(-1).hands)}; classes=${JSON.stringify(r.frames.at(-1).classes)}`);
  if(results.some(r=>!r.passed))process.exitCode=1;
} finally {await browser?.close();await new Promise(done=>server.close(done));}
