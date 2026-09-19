import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

// Browser/database smoke tests only. No forwarding, external provider or quality claims.
const audio = await readFile(new URL("../fixtures/needforspeed/progressive-speech.mp3", import.meta.url));
const port = Number(process.env.NFS_STUB_PORT || 8091);
const server = createServer(async (request, response) => {
  if (request.method !== "POST") { response.writeHead(404); response.end(); return; }
  let data = "";
  for await (const chunk of request) {
    data += chunk;
    if (data.length > 1_000_000) { response.writeHead(413); response.end(); return; }
  }
  if (request.url === "/v1/audio/speech") {
    response.writeHead(200, { "Content-Type": "audio/mpeg" }); response.end(audio); return;
  }
  if (request.url !== "/v1/chat/completions") { response.writeHead(404); response.end(); return; }
  try {
    const payload = JSON.parse(data), format = payload.response_format;
    let result;
    if (format?.type === "json_schema") {
      result = JSON.stringify(Object.fromEntries(Object.keys(format.json_schema.schema.properties).map(id => [id, false])));
    } else if (format?.type === "json_object") result = JSON.stringify({ speech: "Synthetic fixture response.", nonVerbal: { gesture: "NONE" } });
    else if (payload.messages.some(m => /true.*false|false.*true/i.test(m.content))) result = "false";
    else if (payload.messages.some(m => /JSON/.test(m.content))) result = "{}";
    else result = "Synthetic fixture response.";
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(JSON.stringify({ choices: [{ finish_reason: "stop", message: { content: result } }] }));
  } catch (_) { response.writeHead(400); response.end(); }
});
server.listen(port, "127.0.0.1", () => process.stdout.write(`Synthetic provider listening on 127.0.0.1:${server.address().port}\n`));
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => { server.closeAllConnections(); server.close(); });
