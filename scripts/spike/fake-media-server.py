#!/usr/bin/env python3
"""Fake Anki media server for emulator tests (stdlib only). Mimics the real one: Bearer token, 401/404/405/400,
/healthz without token, ETag. Usage: fake-media-server.py <dir> [port=8099] ; token from env FAKE_TOKEN (default testtoken).
Never use the real server or token in tests."""
import hashlib, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import unquote

ROOT = os.path.realpath(sys.argv[1])
PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 8099
TOKEN = os.environ.get("FAKE_TOKEN", "testtoken")
TYPES = {".png": "image/png", ".jpg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp"}


class H(BaseHTTPRequestHandler):
    def _send(self, code, body=b"", ctype="text/plain", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def _handle(self):
        if self.path == "/healthz":
            return self._send(200, b"ok")
        if self.headers.get("Authorization") != f"Bearer {TOKEN}":
            return self._send(401, b"unauthorized")
        name = unquote(self.path.split("?")[0].lstrip("/"))
        if not name or "/" in name or name.startswith("."):
            return self._send(400, b"bad path")
        path = os.path.realpath(os.path.join(ROOT, name))
        if os.path.dirname(path) != ROOT or not os.path.isfile(path):
            return self._send(404, b"not found")
        data = open(path, "rb").read()
        etag = '"%s"' % hashlib.md5(data).hexdigest()
        if self.headers.get("If-None-Match") == etag:
            return self._send(304, b"", extra={"ETag": etag})
        ctype = TYPES.get(os.path.splitext(name)[1].lower(), "application/octet-stream")
        self._send(200, data, ctype, {"ETag": etag, "Cache-Control": "private, max-age=86400"})

    do_GET = do_HEAD = _handle

    def _no(self):
        self._send(405, b"method not allowed")

    do_POST = do_PUT = do_DELETE = _no

    def log_message(self, fmt, *a):
        sys.stderr.write("fake-media %s\n" % (fmt % a))


ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
