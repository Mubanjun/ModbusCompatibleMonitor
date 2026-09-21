# -*- coding: utf-8 -*-
"""RS-QXZ-M 通信方式深度探测：区分 RS-Modbus / 标准 Modbus，读出寄存器布局"""
import sys, time, serial

def crc16(data: bytes) -> bytes:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])

def make(addr, func, start=0, count=0, payload=b""):
    if func in (3, 4):
        body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF])
    elif func == 6:
        body = bytes([addr, func, start >> 8, start & 0xFF, (count >> 8) & 0xFF, count & 0xFF])
    elif func == 16:
        body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF,
                      len(payload)]) + payload
    else:
        body = bytes([addr, func]) + payload
    return body + crc16(body)

def hexs(b): return " ".join(f"{x:02X}" for x in b)

def crc_ok(fr):
    if len(fr) < 4: return False
    return crc16(fr[:-2]) == fr[-2:]

def xact(ser, tx, wait=1.0):
    ser.reset_input_buffer()
    ser.write(tx); ser.flush()
    t0 = time.time(); buf = bytearray()
    deadline = t0 + wait
    ser.timeout = 0.02
    while time.time() < deadline:
        chunk = ser.read(256)
        if chunk:
            buf += chunk
            # 提前返回：长度足够（先用最小可判定长度）
            deadline = time.time() + 0.05
        elif buf and time.time() - t0 > 0.05:
            # 已收到数据且静默 50ms，认为帧结束
            break
    return bytes(buf), (time.time() - t0) * 1000

def classify(rx):
    if not rx: return "TIMEOUT"
    if len(rx) >= 5 and (rx[1] & 0x80):
        return f"EXCEPTION code={rx[2]}"
    return "OK" if crc_ok(rx) else "CRC_FAIL"

def run(port, baud, addr):
    print(f"\n########## {port} @ {baud} addr={addr} ##########")
    ser = serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.02)
    tests = [
        ("FC03 reg0 n2  (RS/标准 通道1 原始值)", make(addr, 3, 0, 2)),
        ("FC03 reg0 n1", make(addr, 3, 0, 1)),
        ("FC03 reg0 n4", make(addr, 3, 0, 4)),
        ("FC03 reg0 n8", make(addr, 3, 0, 8)),
        ("FC03 reg0 n40 (通道1-20 原始值)", make(addr, 3, 0, 40)),
        ("FC04 reg0 n2", make(addr, 4, 0, 2)),
        ("FC03 reg64 n4  (标准: 通道1 处理值)", make(addr, 3, 64, 4)),
        ("FC03 reg64 n8", make(addr, 3, 64, 8)),
        ("FC03 reg64 n80 (20路处理值 165B)", make(addr, 3, 64, 80)),
        ("FC03 reg188 n4 (32号通道处理值)", make(addr, 3, 188, 4)),
        ("FC03 reg300 n4 (上下限)", make(addr, 3, 300, 4)),
        ("FC03 reg556 n4 (偏差)", make(addr, 3, 556, 4)),
        ("FC03 reg684 n6 (时间)", make(addr, 3, 684, 6)),
        ("FC03 reg690 n6 (存储)", make(addr, 3, 690, 6)),
        ("FC03 reg696 n2 (继电器)", make(addr, 3, 696, 2)),
        ("FC03 reg1000 n2 (越界)", make(addr, 3, 1000, 2)),
    ]
    for name, tx in tests:
        try:
            rx, dt = xact(ser, tx, wait=1.0)
            print(f"{name:42s} TX={hexs(tx):24s} RX({len(rx):3d})={hexs(rx):60s} {classify(rx)} {dt:6.0f}ms")
        except Exception as e:
            print(f"{name:42s} ERROR {e}")
    ser.close()

def scan_addrs(port, baud, addrs):
    print(f"\n########## 地址扫描 @ {baud} ##########")
    ser = serial.Serial(port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.02)
    for a in addrs:
        tx = make(a, 3, 0, 2)
        rx, dt = xact(ser, tx, wait=0.6)
        print(f"addr={a:3d} RX({len(rx):3d})={hexs(rx):24s} {classify(rx)} {dt:5.0f}ms")
    ser.close()

if __name__ == "__main__":
    port = "COM3"
    baud = int(sys.argv[1]) if len(sys.argv) > 1 else 9600
    addr = int(sys.argv[2]) if len(sys.argv) > 2 else 1
    run(port, baud, addr)
    if len(sys.argv) > 3 and sys.argv[3] == "scan":
        scan_addrs(port, baud, list(range(1, 33)))
