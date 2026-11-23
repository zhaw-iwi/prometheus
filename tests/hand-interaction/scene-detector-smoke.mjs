import {createServer} from 'node:http';
import {readFileSync,writeFileSync,existsSync,mkdirSync} from 'node:fs';
import {resolve,basename} from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {chromium} from 'playwright';
const manifest=JSON.parse(readFileSync('tests/hand-interaction/scene-corpus.json','utf8'));
const root=resolve('target/hand-interaction-detector/objects'),artifacts=resolve(root,'run-'+Date.now());mkdirSync(artifacts,{recursive:true});
const hash=data=>createHash('sha256').update(data).digest('hex');
async function source(asset){const path=resolve(root,asset.file);if(!existsSync(path)){const response=await fetch(asset.url);if(!response.ok)throw new Error(`HTTP ${response.status}: ${asset.file}`);writeFileSync(path,Buffer.from(await response.arrayBuffer()));}if(hash(readFileSync(path))!==asset.sha256)throw new Error('Hash mismatch: '+asset.file);}
await source(manifest.archive);
for(const asset of manifest.sources){
  if(asset.archivePath&&!existsSync(resolve(root,asset.file))){
    const script="import zipfile,sys,pathlib;root=pathlib.Path(sys.argv[1]);name=sys.argv[3];assert pathlib.Path(name).name==name;z=zipfile.ZipFile(root/'coco128.zip');(root/name).write_bytes(z.read(sys.argv[2]))";
    const extracted=spawnSync('python',['-B','-c',script,root,asset.archivePath,asset.file],{encoding:'utf8'});if(extracted.status!==0)throw new Error('Could not extract public fixture: '+asset.file);
  }
  await source(asset);
}
const allowed=new Set(manifest.sources.map(s=>s.file));allowed.add('targets.js');
const server=createServer((req,res)=>{const name=basename(new URL(req.url,'http://local').pathname);
  if(!name){res.setHeader('Content-Type','text/html');res.end('<script src="/tf.min.js"></script><script src="/coco-ssd.js"></script><canvas></canvas>');return;}
  if(!allowed.has(name)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':name.endsWith('.json')?'application/json':'application/octet-stream');
  res.end(readFileSync(name==='targets.js'?'src/main/resources/public/valerian/hand/targets.js':resolve(root,name)));
});
await new Promise(done=>server.listen(0,'127.0.0.1',done));let browser;
try{
  browser=await chromium.launch({headless:true});const page=await browser.newPage();await page.goto('http://127.0.0.1:'+server.address().port+'/');
  const results=await page.evaluate(async manifest=>{
    const {createTargetTracker}=await import('/targets.js'),model=await cocoSsd.load({base:'lite_mobilenet_v2',modelUrl:'/model.json'});
    const canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d'),results=[];
    for(const scenario of manifest.cases){
      let image=null;if(scenario.image){image=new Image();image.src='/'+scenario.image;await image.decode();}
      canvas.width=image?.width||640;canvas.height=image?.height||480;const tracker=createTargetTracker(scenario.id),frames=[];
      for(let i=0;i<manifest.frames;i++){
        ctx.fillStyle='#ddd';ctx.fillRect(0,0,canvas.width,canvas.height);if(image)ctx.drawImage(image,scenario.motion?[-3,-2,0,2,3,1,0][i]:0,0);
        const start=performance.now(),predictions=await model.detect(canvas,20,manifest.threshold);
        const snapshot=tracker.observe(predictions,canvas.width,canvas.height,{id:'camera-test',revision:1,space:'camera_image',mirrored:false},new Date(100000+i*manifest.frameIntervalMs).toISOString());
        const classes=[...new Set(snapshot.targets.map(t=>t.type))].sort();const passed=scenario.expected.every(c=>classes.includes(c))&&(scenario.expected.length!==0||classes.length===0);
        frames.push({at:i*manifest.frameIntervalMs,ms:performance.now()-start,passed,predictions,snapshot});
      }
      for(const p of frames.at(-1).predictions.filter(p=>['person','cup','bottle','book'].includes(p.class))){ctx.strokeStyle='#d7631c';ctx.lineWidth=3;ctx.strokeRect(...p.bbox);ctx.fillStyle='#d7631c';ctx.font='16px sans-serif';ctx.fillText(p.class+' '+p.score.toFixed(2),p.bbox[0],Math.max(18,p.bbox[1]));}
      results.push({id:scenario.id,expected:scenario.expected,passed:frames.every(f=>f.passed),frames,image:canvas.toDataURL('image/png')});
    }model.dispose();return results;
  },manifest);
  for(const result of results){writeFileSync(resolve(artifacts,result.id+'.png'),Buffer.from(result.image.split(',')[1],'base64'));delete result.image;}
  writeFileSync(resolve(artifacts,'results.json'),JSON.stringify({sdk:manifest.sdkVersion,threshold:manifest.threshold,semanticReview:manifest.semanticReview,results},null,2));console.log('Artifacts: '+artifacts);
  for(const r of results)console.log(`CLASS-OUTPUT ${r.passed?'PASS':'FAIL'} ${r.id}: ${JSON.stringify(r.frames.at(-1).snapshot.targets.map(t=>t.type))}`);
  for(const [name,review] of Object.entries(manifest.semanticReview||{}))console.log(`SEPARATE REVIEW ${review.status} ${name}: ${review.note}`);
  if(results.some(r=>!r.passed))process.exitCode=1;
}finally{await browser?.close();await new Promise(done=>server.close(done));}
