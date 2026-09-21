# -*- coding: utf-8 -*-
"""RS-QXZ-M 主机通讯探测脚本
目的：不依赖厂商文档，直接探测 485 从站口的真实通讯参数与规约。
"""
import sys, time, serial
from serial.tools import list_ports

def crc16(data: bytes) -> bytes:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            if crc & 1:
                crc = (crc >> 1) ^ 0xA001
            else:
                crc >>= 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])

def frame(addr, func, start, count):
    body = bytes([addr, func, (start >> 8) & 0xFF, start & 0xFF, (count >> 8) & 0xFF, count & 0xFF])
    return body + crc16(body)

def hexs(b): return " ".join(f"{x:02X}" for x in b)

def transact(ser, tx, timeout=1.0):
    ser.reset_input_buffer()
    ser.reset_output_buffer()
    t0 = time.time()
    ser.write(tx)
    ser.flush()
    ser.timeout = timeout
    rx = ser.read(256)
    dt = (time.time() - t0) * 1000
    # drain a bit more
    time.sleep(0.05)
    more = ser.read(256)
    return rx + more, dt

def main():
    port = sys.argv[1] if len(sys.argv) > 1 else "COM3"
    print("ports:", [p.device + " " + str(p.description) for p in list_ports.comports()])
    # 阶段1：被动监听，看总线上是否有自发数据
    for baud in (4800, 9600, 19200, 38400, 57600, 115200):
        try:
            with serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.3) as ser:
                ser.reset_input_buffer()
                time.sleep(0.6)
                data = ser.read(512)
                print(f"[listen] {baud:6d} N81 -> {len(data)} bytes {hexs(data[:64])}")
        except Exception as e:
            print(f"[listen] {baud:6d} N81 -> ERROR {e}")
    # 阶段2：主动问询
    for baud in (4800, 9600, 19200, 38400, 57600, 115200):
        for addr in (1, 2, 3, 5):
            # RS-Modbus：读 0000 起 2 个寄存器（模拟量1/2）
            tx = frame(addr, 3, 0, 2)
            try:
                with serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=1.0) as ser:
                    rx, dt = transact(ser, tx)
                    print(f"[probe ] {baud:6d} addr={addr} tx={hexs(tx)} rx({len(rx)})={hexs(rx)} {dt:.0f}ms")
            except Exception as e:
                print(f"[probe ] {baud:6d} addr={addr} ERROR {e}")
                break

if __name__ == "__main__":
    main()
