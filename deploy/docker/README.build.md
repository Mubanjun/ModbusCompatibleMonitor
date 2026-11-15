# Docker 编译与测试

> 面向新测试机（Ubuntu + Docker），宿主机不需要 Java/Node/pnpm。

## 一键执行

```bash
cd /home/wei/jdrk
bash tools/docker/build-and-test.sh
```

流程：

1. `node:22-bookworm-slim` 中安装 pnpm 9.3.0，执行 `pnpm lint && pnpm typecheck && pnpm build`；
2. `eclipse-temurin:21-jdk` 中执行 `./gradlew build`；
3. 构建运行镜像，启动 MySQL 8.4 + Redis 7 + 后端；
4. 等待 `http://127.0.0.1:18000/monitor/watchdog/heartbeat` 返回 200。

## 端口

- 后端：宿主机 `18000` → 容器 `8000`
- MySQL：宿主机 `13306` → 容器 `3306`
- Redis：宿主机 `16379` → 容器 `6379`

## 停止与重置

```bash
docker compose -f deploy/docker/docker-compose.build.yml down
docker compose -f deploy/docker/docker-compose.build.yml down -v
```
