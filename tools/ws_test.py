# -*- coding: utf-8 -*-
"""极简 RFC6455 WebSocket 客户端（不依赖第三方库），用于自测 /api/ws。"""
import base64, json, os, socket, struct, time

HOST, PORT = "127.0.0.1", 8790


class WS:
    def __init__(self, host, port, path="/api/ws"):
        self.s = socket.create_connection((host, port), timeout=10)
        key = base64.b64encode(os.urandom(16)).decode()
        req = (
            f"GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\n"
            f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        )
        self.s.sendall(req.encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.s.recv(4096)
        head, _, rest = buf.partition(b"\r\n\r\n")
        assert b"101" in head.split(b"\r\n")[0], head[:200]
        self.buf = rest

    def _read(self, n):
        while len(self.buf) < n:
            chunk = self.s.recv(65536)
            if not chunk:
                raise ConnectionError("closed")
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def send(self, text):
        payload = text.encode()
        header = bytearray([0x81])
        n = len(payload)
        if n < 126:
            header.append(0x80 | n)
        elif n < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", n)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", n)
        mask = os.urandom(4)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        self.s.sendall(bytes(header) + masked)

    def recv(self, timeout=5):
        self.s.settimeout(timeout)
        while True:
            b0, b1 = self._read(2)
            opcode = b0 & 0x0F
            masked = b1 & 0x80
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            mk = self._read(4) if masked else b""
            data = self._read(n) if n else b""
            if masked:
                data = bytes(b ^ mk[i % 4] for i, b in enumerate(data))
            if opcode == 0x1:
                return data.decode()
            if opcode == 0x8:
                raise ConnectionError("server closed")
            if opcode == 0x9:
                self.s.sendall(b"\x8a\x80" + os.urandom(4))
            # 其它（pong/continuation）忽略

    def close(self):
        try:
            self.s.close()
        except Exception:
            pass


def main():
    ws = WS(HOST, PORT)
    hello = json.loads(ws.recv(5))
    print("[recv]", hello.get("type"), json.dumps(hello, ensure_ascii=False)[:220])

    ws.send(json.dumps({"action": "ping"}))
    r = json.loads(ws.recv(5))
    print("[recv]", r.get("type"), json.dumps(r, ensure_ascii=False)[:160])

    ws.send(json.dumps({"action": "snapshot"}))
    r = json.loads(ws.recv(5))
    print("[recv]", r.get("type"), "samples=", len(r.get("samples", [])))

    ws.send(json.dumps({"action": "status"}))
    r = json.loads(ws.recv(5))
    print("[recv]", r.get("type"), "rows=", r.get("rows"), "outbox=", r.get("outbox"))

    ws.send(json.dumps({"action": "poll_now"}))
    seen = {}
    end = time.time() + 13
    while time.time() < end:
        try:
            ev = json.loads(ws.recv(2))
        except socket.timeout:
            continue
        t = ev.get("type")
        seen[t] = seen.get(t, 0) + 1
        if t in ("notice", "round"):
            print("[recv]", t, json.dumps(ev, ensure_ascii=False)[:220])
    print("[summary] 事件计数:", seen)
    ws.close()


if __name__ == "__main__":
    main()
