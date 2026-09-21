# -*- coding: utf-8 -*-
# 假 Modbus-RTU 从站（TCP 承载），用于模拟"中转卡死"：
#   数据口 DATA_PORT：接收 RTU 请求并回 RTU 应答
#   控制口 DATA_PORT+1：发送 stall / resume 切换"卡死"（只收不回）
import socket, threading, sys

STALL = threading.Event()
DATA_PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8901
CTRL_PORT = DATA_PORT + 1


def crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])


def handle(conn, addr):
    buf = b""
    try:
        while True:
            chunk = conn.recv(256)
            if not chunk:
                break
            if STALL.is_set():
                buf = b""
                continue
            buf += chunk
            while len(buf) >= 8:
                req, buf = buf[:8], buf[8:]
                a, f = req[0], req[1]
                start = (req[2] << 8) | req[3]
                cnt = (req[4] << 8) | req[5]
                if f != 3:
                    body = bytes([a, f | 0x80, 0x01])
                    conn.sendall(body + crc16(body))
                    continue
                data = b""
                for i in range(cnt):
                    reg = start + i
                    if reg == 0:
                        v = 600                   # 模拟量2（无符号）
                    elif reg == 1:
                        v = 200 + a * 10          # 模拟量1（有符号），按通道变化
                    else:
                        v = 0
                    data += bytes([(v >> 8) & 0xFF, v & 0xFF])
                body = bytes([a, 3, len(data)]) + data
                conn.sendall(body + crc16(body))
    except Exception:
        pass
    finally:
        try:
            conn.close()
        except Exception:
            pass


def control():
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", CTRL_PORT))
    s.listen(4)
    while True:
        c, _ = s.accept()
        try:
            cmd = c.recv(64).decode("utf-8", "replace").strip().lower()
            if cmd == "stall":
                STALL.set()
            elif cmd == "resume":
                STALL.clear()
            c.sendall(("state=" + ("stall" if STALL.is_set() else "normal")).encode())
        except Exception:
            pass
        finally:
            c.close()


def main():
    threading.Thread(target=control, daemon=True).start()
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", DATA_PORT))
    s.listen(8)
    print("fake modbus slave: data=%d ctrl=%d" % (DATA_PORT, CTRL_PORT), flush=True)
    while True:
        c, a = s.accept()
        threading.Thread(target=handle, args=(c, a), daemon=True).start()


if __name__ == "__main__":
    main()
