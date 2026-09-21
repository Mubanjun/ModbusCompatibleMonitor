# -*- coding: utf-8 -*-
"""被动监听：检测总线上是否有周期性主站轮询/自发报文"""
import sys, time, serial

def hexs(b): return " ".join(f"{x:02X}" for x in b)

def sniff(port, baud, secs=8.0):
    ser = serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.05)
    ser.reset_input_buffer()
    frames = []
    cur = bytearray(); last = time.time(); t_start = time.time()
    while time.time() - t_start < secs:
        b = ser.read(256)
        now = time.time()
        if b:
            if cur and (now - last) > 0.05 and len(cur) > 0:
                frames.append(bytes(cur)); cur = bytearray()
            cur += b; last = now
        else:
            if cur and (now - last) > 0.05:
                frames.append(bytes(cur)); cur = bytearray()
    if cur: frames.append(bytes(cur))
    ser.close()
    print(f"--- {port} @ {baud} N81, 监听 {secs:.0f}s：收到 {len(frames)} 帧 ---")
    for f in frames[:60]:
        print(f"   [{len(f):3d}] {hexs(f)}")
    return frames

if __name__ == "__main__":
    port = sys.argv[1] if len(sys.argv) > 1 else "COM3"
    for baud in ([int(x) for x in sys.argv[2:]] or [9600, 4800]):
        sniff(port, baud, 6.0)
