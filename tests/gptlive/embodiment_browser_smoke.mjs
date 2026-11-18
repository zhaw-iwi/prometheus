import {chromium,expect} from '@playwright/test';
import {installOfflineUiAssets} from '../playwright/fixtures/offline-ui-assets.mjs';
import path from 'node:path';

// Only reads the actual scoped app/history created by the explicitly paid,
// disposable-local-database journey. No model/API response is mocked here.
export async function inspectEmbodiment({base,agentId,code,output}) {
  const browser=await chromium.launch({headless:true});
  try {
    const context=await browser.newContext({viewport:{width:1440,height:1100},reducedMotion:'reduce'});
    await installOfflineUiAssets(context);
    await context.route(/https:\/\/.*(?:tensorflow|face-api)/,route=>route.abort());
    const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    await page.goto(`${base}/valerian/?agentId=${agentId}`);
    await page.getByTestId('access-code-input').fill(code);await page.getByTestId('submit-access-code').click();
    await expect(page.getByTestId('speech-preview')).not.toHaveText('-');
    const speech=await page.getByTestId('speech-preview').textContent();
    const latest=await page.getByTestId('latest-behaviour-event').textContent();
    await page.getByRole('button',{name:'Robot',exact:true}).click();
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-ready','true');
    await expect(page.getByTestId('embodiment-stage')).toHaveAttribute('data-animating','false');
    await expect(page.getByTestId('speech-preview')).toHaveText(speech);
    await expect(page.getByTestId('latest-behaviour-event')).toHaveText(latest);
    const summary=await page.locator('#embodiment_summary').textContent();
    await page.locator('[data-column-panel="behaviour"]').screenshot({path:path.join(path.dirname(output),'provider-embodiment.png')});
    await page.getByRole('button',{name:'Details',exact:true}).click();
    await expect(page.getByTestId('gesture-value')).toBeVisible();expect(errors).toEqual([]);
    return {passed:true,canonicalSpeechPreserved:true,canonicalLatestEventPreserved:true,historyDidNotAnimate:true,summary,pageErrors:errors};
  } finally {await browser.close();}
}
