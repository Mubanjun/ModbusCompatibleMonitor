# -*- coding: utf-8 -*-
# GitHub Git Data API 推送（绕过沙箱不可用的 git ssh/https 传输）
# token 从 GITHUB_TOKEN 读取，不落盘；提交信息可用 COMMIT_MSG 覆盖
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
    repo = call("GET", "/repos/" + OWNER + "/" + REPO)
    print("repo:", repo.get("full_name"), "| default:", repo.get("default_branch"))

    files = [f for f in subprocess.check_output(["git", "ls-files", "-z"]).decode("utf-8", "replace").split("\0") if f]
    print("files to push:", len(files))

    tree = []
    for i, path in enumerate(files, 1):
        with open(path, "rb") as fh:
            content = fh.read()
        blob = call("POST", "/repos/" + OWNER + "/" + REPO + "/git/blobs",
                    {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        tree.append({"path": path.replace("\\", "/"), "mode": "100644", "type": "blob", "sha": blob["sha"]})
        if i % 25 == 0 or i == len(files):
            print("  blobs %d/%d" % (i, len(files)), flush=True)

    new_tree = call("POST", "/repos/" + OWNER + "/" + REPO + "/git/trees", {"tree": tree})
    print("tree:", new_tree["sha"])

    parents = []
    try:
        ref = call("GET", "/repos/" + OWNER + "/" + REPO + "/git/ref/heads/" + BRANCH)
        parents = [ref["object"]["sha"]]
        print("parent:", parents[0][:8])
    except Exception:
        print("parent: none (first commit)")

    msg = os.environ.get("COMMIT_MSG") or "chore: update"
    commit = call("POST", "/repos/" + OWNER + "/" + REPO + "/git/commits",
                  {"message": msg, "tree": new_tree["sha"], "parents": parents})
    print("commit:", commit["sha"])

    try:
        call("PATCH", "/repos/" + OWNER + "/" + REPO + "/git/refs/heads/" + BRANCH,
             {"sha": commit["sha"], "force": False})
        print("updated ref refs/heads/" + BRANCH)
    except Exception:
        call("POST", "/repos/" + OWNER + "/" + REPO + "/git/refs",
             {"ref": "refs/heads/" + BRANCH, "sha": commit["sha"]})
        print("created ref refs/heads/" + BRANCH)

    print("DONE -> https://github.com/" + OWNER + "/" + REPO)


if __name__ == "__main__":
    main()
