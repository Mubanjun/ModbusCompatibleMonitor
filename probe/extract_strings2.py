# -*- coding: utf-8 -*-
import re, os, io

targets = [
    r"D:\EXPPROJECT\JDRK-MonitorPlatform\智能监控主机资料包\智能监控主机资料包\RS-QXZ-M配置软件.exe",
]
out = io.open(r"D:\EXPPROJECT\JDRK-MonitorPlatform\probe\strings.txt", "w", encoding="utf-8")

def extract(data):
    res = []
    for m in re.finditer(rb"[\x20-\x7e]{4,}", data):
        res.append(m.group().decode("ascii", "ignore"))
    for m in re.finditer(rb"(?:[\x20-\x7e]\x00){4,}", data):
        res.append(m.group().decode("utf-16-le", "ignore"))
    for m in re.finditer(rb"(?:[\xb0-\xf7][\xa1-\xfe]){2,}", data):
        try: res.append(m.group().decode("gbk", "ignore"))
        except Exception: pass
    # also decode any non-ascii blobs as gbk
    for m in re.finditer(rb"[\x80-\xff]{6,}", data):
        try:
            s = m.group().decode("gbk", "ignore")
            if sum(1 for c in s if '\u4e00' <= c <= '\u9fff') >= 2:
                res.append(s)
        except Exception: pass
    return res

for path in targets:
    data = open(path, "rb").read()
    ss = extract(data)
    seen = []
    for s in ss:
        if s not in seen:
            seen.append(s)
    out.write(f"### {os.path.basename(path)} 唯一字符串 {len(seen)}\n")
    for s in seen:
        out.write(s + "\n")
out.close()
print("done")
