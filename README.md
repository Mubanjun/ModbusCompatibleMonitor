# 仁科兼容环境监测平台（ModbusCompatibleMonitor）

一套面向环境/水质监测场景的开源监测平台：在不使用厂商私有协议的前提下，通过标准 Modbus 与公开规约接入仁科及第三方传感器、数采主机，提供设备管理、实时监控、历史查询、报警、上报与迁移能力。

> 当前版本为 **v1.4（单机部署形态）**。
> 下一阶段将改为「前后端分离 + 多机接入」架构，方案讨论见
> [技术方案-前后端分离与多机接入-讨论稿](docs/技术方案-前后端分离与多机接入-讨论稿.md)。

## 功能概览

| 模块 | 能力 |
|---|---|
| 设备与端口 | 串口 / Modbus RTU / ASCII / Modbus TCP / RFC2217；设备分组树、位置与地图 |
| 采集 | 多级采集间隔、超时与重试、熔断退避、信号与原始帧记录 |
| 实时监控 | 实时值、历史曲线、多视图、仪表盘 |
| 报警 | 阈值/离线规则、防抖抑制、通知与报警记录 |
| 数据上报 | MQTT、目标数据库（MySQL/PostgreSQL/SQL Server/KingbaseES 等） |
| 数据同步与迁移 | 仁科原库导入、在线增量同步、一致性校验、并存与回退 |
| 系统 | 用户/角色/菜单/数据权限、审计日志、看门狗与自恢复 |
| 终端管理 | Rust + ratatui TUI（本地运维，无图形依赖） |

## 技术栈

- **后端**：Java 21、Spring Boot 3.5、MyBatis-Plus、Sa-Token、Liquibase、Gradle
- **前端**：Vue 3、TypeScript、Arco Design、Vite
- **存储**：MySQL 8、Redis、SQLite（本地暂存）
- **终端**：Rust + ratatui
- **部署**：Docker Compose、systemd / Windows 计划任务

## 仓库结构

| 目录 | 说明 |
|---|---|
| `backend/` | 后端多模块工程（基座 + 协议/设备/存储/上报/同步等） |
| `frontend/` | Web 前端（Vue 3 + Vite） |
| `desktop/` | 终端管理端（Rust TUI） |
| `deploy/` | Docker 与 Linux 部署脚本 |
| `tools/docker/` | 一键 Docker 编译与冒烟测试脚本 |
| `docs/` | 需求规格、部署验收手册、需求核查表、架构讨论稿 |

## 快速开始（Docker）

前置：Docker 24+ 与 Docker Compose v2。

```bash
# 1) 编译前端与后端，启动 MySQL + Redis + 后端，并等待心跳接口
bash tools/docker/build-and-test.sh

# 2) 打开 http://127.0.0.1:18000
#    默认账号 admin / admin123（首次登录后请立即修改）
```

常用端口：后端 `18000`、MySQL `13306`、Redis `16379`（均可在 `deploy/docker/.env` 覆盖）。

停止与清理：

```bash
docker compose -f deploy/docker/docker-compose.build.yml down
docker compose -f deploy/docker/docker-compose.build.yml down -v   # 连同数据卷
```

## 本地开发

```bash
# 后端（需 JDK 21；MySQL/Redis 连接通过环境变量覆盖）
cd backend
./gradlew :continew-server:bootRun

# 前端
cd frontend
pnpm install
pnpm dev            # http://127.0.0.1:5173，代理到后端 8000
```

提交前门禁：

```bash
cd frontend && pnpm lint && pnpm typecheck && pnpm build
cd backend && ./gradlew check
```

## 文档

- [需求规格说明书](docs/仁科兼容环境监测平台需求规格说明书.md)
- [现场部署与验收手册](docs/现场部署与验收手册.md)
- [需求实现核查表](docs/需求实现核查表.md)
- [技术方案讨论稿：前后端分离与多机接入](docs/技术方案-前后端分离与多机接入-讨论稿.md)
- [开源底座选型与魔改方案](docs/开源底座选型与魔改方案.md)
- **v2 架构基线**
  - [架构决策记录（ADR-001~007）](docs/v2/00-架构决策记录.md)
  - [总体架构](docs/v2/01-总体架构.md)
  - [多机接入与级联规范](docs/v2/02-多机接入与级联规范.md)
  - [设备模板规范](docs/v2/03-设备模板规范.md)
  - [部署与前后端分离](docs/v2/04-部署与前后端分离.md)
  - [迁移与实施计划](docs/v2/05-迁移与实施计划.md)

## 安全说明

- 仓库不包含任何现场凭据、内部网络拓扑或厂商安装包；
- 默认口令仅用于本地开发，部署到现场前必须修改数据库、Redis 与管理员口令；
- 发现安全问题请通过 GitHub Security Advisories 私下报告，不要开公开 Issue。

## 许可证

MIT，见 [LICENSE](LICENSE)。
