# -*- coding: utf-8 -*-
"""干净探测：写后固定等待再全量读取，重复性验证"""
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
def classify(rx):
    if not rx: return "TIMEOUT"
    if len(rx) >= 3 and (rx[1] & 0x80): return f"EXC-{rx[2]:02X}"
    return "OK" if crc_ok(rx) else "CRC_FAIL"

def query(ser, tx, settle=0.35):
    ser.reset_input_buffer(); ser.reset_output_buffer()
    t0 = time.time(); ser.write(tx); ser.flush()
    time.sleep(settle)
    rx = ser.read(512)
    return rx, (time.time() - t0) * 1000

def main():
    port, baud = "COM3", 9600
    if len(sys.argv) > 1: baud = int(sys.argv[1])
    ser = serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.1)
    print("== A. 重复性：addr=1 FC03 r0 n2 x10 ==")
    tx = make(1, 3, 0, 2)
    for i in range(10):
        rx, dt = query(ser, tx)
        print(f"  #{i+1:2d} RX({len(rx):3d})={hexs(rx):40s} {classify(rx)} {dt:5.0f}ms")
        time.sleep(0.2)
    print("\n== B. 地址扫描 1..32：FC03 r0 n2 ==")
    hits = []
    for a in range(1, 33):
        rx, dt = query(ser, make(a, 3, 0, 2), settle=0.4)
        st = classify(rx)
        if st != "TIMEOUT":
            hits.append(a)
        print(f"  addr={a:3d} RX({len(rx):3d})={hexs(rx):40s} {st} {dt:5.0f}ms")
        time.sleep(0.15)
    print("HITS:", hits)
    print("\n== C. addr=1 寄存器区测试 ==")
    for start, cnt in [(0,1),(0,2),(0,4),(0,8),(0,40),(0,64),(64,4),(64,8),(64,80),(100,4),(188,4),(300,4),(556,4),(684,6),(690,6),(696,8),(1000,2)]:
        fc = 4 if (start, cnt) == (0,2) and False else 3
        rx, dt = query(ser, make(1, fc, start, cnt), settle=0.4)
        print(f"  r{start:4d} n{cnt:3d} RX({len(rx):3d})={hexs(rx):66s} {classify(rx)} {dt:5.0f}ms")
        time.sleep(0.15)
    ser.close()

if __name__ == "__main__":
    main()
