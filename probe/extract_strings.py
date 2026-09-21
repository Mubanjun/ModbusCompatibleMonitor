# -*- coding: utf-8 -*-
"""从厂家配置软件/说明书中提取协议相关字符串"""
import re, os, sys

targets = [
    r"D:\EXPPROJECT\JDRK-MonitorPlatform\智能监控主机资料包\智能监控主机资料包\RS-QXZ-M配置软件.exe",
    r"D:\EXPPROJECT\JDRK-MonitorPlatform\智能监控主机资料包\智能监控主机资料包\LED调试软件\ZKLED 3.0.20Build 2\config.ini",
]
keywords = ["modbus", "mod bus", "寄存器", "通道", "波特率", "规约", "从站", "40001", "模拟量", "系数",
            "COM", "9600", "4800", "19200", "115200", "CRC", "地址"]

def strings_utf16(data):
    out = []
    for m in re.finditer(rb"(?:[\x20-\x7e\u4e00-\u9fff]\x00){4,}", data):
        pass
    return out

def extract(data):
    res = []
    # ASCII
    for m in re.finditer(rb"[\x20-\x7e]{4,}", data):
        res.append(m.group().decode("ascii", "ignore"))
    # UTF-16LE
    for m in re.finditer(rb"(?:[\x20-\x7e]\x00){4,}", data):
        res.append(m.group().decode("utf-16-le", "ignore"))
    # GBK Chinese
    for m in re.finditer(rb"(?:[\xb0-\xf7][\xa1-\xfe]){2,}", data):
        try: res.append(m.group().decode("gbk", "ignore"))
        except Exception: pass
    return res

for path in targets:
    print("="*80)
    print(os.path.basename(path), os.path.getsize(path), "bytes")
    if not os.path.exists(path):
        print("  NOT FOUND"); continue
    data = open(path, "rb").read()
    ss = extract(data)
    seen = set()
    hits = []
    for s in ss:
        if s in seen: continue
        seen.add(s)
        low = s.lower()
        if any(k.lower() in low for k in keywords):
            hits.append(s)
    print(f"  共提取 {len(seen)} 条唯一字符串，命中关键词 {len(hits)} 条：")
    for h in hits[:200]:
        print("   |", h[:200])
