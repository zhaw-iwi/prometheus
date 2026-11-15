import { test, expect } from './fixtures/offline-ui-assets.mjs';

test('study preparation is clearly closed and text only', async ({page}, info) => {
  const media = [];
  page.on('request', r => { if (/\/(live|speech|transcription)\//.test(r.url())) media.push(r.url()); });
  await page.goto('/hri-study/');
  await expect(page.getByRole('heading', {name: 'Coordinating an activity together'})).toBeVisible();
  await expect(page.getByText('Preparation in progress')).toBeVisible();
  await expect(page.locator('body')).toContainText('Participant sessions are not open yet');
  expect(media).toEqual([]);
  for (const width of [1440, 390]) {
    await page.setViewportSize({width, height: 900});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({path: info.outputPath(`study-${width}.png`), fullPage: true});
  }
});
