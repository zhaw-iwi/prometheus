import { test, expect } from "@playwright/test";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

let server, origin, held, requests;
const pcmType = "audio/pcm;rate=24000;channels=1;encoding=s16le";
const tone = Buffer.alloc(24000); // Half a second of deterministic 440 Hz mono PCM.
for (let sample = 0; sample < tone.length / 2; sample++) tone.writeInt16LE(Math.round(5000 * Math.sin(sample * Math.PI * 2 * 440 / 24000)), sample * 2);

test.beforeEach(async () => {
  held = new Set(); requests = 0;
  server = createServer(async (request, response) => {
    const path = new URL(request.url, "http://localhost").pathname;
    if (path === "/audio") {
      requests++;
      response.writeHead(200, { "Content-Type": pcmType });
      response.write(tone.subarray(0, 3)); // Deliberately split one sample.
      response.write(tone.subarray(3));
      held.add(response); response.on("close", () => held.delete(response));
    } else if (path === "/release") {
      for (const pending of held) pending.end(tone);
      response.end("ok");
    } else if (path === "/broken-tail") {
      for (const pending of held) pending.end(Buffer.from([1]));
      response.end("ok");
    } else if (["pcm.js", "pcm-worklet.js", "pcm-buffer.js", "playback.js"].some(file => path === `/speech/${file}`)) {
      response.writeHead(200, { "Content-Type": "text/javascript" });
      response.end(await readFile(new URL(`../../src/main/resources/public${path}`, import.meta.url)));
    } else {
      response.writeHead(200, { "Content-Type": "text/html" });
      response.end(`<!doctype html><button id="start">Start</button><button id="stop">Stop</button><output>idle</output>
      <script type="module">
      import {preparePcmSpeech} from '/speech/pcm.js';
      import {BehaviourSpeechPlaybackQueue} from '/speech/playback.js';
      window.stages=[];window.metrics=[];window.gates=[];
      window.queue=new BehaviourSpeechPlaybackQueue({
        synthesize:async(item,signal)=>{
          const prepared=await preparePcmSpeech({signal,onStage:s=>stages.push(s),onMetrics:m=>metrics.push(m)});
          if(!prepared.resource) throw new Error('Native PCM unavailable: '+prepared.fallbackReason);
          window.resource=prepared.resource;
          resource.setResponse(await fetch('/audio',{method:'POST',signal}));
          return resource;
        },
        play:(resource,item,signal,playing)=>resource.play(()=>{stages.push('playing');playing();}),
        releaseResource:resource=>resource?.dispose(),
        setInputEnabled:enabled=>gates.push(enabled),
        onStatus:s=>{document.querySelector('output').textContent=s.state;window.lastStatus=s;}
      });
      document.querySelector('#start').onclick=()=>queue.enqueue({eventId:'fixture',speech:'Fixture',delivery:'live'});
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

test("native PCM renderer consumes samples before EOF and drains before reopening input", async ({ page }) => {
  await page.goto(origin); await page.locator("#start").click();
  await expect(page.locator("output")).toHaveText("speaking");
  expect(held.size).toBe(1);
  expect(requests).toBe(1);
  expect(await page.evaluate(() => stages)).toEqual(["audio_first_byte", "playing"]);
  expect(await page.evaluate(() => gates)).toEqual([false]);
  expect(await page.evaluate(() => metrics.find(value => value.playbackStartSource)?.playbackStartSource)).toBe("pcm_renderer");
  await page.request.get(`${origin}/release`);
  await expect(page.locator("output")).toHaveText("completed");
  expect(await page.evaluate(() => gates)).toEqual([false, true]);
  expect(await page.evaluate(() => stages)).toContain("audio_downloaded");
});

test("native PCM Stop cancels the held response and never plays a queued reply", async ({ page }) => {
  await page.goto(origin); await page.locator("#start").click();
  await expect(page.locator("output")).toHaveText("speaking");
  await page.evaluate(() => queue.enqueue({ eventId: "queued", speech: "Queued", delivery: "live" }));
  await page.locator("#stop").click();
  await expect(page.locator("output")).toHaveText("stopped");
  await expect.poll(() => held.size).toBe(0);
  expect(requests).toBe(1);
  expect(await page.evaluate(() => gates)).toEqual([false, true]);
  expect(await page.evaluate(() => queue.snapshot().completed)).toEqual([]);
});

test("native PCM rejects a truncated sample after playback without resynthesis or replay", async ({ page }) => {
  await page.goto(origin); await page.locator("#start").click();
  await expect(page.locator("output")).toHaveText("speaking");
  await page.request.get(`${origin}/broken-tail`);
  await expect(page.locator("output")).toHaveText("failed");
  expect(requests).toBe(1);
  expect(await page.evaluate(() => stages.filter(stage => stage === "playing").length)).toBe(1);
  expect(await page.evaluate(() => gates)).toEqual([false, true]);
});
