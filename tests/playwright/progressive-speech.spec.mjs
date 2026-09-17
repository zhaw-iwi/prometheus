import { expect, test } from "@playwright/test";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

// Real HTTP chunks, real MP3 decoder, real HTMLAudioElement. No media mocks.
let server, origin, held, requests;
const bytes = await readFile(new URL("../fixtures/needforspeed/progressive-speech.mp3", import.meta.url));
test.beforeEach(async () => {
  held = new Set(); requests = [];
  server = createServer(async (request, response) => {
    if (request.url === "/speech") {
      requests.push({ method: request.method, access: request.headers["x-demo-access-code"] });
      response.writeHead(200, { "Content-Type": "audio/mpeg", "Cache-Control": "no-store" });
      response.write(bytes.subarray(0, Math.floor(bytes.length / 2)));
      held.add(response);
      response.on("close", () => held.delete(response));
    } else if (request.url === "/release") {
      for (const stream of held) stream.end(bytes.subarray(Math.floor(bytes.length / 2)));
      response.end("released");
    } else if (["/speech/progressive.js", "/speech/playback.js"].includes(request.url)) {
      response.writeHead(200, { "Content-Type": "text/javascript" });
      response.end(await readFile(new URL(`../../src/main/resources/public${request.url}`, import.meta.url)));
    } else {
      response.writeHead(200, { "Content-Type": "text/html" });
      response.end(`<!doctype html><title>Progressive speech verification</title>
        <button id="start">Start</button><button id="stop">Stop</button><output id="status">Ready</output><audio controls></audio>
        <script type="module">
        import {createSpeechAudio} from '/speech/progressive.js';
        import {BehaviourSpeechPlaybackQueue} from '/speech/playback.js';
        const audio = document.querySelector('audio');
        window.stages = []; window.gates = [];
        window.queue = new BehaviourSpeechPlaybackQueue({
          synthesize: async (item, signal) => createSpeechAudio(await fetch('/speech', {method:'POST', headers:{'X-Demo-Access-Code':'SYNTHETIC'},signal}),
            {signal, onStage:s=>stages.push(s), MediaSourceClass: location.search ? undefined : MediaSource}),
          play: (resource,item,signal,playing) => new Promise((resolve,reject)=>{
            const done = action => { audio.onended = audio.onerror = audio.onplaying = null; signal.removeEventListener('abort', stop); action(); };
            const stop = () => done(()=>reject(new DOMException('Stopped','AbortError')));
            signal.addEventListener('abort',stop,{once:true});
            audio.onplaying=()=>{stages.push('playing');playing();};
            audio.onended=()=>done(resolve); audio.onerror=()=>done(()=>reject(new Error('decode')));
            resource.done.catch(error=>done(()=>reject(error)));
            resource.attach(audio).then(()=>audio.play()).catch(error=>done(()=>reject(error)));
          }),
          releaseResource: resource => {audio.pause();audio.removeAttribute('src');audio.load();resource?.dispose();},
          setInputEnabled: enabled => gates.push(enabled),
          onStatus: status => document.querySelector('output').textContent=status.state,
        });
        document.querySelector('#start').onclick=()=>queue.enqueue({eventId:'fixture-event',speech:'I would like to draw tomorrow after breakfast.',delivery:'live'});
        document.querySelector('#stop').onclick=()=>queue.stop();
        </script>`);
    }
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
});
test.afterEach(async () => {
  for (const response of held) response.destroy();
  server.closeAllConnections();
  await new Promise(resolve => server.close(resolve));
});

test("native MP3 playback advances before the HTTP tail is released", async ({ page }) => {
  await page.goto(origin);
  await page.locator("#start").click();
  await expect(page.locator("output")).toHaveText("speaking");
  await expect.poll(() => page.locator("audio").evaluate(audio => audio.currentTime)).toBeGreaterThan(0.2);
  expect(held.size).toBe(1);
  expect(await page.evaluate(() => stages)).toContain("playing");
  expect(await page.evaluate(() => stages)).not.toContain("audio_downloaded");
  expect(requests).toEqual([{ method: "POST", access: "SYNTHETIC" }]);
  await page.request.get(`${origin}/release`);
  await expect(page.locator("output")).toHaveText("completed", { timeout: 15000 });
  expect(await page.evaluate(() => gates)).toEqual([false, true]);
});

test("native Stop cancels a withheld stream and reopens input", async ({ page }) => {
  await page.goto(origin); await page.locator("#start").click();
  await expect(page.locator("output")).toHaveText("speaking");
  await page.locator("#stop").click();
  await expect(page.locator("output")).toHaveText("stopped");
  await expect.poll(() => held.size).toBe(0);
  expect(await page.evaluate(() => gates)).toEqual([false, true]);
  expect(await page.evaluate(() => queue.snapshot().skipped)).toEqual(["fixture-event"]);
});

test("unsupported MSE buffers once and then decodes the same MP3", async ({ page }) => {
  await page.addInitScript(() => { window.MediaSource = undefined; });
  await page.goto(`${origin}/?buffered`); await page.locator("#start").click();
  await expect.poll(() => held.size).toBe(1);
  await expect(page.locator("output")).toHaveText("loading");
  expect(await page.locator("audio").evaluate(audio => audio.currentTime)).toBe(0);
  await page.request.get(`${origin}/release`);
  await expect.poll(() => page.locator("audio").evaluate(audio => audio.currentTime)).toBeGreaterThan(0.2);
  expect(requests).toHaveLength(1);
  expect(await page.evaluate(() => stages.indexOf("audio_downloaded") < stages.indexOf("playing"))).toBe(true);
  await page.locator("#stop").click();
});
