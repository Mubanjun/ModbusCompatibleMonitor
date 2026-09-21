# -*- coding: utf-8 -*-
import glob, os, io, re

root = r"D:\EXPPROJECT\JDRK-MonitorPlatform\frontend\qml"
changed = []
for path in glob.glob(os.path.join(root, "**", "*.qml"), recursive=True):
    lines = io.open(path, encoding="utf-8").read().splitlines(keepends=True)
    out = []
    depth = 0
    in_block = False
    for ln in lines:
        stripped = ln.strip()
        if not in_block and "StatusPill {" in ln:
            in_block = True
            depth = ln.count("{") - ln.count("}")
            out.append(ln)
            continue
        if in_block:
            if re.match(r"^\s*color\s*:", ln):
                ln = re.sub(r"^(\s*)color\s*:", r"\1statusColor:", ln)
                changed.append((os.path.basename(path), ln.strip()))
            depth += ln.count("{") - ln.count("}")
            if depth <= 0:
                in_block = False
        out.append(ln)
    io.open(path, "w", encoding="utf-8").write("".join(out))
print("replacements:", len(changed))
for c in changed:
    print("  ", c[0], "->", c[1])
