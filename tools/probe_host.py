# -*- coding: utf-8 -*-
"""Real-hardware probe: wait for the serial port, then detect baud / RTS / protocol.
Output is ASCII-only so it survives console encoding issues.
"""
import argparse, json, sys, time
import serial
import serial.tools.list_ports as list_ports

BAUDS = [4800, 9600, 19200, 38400, 57600, 115200]


def crc16(data):
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0xA001 if crc & 1 else crc >> 1
    return bytes([crc & 0xFF, (crc >> 8) & 0xFF])


def crc_ok(fr):
    return len(fr) >= 4 and crc16(fr[:-2]) == fr[-2:]


def make(addr, func, start, count):
    body = bytes([addr, func, start >> 8, start & 0xFF, count >> 8, count & 0xFF])
    return body + crc16(body)


def wait_open(port, wait_s):
    deadline = time.time() + wait_s
    attempt = 0
    while time.time() < deadline:
        attempt += 1
        try:
            s = serial.Serial(port, 9600, timeout=0.1)
            s.close()
            print("[wait] port %s is openable after %d attempts" % (port, attempt), flush=True)
            return True
        except Exception as e:
            if attempt == 1 or attempt % 10 == 0:
                print("[wait] %s not ready (%s) - replug the USB-485 module..." % (port, str(e)[:60]), flush=True)
            time.sleep(3)
    print("[wait] timeout after %ds" % wait_s, flush=True)
    return False


def wait_detect(wait_s):
    deadline = time.time() + wait_s
    while time.time() < deadline:
        ports = list(list_ports.comports())
        if ports:
            print("[wait] detected: %s" % [p.device for p in ports], flush=True)
            return ports[0].device
        time.sleep(3)
    return None


def transact(s, tx, rts_flip, wait=0.4):
    s.reset_input_buffer()
    s.reset_output_buffer()
    if rts_flip:
        s.setRTS(True)
    s.write(tx)
    s.flush()
    if rts_flip:
        time.sleep(0.002)
        s.setRTS(False)
    time.sleep(wait)
    return s.read(512)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", default="COM3")
    ap.add_argument("--wait", type=int, default=0, help="seconds to wait for the port")
    ap.add_argument("--baud", type=int, default=0, help="force a baud rate")
    args = ap.parse_args()

    if args.port == "auto":
        args.port = wait_detect(args.wait or 120)
        if not args.port:
            print("[wait] no serial port detected", flush=True)
            sys.exit(2)
        print("[wait] using port %s" % args.port, flush=True)
    elif args.wait and not wait_open(args.port, args.wait):
        sys.exit(2)

    bauds = [args.baud] if args.baud else BAUDS
    winner = None
    print("[probe] scanning baud x rts ...", flush=True)
    for baud in bauds:
        for rts in (False, True):
            try:
                s = serial.Serial(args.port, baud, bytesize=8, parity="N", stopbits=1, timeout=0.1)
            except Exception as e:
                print("[probe] baud=%-6d rts=%-5s OPEN FAIL %s" % (baud, rts, str(e)[:50]), flush=True)
                continue
            try:
                rx = transact(s, make(1, 3, 0, 2), rts)
                ok = crc_ok(rx)
                print("[probe] baud=%-6d rts=%-5s rx(%3d)=%s %s" % (
                    baud, rts, len(rx), rx.hex(" "), "OK" if ok else ("TIMEOUT" if not rx else "CRC_FAIL")), flush=True)
                if ok and winner is None:
                    winner = {"baud": baud, "rts_flip": rts}
            finally:
                s.close()
            if winner:
                break
        if winner:
            break

    if not winner:
        print("[probe] no working baud/rts combination", flush=True)
        sys.exit(3)

    print("[probe] winner: baud=%d rts_flip=%s" % (winner["baud"], winner["rts_flip"]), flush=True)

    s = serial.Serial(args.port, winner["baud"], bytesize=8, parity="N", stopbits=1, timeout=0.1)
    # address scan
    online = []
    for addr in range(1, 33):
        rx = transact(s, make(addr, 3, 0, 2), winner["rts_flip"], wait=0.25)
        if crc_ok(rx):
            online.append(addr)
            if len(online) <= 3:
                print("[scan] addr=%-2d regs=%s" % (addr, rx.hex(" ")), flush=True)
    print("[scan] online addresses: %s" % online, flush=True)

    # standard-modbus time register probe (684..689)
    time_year = None
    rx = transact(s, make(1, 3, 684, 6), winner["rts_flip"], wait=0.4)
    if crc_ok(rx) and len(rx) >= 17:
        vals = [int.from_bytes(rx[3 + 2 * i:5 + 2 * i], "big") for i in range(6)]
        print("[std] reg684..689 = %s" % vals, flush=True)
        if 2000 <= vals[0] <= 2100:
            time_year = vals[0]

    # rs-modbus channel-clock probe (37..42)
    rs_clock = False
    rx = transact(s, make(1, 3, 37, 6), winner["rts_flip"], wait=0.4)
    if crc_ok(rx) and len(rx) >= 17:
        vals = [int.from_bytes(rx[3 + 2 * i:5 + 2 * i], "big") for i in range(6)]
        print("[rs ] reg37..42 = %s" % vals, flush=True)
        if 2000 <= vals[0] <= 2100:
            rs_clock = True

    protocol = "std_modbus" if time_year else ("rs_modbus" if rs_clock else "unknown")
    result = {
        "port": args.port,
        "baud": winner["baud"],
        "rts_flip": winner["rts_flip"],
        "online_addresses": online,
        "protocol": protocol,
        "std_time_year": time_year,
        "rs_clock": rs_clock,
    }
    print("[result] %s" % json.dumps(result), flush=True)
    with open("probe/hardware_result.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    s.close()


if __name__ == "__main__":
    main()
