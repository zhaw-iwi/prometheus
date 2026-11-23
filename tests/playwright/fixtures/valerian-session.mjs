// Valerian initializes asynchronously after window.load. Visible login controls
// alone do not mean their handlers are attached yet.
export async function openValerianSession(page, url, code) {
  await page.goto(url, {waitUntil:'domcontentloaded'});
  await page.waitForFunction(() => document.readyState === 'complete'
    && document.querySelector('[data-testid="send-text"]')?.disabled === true);
  await page.getByTestId('access-code-input').fill(code);
  const opened = page.waitForResponse(response => response.url().endsWith('/demo/session')
    && response.request().method() === 'POST');
  await page.getByTestId('submit-access-code').click();
  const response = await opened;
  if (!response.ok()) throw new Error('Cockpit access rejected: HTTP ' + response.status());
  await page.getByTestId('send-text').waitFor({state:'visible'});
}
