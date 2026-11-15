# Docker 容器化部署（验证用）

> 目的：把平台栈（MySQL + Redis + 后端）整体装进容器，**不触碰宿主已部署的服务与数据库**，
> 用于功能回归、长期测试与演示，避免直接改动测试机系统导致意外。

## 一、端口约定（避开宿主原生部署）

| 服务 | 容器内 | 宿主 | 说明 |
| --- | --- | --- | --- |
| 后端 | 8000 | **18000** | `http://<host>:18000/monitor/watchdog/heartbeat` |
| MySQL | 3306 | **13306** | 独立数据卷 `mysql-data` |
| Redis | 6379 | **16379** | 独立容器 |

宿主原有的 8000/3306/6379 与 systemd 服务**完全不受影响**。

## 二、构建与启动

```bash
# 在仓库根目录：把后端 jar 放到构建上下文
cp backend/continew-server/build/libs/continew-admin.jar deploy/docker/continew-admin.jar

cd deploy/docker
cp .env.example .env          # 需要时改端口/口令
sudo docker compose up -d --build
sudo docker compose ps
curl -s http://127.0.0.1:18000/monitor/watchdog/heartbeat | head -c 200
```

首次启动 MySQL 初始化 + Liquibase 建表约 1~3 分钟（ARM64 更久），`healthcheck` 通过后后端才启动。

## 三、日志与停止

```bash
sudo docker compose logs -f backend
sudo docker compose down            # 保留数据卷
sudo docker compose down -v         # 连数据卷一起删除（重置基线）
```

## 四、用长期测试机制验证容器栈

容器栈只暴露端口，验收脚本在目标机上以环境变量指向它即可（不依赖 systemd/sudo）：

```bash
# 目标机上（或经 SSH）：
export JDRK_BASE_URL=http://127.0.0.1:18000
export JDRK_DB_MODE=local JDRK_DB_CLIENT=mysql JDRK_DB_USER=jdrk JDRK_DB_PWD=123456
export DB_HOST=127.0.0.1 DB_PORT=13306 DB_USER=jdrk DB_PWD=123456
export REDIS_HOST=127.0.0.1 REDIS_PORT=16379 REDIS_PWD=123456
```

开发机侧直接指容器地址：`JDRK_TEST_BASE_URL=http://<host>:18000`。

用本仓库执行器跑容器栈时，**app-home 要指向容器日志的宿主挂载目录**，否则 `verify_log.py` 读不到模块日志：

```powershell
$env:JDRK_DB_HOST='127.0.0.1'; $env:JDRK_DB_PORT='13306'
$env:JDRK_PLATFORM_DB_HOST='127.0.0.1'; $env:JDRK_PLATFORM_DB_PORT='13306'
pwsh tools/test/run-functional-suite.ps1 -SkipDev -TargetGroup core \
  -BaseUrl http://<host>:18000 -AppHome /home/wwt/jdrk-docker -RemoteDir /home/wwt/jdrk-accept
```

## 五、边界

- 串口组（serial）需要真实/虚拟串口与设备侧从站，容器内不覆盖；
- 运维组（ops）依赖 systemd 与 sudo，容器内不适用；
- core 组（采集/落地/报警/上报/同步/日志/票据/死信/编码/版本/导入导出/校时）可在容器栈上跑。
