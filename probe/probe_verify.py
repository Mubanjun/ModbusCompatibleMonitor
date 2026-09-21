# -*- coding: utf-8 -*-
"""1) 校验厂商文档全部样例 CRC  2) 实测 RTT  3) 异常/边界行为"""
import time, serial, statistics

def crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])
def h(b): return " ".join(f"{x:02X}" for x in b)

print("="*70)
print("A. 厂商文档样例 CRC 复算")
print("="*70)
samples = [
 ("RS-Modbus 举例1 问询",        "02 03 00 00 00 02", "C4 38"),
 ("RS-Modbus 举例2 问询",        "0C 03 00 00 00 02", "C5 16"),
 ("RS-Modbus 举例1 应答",        "02 03 04 02 92 FF 9B", "69 3D"),
 ("RS-Modbus 举例2 应答",        "0C 03 04 02 92 FF 9B", "86 FD"),
 ("标准规约 读原始值 问询",        "05 03 00 02 00 04", "E4 4D"),
 ("标准规约 读原始值 应答",        "05 03 08 00 ED 02 7B 00 E0 00 F9", "99 B5"),
 ("标准规约 读处理值 问询",        "05 03 00 BC 00 04", "84 69"),
 ("标准规约 读处理值 应答",        "05 03 08 41 E3 A5 E3 42 82 B1 AA", "0A 89"),
 ("标准规约 校时 设置(起始02AC)",  "05 10 02 AC 00 06 0C 07 E1 00 03 00 1C 00 09 00 3B 00 20", "E4 2B"),
 ("标准规约 校时 应答(文档写026C)","05 10 02 6C 00 06", "80 16"),
 ("标准规约 校时 应答(正确02AC)",  "05 10 02 AC 00 06", "80 16"),
 ("标准规约 继电器 设置",          "05 06 02 BA 00 01", "69 D3"),
]
for name, hexstr, stated in samples:
    data = bytes(int(x, 16) for x in hexstr.split())
    calc = crc16(data)
    statedb = bytes(int(x, 16) for x in stated.split())
    print(f"  {name:34s} stated={stated.replace(' ','')} calc={h(calc).replace(' ','')} {'OK' if calc==statedb else '*** MISMATCH ***'}")

print()
print("="*70)
print("B. RTT 实测（COM3 @9600）")
print("="*70)
ser = serial.Serial("COM3", 9600, bytesize=8, parity="N", stopbits=1, timeout=1.0)
def make(addr, func, start, count):
    b = bytes([addr, func, start>>8, start&0xFF, count>>8, count&0xFF])
    return b + crc16(b)
def timed(tx, reps=50):
    ts = []
    for _ in range(reps):
        ser.reset_input_buffer(); ser.reset_output_buffer()
        t0 = time.perf_counter(); ser.write(tx); ser.flush()
        # read until 3 bytes header + payload
        head = ser.read(3)
        if len(head) < 3: ts.append(None); continue
        n = head[2]
        rest = ser.read(n+2)
        dt = (time.perf_counter()-t0)*1000
        ts.append((dt, len(head)+len(rest)))
        time.sleep(0.05)
    ok = [t for t in ts if t]
    ds = [t[0] for t in ok]
    print(f"  tx n={len(tx)}B: n={len(ts)} min={min(ds):.1f} avg={statistics.mean(ds):.1f} p95={statistics.quantiles(ds, n=20)[18]:.1f} max={max(ds):.1f} ms")
    return ts
timed(make(1,3,0,2), 50)
timed(make(1,3,0,64), 50)
timed(make(1,3,64,80), 50)

print()
print("="*70)
print("C. 异常/边界")
print("="*70)
def q(tx, settle=0.4):
    ser.reset_input_buffer(); ser.reset_output_buffer()
    ser.write(tx); ser.flush(); time.sleep(settle); return ser.read(1024)
def st(rx):
    if not rx: return "TIMEOUT"
    if len(rx)>=3 and rx[1]&0x80: return f"EXC-{rx[2]:02X}"
    return "OK" if (len(rx)>=4 and crc16(rx[:-2])==rx[-2:]) else "CRC_FAIL"
cases = [
 ("FC03 n=0 (非法数量)", make(1,3,0,0)),
 ("FC03 n=125", make(1,3,0,125)),
 ("FC03 n=126", make(1,3,0,126)),
 ("FC01 (不支持)", bytes.fromhex("0101")+crc16(bytes.fromhex("0101"))),
 ("FC07 (不支持)", bytes.fromhex("0107")+crc16(bytes.fromhex("0107"))),
 ("FC03 起始7000 n2", make(1,3,7000,1)),
 ("坏CRC 帧", bytes([1,3,0,0,0,2,0xFF,0xFF])),
 ("纯噪声", bytes([0xAA]*8)),
]
for name, tx in cases:
    rx = q(tx)
    print(f"  {name:24s} TX={h(tx):28s} RX({len(rx):4d})={h(rx)[:50]:50s} {st(rx)}")
ser.close()
