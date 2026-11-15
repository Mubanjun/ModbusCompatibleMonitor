# 2 周 PoC 计划（2026-11）

> 对应 ADR-013（D10：先 PoC 再全面开工）。
> 目标：用最小闭环验证「ThingsPanel + IoTDB + VerneMQ/NanoMQ + Rust/模拟站点」能否支撑省级规模，并校准工期。

---

## 1. 环境

| 项 | 值 |
|---|---|
| 机器 | `10.34.80.169`（8C/14G/435G，Ubuntu 26.04，Docker 29.7.2） |
| 目录 | `/home/wei/poc/`（与 v1 的 `/home/wei/jdrk` 隔离） |
| 网络 | Docker Hub 走 `docker.1panel.live` 镜像加速 |
| 编排 | Docker Compose（单机），后续可迁 K8s |

组件与版本（以 PoC 当天拉取为准）：

| 组件 | 版本 | 用途 |
|---|---|---|
| ThingsPanel community | 最新 release | 业务底座、模板、看板 |
| PostgreSQL | 16 | ThingsPanel 元数据 |
| Apache IoTDB | 1.3+/2.x | 时序库 |
| VerneMQ | 2.2.x | 区域/省 Broker |
| NanoMQ | 0.25.x | 站点 Broker |
| EMQX（仅对照） | 5.8.x | 可选基线对比，不进入交付 |

---

## 2. 两周任务

### 第 1 周：单链路打通

| 天 | 任务 | 产出 |
|---|---|---|
| D1 | 部署 PG + ThingsPanel + IoTDB + VerneMQ/NanoMQ；健康检查 | 访问地址、账号、compose 文件 |
| D2 | ThingsPanel 设备模板 import：Modbus RTU/TCP 模板 | 模板 JSON、导入截图 |
| D3 | 启动 Modbus 模拟器（diagslave/自研），Agent 采集 → MQTT | 采集日志、原始帧 |
| D4 | MQTT → 写入服务 → IoTDB；建库/测点模板 | 表结构、写入延迟 |
| D5 | ThingsPanel 看板展示实时值/曲线 | 页面截图 |

### 第 2 周：规模与可靠性

| 天 | 任务 | 产出 |
|---|---|---|
| D6 | 1000 站模拟器（60s 打包上报，每站 100 点） | 模拟器代码、消息速率 |
| D7 | 压测 1 小时：写入、查询 P95、CPU/内存/磁盘 | 资源曲线、P95 表 |
| D8 | 级联最小链路：NanoMQ → VerneMQ bridge；主题/ACL 验证 | 桥接配置、消息穿透 |
| D9 | 断网续传：停 Broker 10 分钟，恢复后补传；校验丢点 | 丢点率、追平时间 |
| D10 | 多租户/RBAC 改造成本评估（读代码 + 列出改造点）；白标点清单 | 评估报告、改造清单 |

---

## 3. 验收指标

| 编号 | 指标 | 目标 |
|---|---|---|
| POC-01 | 模板导入到出数 | ≤5 分钟 |
| POC-02 | 1000 站 1 小时 | 5xx=0；消息不丢 |
| POC-03 | IoTDB 写入 | ≥5000 点/s，P95 写入延迟 ≤200ms |
| POC-04 | 查询 | 单站 7 天曲线 P95 ≤1s |
| POC-05 | 断网 10 分钟 | 恢复后 5 分钟内追平，丢点 0 |
| POC-06 | 级联 | 主题穿透正确，无回环/重复 |
| POC-07 | 资源 | 单机 CPU <70%，内存 <8GB |
| POC-08 | 改造成本 | 多租户/RBAC 改造点 ≤15 处，人周估算有依据 |

---

## 4. 交付物

1. `poc/docker-compose.yml` + `.env.example` + 启动脚本；
2. 模板样例（Modbus RTU、MQTT 网关）与导入说明；
3. 1000 站模拟器（Go 或 Rust 单文件）；
4. 压测报告：资源曲线、P95、丢点率、瓶颈分析；
5. 级联与断网续传记录；
6. ThingsPanel 改造清单（多租户、白标、模板扩展、集群）；
7. PoC 结论：是否继续、工期校准、风险清单。

---

## 5. 风险与回退

| 风险 | 应对 |
|---|---|
| ThingsPanel 部署依赖外部镜像/网络受限 | 提前拉镜像；必要时用源码构建 |
| IoTDB 写入模型不适配 | 备选 TimescaleDB（旁路分析）或调整 schema |
| 1000 站模拟器压不满 | 提升到 3000 站或缩短周期 |
| 级联桥接配置复杂 | 先用单跳验证，再做三级 |
| PoC 不通过 | 回到 D1 重新评估底座（IoTSharp/自研） |

---

## 6. 出口判定

- 全部 POC-01~08 达标 → 进入 P1 全面开工；
- 仅 POC-03/04 不达标 → 调整时序 schema 或换 TimescaleDB，不影响底座；
- POC-01/02/06/08 不达标 → 暂停，重新评估底座与级联方案。
