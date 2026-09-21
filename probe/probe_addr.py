# -*- coding: utf-8 -*-
import time, serial
def crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])
def make(addr, func, start, count):
    return bytes([addr, func, start>>8, start&0xFF, count>>8, count&0xFF]) + crc16(bytes([addr, func, start>>8, start&0xFF, count>>8, count&0xFF]))
def crc_ok(fr): return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]
ser = serial.Serial("COM3", 9600, bytesize=8, parity="N", stopbits=1, timeout=0.1)
def rd(addr, start, count, settle=0.4):
    tx = make(addr, 3, start, count)
    for _ in range(2):
        ser.reset_input_buffer(); ser.reset_output_buffer()
        ser.write(tx); ser.flush(); time.sleep(settle)
        rx = ser.read(512)
        if rx and crc_ok(rx): return rx
    return rx
print("== 高地址与扩展地址 ==")
for a in (0, 33, 34, 64, 65, 96, 97, 128, 247, 248, 255):
    rx = rd(a, 0, 2)
    st = "TIMEOUT" if not rx else ("OK" if crc_ok(rx) else "CRC_FAIL")
    print(f"  addr={a:3d} RX={rx.hex(' '):30s} {st}")
print("\n== 各地址 reg12/reg16 全表 (1..32) ==")
for a in range(1, 33):
    rx = rd(a, 8, 12)
    if rx and crc_ok(rx):
        regs = [int.from_bytes(rx[3+2*i:5+2*i], "big") for i in range(12)]
        print(f"  addr={a:2d} reg8..19 = {regs}")
    else:
        print(f"  addr={a:2d} FAIL {rx.hex(' ')}")
ser.close()
