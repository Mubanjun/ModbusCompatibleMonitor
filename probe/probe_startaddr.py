# -*- coding: utf-8 -*-
import time, serial

def crc16(data: bytes) -> bytes:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])
def make(addr, func, start=0, count=0):
    body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF])
    return body + crc16(body)
def hexs(b): return " ".join(f"{x:02X}" for x in b)
def crc_ok(fr): return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]

def probe(baud):
    try:
        ser = serial.Serial("COM3", baud, bytesize=8, parity="N", stopbits=1, timeout=0.1)
    except Exception as e:
        print(f"baud={baud}: open fail {e}"); return
    print(f"--- baud={baud} ---")
    for name, start, cnt in [("r0 n8",0,8), ("r8 n8",8,8), ("r37 n6",37,6), ("r16 n2",16,2), ("r64 n8",64,8)]:
        tx = make(1, 3, start, cnt)
        ser.reset_input_buffer(); ser.reset_output_buffer()
        ser.write(tx); ser.flush(); time.sleep(0.35)
        rx = ser.read(512)
        st = "TIMEOUT" if not rx else ("OK" if crc_ok(rx) else "CRC_FAIL")
        print(f"  {name:10s} TX={hexs(tx):24s} RX({len(rx):4d})={hexs(rx)[:90]:90s} {st}")
        time.sleep(0.1)
    ser.close()

for b in (4800, 9600, 19200, 38400, 57600, 115200):
    probe(b)
