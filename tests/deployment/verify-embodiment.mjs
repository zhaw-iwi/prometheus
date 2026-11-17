// Read-only release verification. No production agent/access records or model calls.
import {chromium,expect} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';

const [base,output]=process.argv.slice(2);
if(!base||!output||new URL(base).protocol!=='https:')throw Error('Usage: node tests/deployment/verify-embodiment.mjs https://deployment output-directory');
await mkdir(output,{recursive:true});
const report={sourceRevision:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),base,assets:[],passed:false};
const assets=['index.html','script.js','embodiment/model.js','embodiment/robot.js','embodiment/view.js',
  'embodiment/view.css','embodiment/vendor/three.module.js','embodiment/vendor/three.core.js','embodiment/vendor/LICENSE.txt'];
const hash=value=>createHash('sha256').update(value.replaceAll('\r\n','\n')).digest('hex');
let browser;
try {
  const health=await fetch(new URL('/actuator/health',base),{signal:AbortSignal.timeout(30000)});
  expect(health.ok).toBe(true);report.health=(await health.json()).status;expect(report.health).toBe('UP');
  for(const file of assets) {
    const response=await fetch(new URL('/valerian/'+file,base),{signal:AbortSignal.timeout(30000)});
    expect(response.ok).toBe(true);
    const expected=execFileSync('git',['show',`HEAD:src/main/resources/public/valerian/${file}`],{encoding:'utf8',maxBuffer:4*1024*1024});
    const actual=hash(await response.text());expect(actual).toBe(hash(expected));report.assets.push({file,sha256:actual});
  }
  browser=await chromium.launch({headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1100},reducedMotion:'reduce'});
  // Camera and microphone are unused. Avoid fetching their optional ML libraries.
  await context.route(/https:\/\/.*(?:tensorflow|face-api)/,route=>route.abort());
  const writes=[],errors=[],page=await context.newPage();
  await context.route('**/*',async route=>{
    if(!['GET','HEAD'].includes(route.request().method())) {writes.push(route.request().method());return route.abort();}
    return route.fallback();
  });
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(new URL('/valerian/',base).href);
  await expect(page.getByTestId('access-screen')).toBeVisible();
  await page.evaluate(()=>{
    // A local synthetic plan exercises served rendering without accessing an agent.
    showCockpit(true);setTheme('dark',{persist:false});
    const plan={speech:'Deployment preview — no agent interaction.',nonVerbal:{gesture:'OPEN_QUESTION',
      facialExpression:{type:'warm_smile',intensity:0.65},gaze:{direction:'toward_user'},motion:{energy:0.4,stillness:0.7}},
      motion:{handSign:'scissor'},display:{text:'Synthetic preview'}};
    renderBehaviourPlan(plan,{delivery:'replay'});
    renderLatestEvent({type:'resp.behaviour_plan',payload:JSON.stringify(plan)});
  });
  await page.getByRole('button',{name:'Robot',exact:true}).click();
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
  await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
  await expect(page.getByTestId('speech-preview')).toHaveText('Deployment preview — no agent interaction.');
  await expect(page.locator('#embodiment_summary')).toContainText('right hand: scissor');
  await page.locator('[data-column-panel="behaviour"]').screenshot({path:path.join(output,'deployed-robot.png')});
  await page.getByRole('button',{name:'Details',exact:true}).click();await expect(page.getByTestId('gesture-value')).toBeVisible();
  expect(errors).toEqual([]);expect(writes).toEqual([]);
  report.browser={passed:true,pageErrors:errors,attemptedWrites:writes,syntheticClientPlan:true};report.passed=true;
} catch(error) {report.error=error.message;process.exitCode=1;}
finally {
  await browser?.close();await writeFile(path.join(output,'verification.json'),JSON.stringify(report,null,2));
  console.log(JSON.stringify(report));
}
