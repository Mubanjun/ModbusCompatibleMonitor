# -*- coding: utf-8 -*-
"""完整 dump：地址 1..32 的 reg0..63 + 时间；地址1 的 reg64..191；保存 JSON"""
import time, serial, json
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
def read_regs(addr, start, count, settle=0.5, tries=3):
    tx = make(addr, 3, start, count)
    for _ in range(tries):
        ser.reset_input_buffer(); ser.reset_output_buffer()
        ser.write(tx); ser.flush(); time.sleep(settle)
        rx = ser.read(1024)
        if rx and crc_ok(rx) and rx[2] == count*2:
            return [int.from_bytes(rx[3+2*i:5+2*i], "big") for i in range(count)]
    return None

out = {"link": {"port": "COM3", "baud": 9600, "bytesize": 8, "parity": "N", "stopbits": 1},
       "per_addr": {}, "addr1_high": {}}
for a in range(1, 33):
    regs0 = read_regs(a, 0, 64)
    t = read_regs(a, 37, 6)
    out["per_addr"][a] = {"reg0_63": regs0, "time37_42": t}
    print(f"addr={a:2d} reg0..63={regs0}")
    print(f"        time={t}")
for start, cnt in [(64, 64), (128, 64)]:
    r = read_regs(1, start, cnt)
    out["addr1_high"][start] = r
    print(f"addr1 reg{start}..{start+cnt-1} = {r}")
ser.close()
with open(r"D:\EXPPROJECT\JDRK-MonitorPlatform\probe\dump.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("saved dump.json")
