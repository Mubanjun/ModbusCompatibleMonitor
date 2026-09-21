# -*- coding: utf-8 -*-
"""用 GitHub Git Data API 推送本地仓库（绕过 git 的 ssh/https 传输）。
token 从环境变量 GITHUB_TOKEN 读取，不落盘。
用法: $env:GITHUB_TOKEN="<pat>"; python tools/push_via_api.py
"""
import os, sys, json, base64, subprocess, urllib.request, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
OWNER, REPO, BRANCH = "Mubanjun", "ModbusCompatibleMonitor", "main"
TOKEN = os.environ.get("GITHUB_TOKEN")
if not TOKEN:
    print("缺少 GITHUB_TOKEN 环境变量")
    sys.exit(2)
API = "https://api.github.com"


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(
        API + path, data=data, method=method,
        headers={
            "Authorization": "Bearer " + TOKEN,
            "Accept": "application/vnd.github+json",
            "User-Agent": "jdrk-push",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(r, timeout=90) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else {}


def main():
    me = call("GET", "/user")
    print("authenticated as:", me.get("login"))

    repo = call("GET", f"/repos/{OWNER}/{REPO}")
    print("repo:", repo.get("full_name"), "| default:", repo.get("default_branch"))

    files = subprocess.check_output(["git", "ls-files", "-z"]).decode("utf-8", "replace").split("\0")
    files = [f for f in files if f]
    print("files to push:", len(files))

    tree = []
    for i, path in enumerate(files, 1):
        with open(path, "rb") as fh:
            content = fh.read()
        blob = call("POST", f"/repos/{OWNER}/{REPO}/git/blobs",
                    {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        tree.append({"path": path.replace("\\", "/"), "mode": "100644", "type": "blob", "sha": blob["sha"]})
        if i % 20 == 0 or i == len(files):
            print(f"  blobs {i}/{len(files)}", flush=True)

    new_tree = call("POST", f"/repos/{OWNER}/{REPO}/git/trees", {"tree": tree})
    print("tree:", new_tree["sha"])

    msg = "feat: JDRK water-quality monitor platform (Rust backend + Qt6 QML frontend)"
    commit = call("POST", f"/repos/{OWNER}/{REPO}/git/commits",
                  {"message": msg, "tree": new_tree["sha"], "parents": []})
    print("commit:", commit["sha"])

    # 空仓库：创建分支引用；已存在则更新
    try:
        call("POST", f"/repos/{OWNER}/{REPO}/git/refs",
             {"ref": f"refs/heads/{BRANCH}", "sha": commit["sha"]})
        print("created ref refs/heads/" + BRANCH)
    except Exception:
        call("PATCH", f"/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}",
             {"sha": commit["sha"], "force": True})
        print("updated ref refs/heads/" + BRANCH)

    print("DONE ->", f"https://github.com/{OWNER}/{REPO}")


if __name__ == "__main__":
    main()
