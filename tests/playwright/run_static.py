"""Run browser-only fixtures against local assets, never application.properties."""
import functools
import http.server
import os
from pathlib import Path
import subprocess
import sys
import threading

root = Path(__file__).resolve().parents[2]

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass

server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(
    QuietHandler, directory=str(root / "src/main/resources/public")))
thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()
try:
    env = {**os.environ, "PROMETHEUS_SKIP_WEBSERVER": "true",
           "PROMETHEUS_BASE_URL": f"http://127.0.0.1:{server.server_port}"}
    # This runner cannot run the separate real-database journey.
    env.pop("PROMETHEUS_ADMIN_TOKEN", None)
    env.pop("PROMETHEUS_LIVE_EXPECT_ENABLED", None)
    result = subprocess.run(["node", "node_modules/@playwright/test/cli.js", "test",
                             "--config=playwright.config.mjs", *sys.argv[1:]], cwd=root, env=env)
finally:
    server.shutdown()
    server.server_close()
sys.exit(result.returncode)
