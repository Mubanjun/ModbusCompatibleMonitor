#!/usr/bin/env python3
"""本地 crates.io 稀疏索引 + 下载 反向代理（HTTP -> 上游 HTTPS）

背景：本机 Windows schannel 不可用，cargo/git/curl 的 TLS 全部失败，
但 Python(OpenSSL) 可正常访问外网。此代理用 Python 转发，
让 cargo 通过 http://127.0.0.1:8787 使用 crates.io，规避 schannel。

用法：
    python tools/local_crates_proxy.py [port]
"""
import sys, json, urllib.request, urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

UPSTREAM_INDEX = "http://mirrors.cernet.edu.cn/crates.io-index/"
UPSTREAM_CRATE = "http://static.crates.io/crates/"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8787
UA = {"User-Agent": "cargo-local-proxy/1.0"}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + fmt % args + "\n")

    def _send(self, code, body: bytes, ctype="application/octet-stream"):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?", 1)[0]
        try:
            if path == "/index/config.json":
                body = json.dumps({
                    "dl": "http://127.0.0.1:%d/dl" % PORT,
                    "api": None,
                }).encode()
                return self._send(200, body, "application/json")
            if path.startswith("/index/"):
                rel = path[len("/index/"):]
                body = fetch(UPSTREAM_INDEX + rel)
                return self._send(200, body, "text/plain; charset=utf-8")
            if path.startswith("/dl/"):
                # /dl/{crate}/{version}/download
                parts = path[len("/dl/"):].split("/")
                if len(parts) < 3:
                    return self._send(400, b"bad dl path")
                crate, version = parts[0], parts[1]
                url = "%s%s/%s-%s.crate" % (UPSTREAM_CRATE, crate, crate, version)
                body = fetch(url)
                return self._send(200, body, "application/gzip")
            return self._send(404, b"not found")
        except urllib.error.HTTPError as e:
            return self._send(e.code, ("upstream %s" % e).encode())
        except Exception as e:
            return self._send(502, ("proxy error: %r" % e).encode())


if __name__ == "__main__":
    srv = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    sys.stderr.write("listening on http://127.0.0.1:%d\n" % PORT)
    srv.serve_forever()
