# -*- coding: utf-8 -*-
"""动态性观测：15s 内 addr1/addr2 的 reg0..42 变化"""
import time, serial
def crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])
def make(addr, func, start, count):
    b = bytes([addr, func, start>>8, start&0xFF, count>>8, count&0xFF])
    return b + crc16(b)
def crc_ok(fr): return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]
ser = serial.Serial("COM3", 9600, bytesize=8, parity="N", stopbits=1, timeout=0.1)
def rd(addr, start, count, settle=0.4):
    tx = make(addr,3,start,count)
    for _ in range(3):
        ser.reset_input_buffer(); ser.reset_output_buffer()
        ser.write(tx); ser.flush(); time.sleep(settle)
        rx = ser.read(512)
        if rx and crc_ok(rx) and rx[2]==count*2:
            return [int.from_bytes(rx[3+2*i:5+2*i],"big") for i in range(count)]
    return None
snaps = {1: [], 2: []}
t0 = time.time()
while time.time()-t0 < 15:
    for a in (1,2):
        r = rd(a, 0, 43)
        snap = {"t": round(time.time()-t0,1), "regs": r}
        snaps[a].append(snap)
    time.sleep(0.5)
ser.close()
for a, s in snaps.items():
    print(f"=== addr={a} 采样 {len(s)} 次 ===")
    base = s[0]["regs"]
    changed = {}
    for snap in s:
        for i, v in enumerate(snap["regs"]):
            if v != base[i]:
                changed.setdefault(i, []).append((snap["t"], v))
    for i, vals in changed.items():
        print(f"  reg{i:2d}: base={base[i]} 变化序列={vals[:12]}{'...' if len(vals)>12 else ''}")
    if not changed: print("  无变化")
