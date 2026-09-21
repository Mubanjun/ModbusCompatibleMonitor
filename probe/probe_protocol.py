# -*- coding: utf-8 -*-
"""协议判别探测：地址语义、功能码、异常、写操作、寄存器内容"""
import sys, time, serial, struct

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
    elif func in (16, 23):
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

ser = serial.Serial("COM3", 9600, bytesize=8, parity="N", stopbits=1, timeout=0.1)
def q(tx, settle=0.35):
    ser.reset_input_buffer(); ser.reset_output_buffer()
    ser.write(tx); ser.flush(); time.sleep(settle)
    return ser.read(1024)
def show(name, tx, settle=0.35):
    rx = q(tx, settle)
    print(f"{name:34s} TX={hexs(tx):30s} RX({len(rx):4d})={hexs(rx)[:150]:150s} {classify(rx)}")
    time.sleep(0.12)
    return rx

print("== 1. 同一寄存器块，不同从地址 ==")
for a in (1, 2, 5, 16, 32):
    show(f"addr={a} FC03 r0 n8", make(a, 3, 0, 8))

print("\n== 2. count 上限 / 异常 ==")
for cnt in (125, 126, 128, 200):
    show(f"FC03 r0 n{cnt}", make(1, 3, 0, cnt), settle=0.8)
show("FC03 r0 n0", make(1, 3, 0, 0))
show("FC03 r70000 n1", make(1, 3, 70000, 1))

print("\n== 3. 功能码支持 ==")
show("FC01 (读线圈)", make(1, 1, 0, 8))
show("FC02 (读离散)", make(1, 2, 0, 8))
show("FC04 (读输入寄存器)", make(1, 4, 0, 2))
show("FC05 (写单线圈)", bytes([1, 5, 0, 0, 0xFF, 0x00]) + crc16(bytes([1,5,0,0,0xFF,0x00])))
show("FC06 (写单寄存器 r0)", make(1, 6, 0, 0x1234))
show("FC06 (写单寄存器 r690)", make(1, 6, 690, 0))
show("FC16 (写多寄存器 r0)", make(1, 16, 0, 2, b"\x00\x01\x00\x02"))
show("FC08 (诊断)", make(1, 8, 0, 0))
show("FC17 (报告从站ID)", bytes([1, 17]) + crc16(bytes([1,17])))
show("FC43/14 (读设备标识)", make(1, 0x2B, 0, 0, b"\x0E\x01\x00"))

print("\n== 4. 广播地址 0 ==")
show("addr=0 FC03 r0 n2", make(0, 3, 0, 2))

print("\n== 5. 寄存器 0..63 完整数据 ==")
rx = q(make(1, 3, 0, 64), settle=0.8)
if len(rx) >= 3 and rx[1] == 3:
    n = rx[2]
    data = rx[3:3+n]
    regs = [int.from_bytes(data[i:i+2], "big") for i in range(0, n, 2)]
    print("regs:", [f"{i}:{v}" for i, v in enumerate(regs)])
    print("\n通道解析（标准规约：reg 2k=通道k+1 模拟量1，2k+1=模拟量2）:")
    for ch in range(1, 33):
        ai1 = regs[2*(ch-1)] if 2*(ch-1) < len(regs) else None
        ai2 = regs[2*(ch-1)+1] if 2*(ch-1)+1 < len(regs) else None
        if ai1 or ai2:
            print(f"  通道{ch:2d}: AI1={ai1} (s16={ai1-65536 if ai1>32767 else ai1})  AI2={ai2}")
    print("\nraw data bytes:", hexs(data))
ser.close()
