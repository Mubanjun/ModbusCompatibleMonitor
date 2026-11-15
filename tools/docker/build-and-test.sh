#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "== 1/3 Docker 编译（前端门禁 + 后端 build） =="
docker build -f deploy/docker/Dockerfile.build --target backend-build -t jdrk-build-test:backend .

echo "== 2/3 启动 MySQL + Redis + 后端 =="
docker compose -f deploy/docker/docker-compose.build.yml up -d --build

echo "== 3/3 等待后端心跳 =="
for i in $(seq 1 90); do
  if curl -fsS --max-time 3 http://127.0.0.1:18000/monitor/watchdog/heartbeat >/tmp/jdrk-heartbeat.json; then
    cat /tmp/jdrk-heartbeat.json
    echo
    echo "Docker 编译与启动测试通过"
    exit 0
  fi
  sleep 2
done

echo "后端未在 180 秒内恢复，最近日志："
docker compose -f deploy/docker/docker-compose.build.yml logs --tail=120 backend
exit 1
