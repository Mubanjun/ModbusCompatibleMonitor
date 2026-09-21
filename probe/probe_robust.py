# -*- coding: utf-8 -*-
"""RS-QXZ-M 通信方式稳健探测：按预期长度精确收帧 + 重试"""
import sys, time, serial

def crc16(data: bytes) -> bytes:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])

def make(addr, func, start=0, count=0, payload=b""):
    if func in (3, 4, 6):
        body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF])
    elif func == 16:
        body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF, len(payload)]) + payload
    else:
        body = bytes([addr, func]) + payload
    return body + crc16(body)

def hexs(b): return " ".join(f"{x:02X}" for x in b)
def crc_ok(fr): return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]

def read_response(ser, wait=1.0):
    ser.timeout = wait
    head = ser.read(2)
    if len(head) < 2:
        return b""
    if head[1] & 0x80:
        return head + ser.read(3)
    bc = ser.read(1)
    if not bc:
        return head
    n = bc[0]
    rest = b""
    need = n + 2
    t0 = time.time()
    while len(rest) < need and time.time() - t0 < wait:
        c = ser.read(need - len(rest))
        if not c: break
        rest += c
    return head + bc + rest

def xact(ser, tx, wait=1.0):
    ser.reset_input_buffer()
    ser.write(tx); ser.flush()
    t0 = time.time()
    rx = read_response(ser, wait)
    return rx, (time.time() - t0) * 1000

def classify(rx):
    if not rx: return "TIMEOUT"
    if len(rx) >= 3 and (rx[1] & 0x80): return f"EXC-{rx[2]:02X}"
    return "OK" if crc_ok(rx) else "CRC_FAIL"

def test(ser, name, tx, retries=2, wait=1.0):
    for i in range(retries + 1):
        rx, dt = xact(ser, tx, wait)
        st = classify(rx)
        if st == "OK" or st.startswith("EXC"):
            print(f"{name:40s} TX={hexs(tx):26s} RX({len(rx):3d})={hexs(rx):66s} {st} {dt:5.0f}ms")
            return rx
        time.sleep(0.2)
    print(f"{name:40s} TX={hexs(tx):26s} RX({len(rx):3d})={hexs(rx):66s} {st} {dt:5.0f}ms (retries exhausted)")
    return rx

def main():
    port = sys.argv[1] if len(sys.argv) > 1 else "COM3"
    baud = int(sys.argv[2]) if len(sys.argv) > 2 else 9600
    addr = int(sys.argv[3]) if len(sys.argv) > 3 else 1
    ser = serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=1.0)
    tests = [
        ("FC03 r0 n1", make(addr, 3, 0, 1)),
        ("FC03 r0 n2", make(addr, 3, 0, 2)),
        ("FC03 r0 n4", make(addr, 3, 0, 4)),
        ("FC03 r0 n8", make(addr, 3, 0, 8)),
        ("FC03 r0 n40", make(addr, 3, 0, 40)),
        ("FC03 r0 n64", make(addr, 3, 0, 64)),
        ("FC04 r0 n2", make(addr, 4, 0, 2)),
        ("FC03 r64 n4", make(addr, 3, 64, 4)),
        ("FC03 r64 n8", make(addr, 3, 64, 8)),
        ("FC03 r64 n80", make(addr, 3, 64, 80)),
        ("FC03 r100 n4", make(addr, 3, 100, 4)),
        ("FC03 r188 n4", make(addr, 3, 188, 4)),
        ("FC03 r300 n4", make(addr, 3, 300, 4)),
        ("FC03 r556 n4", make(addr, 3, 556, 4)),
        ("FC03 r684 n6", make(addr, 3, 684, 6)),
        ("FC03 r690 n6", make(addr, 3, 690, 6)),
        ("FC03 r696 n8", make(addr, 3, 696, 8)),
        ("FC03 r1000 n2", make(addr, 3, 1000, 2)),
        ("FC06 r696 w0", make(addr, 6, 696, 0)),
    ]
    for name, tx in tests:
        test(ser, name, tx)
        time.sleep(0.15)
    ser.close()

if __name__ == "__main__":
    main()
