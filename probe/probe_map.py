# -*- coding: utf-8 -*-
"""全寄存器映射 + 地址语义 + 时钟验证"""
import time, serial, json

def crc16(data: bytes) -> bytes:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])
def make(addr, func, start, count):
    body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF])
    return body + crc16(body)
def crc_ok(fr): return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]

ser = serial.Serial("COM3", 9600, bytesize=8, parity="N", stopbits=1, timeout=0.1)
def read_regs(addr, start, count, settle=0.45, tries=3):
    tx = make(addr, 3, start, count)
    for _ in range(tries):
        ser.reset_input_buffer(); ser.reset_output_buffer()
        ser.write(tx); ser.flush(); time.sleep(settle)
        rx = ser.read(2048)
        if rx and crc_ok(rx) and rx[2] == count*2:
            return [int.from_bytes(rx[3+2*i:5+2*i], "big") for i in range(count)], rx
    return None, rx

print("== 1. 全寄存器映射 0..2047（块 64）==")
nonzero = {}
for base in range(0, 2048, 64):
    regs, rx = read_regs(1, base, 64)
    if regs is None:
        print(f"  [{base:4d}..{base+63:4d}] FAIL rx={rx.hex(' ')}")
        continue
    nz = [(base+i, v) for i, v in enumerate(regs) if v]
    if nz:
        print(f"  [{base:4d}..{base+63:4d}] 非零: {nz}")
        nonzero.update(dict(nz))
print("全部非零寄存器:", nonzero)

print("\n== 2. 时钟验证（reg 37..42，间隔 5s）==")
a, _ = read_regs(1, 37, 6)
time.sleep(5)
b, _ = read_regs(1, 37, 6)
print("  t0:", a)
print("  t1:", b)

print("\n== 3. 地址语义：地址 1..8 读 reg0..31 对比 ==")
base_blk, _ = read_regs(1, 0, 32)
for a in range(1, 9):
    blk, _ = read_regs(a, 0, 32)
    diff = [(i, base_blk[i], blk[i]) for i in range(32) if base_blk[i] != blk[i]] if blk else "FAIL"
    print(f"  addr={a:2d}: {blk}")
    if a != 1 and diff != "FAIL":
        print(f"       与 addr1 差异: {diff}")

print("\n== 4. 稳定性：地址1 reg0..31 连续 4 次 ==")
for i in range(4):
    blk, _ = read_regs(1, 0, 32)
    print(f"  #{i+1}: {blk}")
    time.sleep(0.5)

print("\n== 5. 可写性探测（FC06，写回原值）==")
orig, _ = read_regs(1, 0, 64)
writable = []
for r in range(0, 64):
    if not orig: break
    val = orig[r]
    tx = make(1, 6, r, val)
    ser.reset_input_buffer(); ser.write(tx); ser.flush(); time.sleep(0.35)
    rx = ser.read(64)
    if rx and crc_ok(rx) and rx[1] == 6:
        writable.append(r)
print("  FC06 应答正常(可写)的寄存器:", writable)
ser.close()
