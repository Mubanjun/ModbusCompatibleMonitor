# -*- coding: utf-8 -*-
import time, serial

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
def show(name, tx, settle=0.35, w=160):
    rx = q(tx, settle)
    print(f"{name:30s} TX={hexs(tx):28s} RX({len(rx):4d})={hexs(rx)[:w]:{w}s} {classify(rx)}")
    time.sleep(0.12)
    return rx

print("== 地址语义：同块 r0 n64 不同地址 ==")
blocks = {}
for a in (1, 2, 3, 16, 32):
    rx = show(f"addr={a} FC03 r0 n64", make(a, 3, 0, 64), settle=0.6, w=0)
    blocks[a] = rx[3:3+rx[2]] if len(rx) > 3 else b""
print("各地址数据是否相同:", all(blocks[a] == blocks[1] for a in blocks))

print("\n== 功能码支持 ==")
show("FC01 读线圈", make(1, 1, 0, 8))
show("FC02 读离散", make(1, 2, 0, 8))
show("FC04 读输入寄存器", make(1, 4, 0, 2))
show("FC05 写单线圈", bytes([1,5,0,0,0xFF,0x00])+crc16(bytes([1,5,0,0,0xFF,0x00])))
show("FC06 写 r0 =0x1234", make(1, 6, 0, 0x1234))
show("FC03 读回 r0", make(1, 3, 0, 1))
show("FC06 写 r690 =0", make(1, 6, 690, 0))
show("FC16 写 r0 两寄存器", make(1, 16, 0, 2, b"\x00\x01\x00\x02"))
show("FC08 诊断", make(1, 8, 0, 0))
show("FC17 报告从站ID", bytes([1,17])+crc16(bytes([1,17])))
show("FC43/14 读设备标识", make(1, 0x2B, 0, 0, b"\x0E\x01\x00"))

print("\n== 广播 addr=0 ==")
show("addr=0 FC03 r0 n2", make(0, 3, 0, 2))

print("\n== 寄存器 0..63 内容 ==")
rx = q(make(1, 3, 0, 64), settle=0.8)
n = rx[2]; data = rx[3:3+n]
regs = [int.from_bytes(data[i:i+2], "big") for i in range(0, n, 2)]
print("raw:", hexs(data))
for i in range(0, len(regs), 16):
    print("  ", " ".join(f"{j:2d}:{regs[j]:5d}" for j in range(i, min(i+16, len(regs)))))
print("非零寄存器:", [(i, v) for i, v in enumerate(regs) if v])
ser.close()
