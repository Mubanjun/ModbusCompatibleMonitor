# JDRK 水质监控平台 · Rust 后端（jdrk-monitor-backend）

RS-QXZ-M / RS-XJZ 系列水质监控主机的采集后端。按《需求与架构选型 v0.3》实现，
并依据对设备的**实测协议**（`docs/主机通讯协议文档.md`）修正了厂家文档中的错误参数。

| 项目 | 内容 |
| --- | --- |
| 语言 / 运行时 | Rust 2021 + Tokio（多线程异步） |
| 串口 | `serialport`（Windows / Linux 通用） |
| 本地缓冲库 | SQLite（`rusqlite bundled`，WAL） |
| 目标库 | MySQL 8.4（`mysql` 驱动，行别名 UPSERT） |
| 对外接口 | `axum` REST + WebSocket |
| 前端 | Qt6 QML（后续接入，通过 REST/WS 与本后端通信） |

---

## 1. 需求实现对照

| 需求 | 状态 | 实现位置 |
| --- | --- | --- |
| FR-01 按规约轮询 20 路，10 s 周期 | ✅ | `collector.rs` / `app.rs` 调度循环 |
| FR-02 A/B 系数换算 + 单位小数位 | ✅ | `domain.rs` `scale()`、`collector.rs` |
| FR-03 数据入库（时间/质量位/链路耗时） | ✅ | `store.rs` `ts_data` |
| FR-04 实时数据 + 在线状态 | ✅ | REST `/api/samples/latest` + WS `sample` / `link_status` |
| FR-05 历史查询 / CSV 导出 | ✅ | `/api/samples`、`/api/export.csv` |
| FR-06 上下限告警产生/复归 | ✅ | `alarm.rs` + `tb_alarm` / `alarm` 事件 |
| FR-07 通道配置管理 | ✅ | `/api/channels`（GET/PUT） |
| FR-08 报文调试台（原始 TX/RX） | ✅ | `/api/debug/frames`、`/api/debug/raw` + `frame` 事件 |
| FR-09 采集参数在线配置 | ✅ | `/api/links`、`/api/poll` |
| FR-10 串口掉线自动重连 | ✅ | `link.rs` `ensure_open()` 每次事务前重开 |
| FR-11 调试/生产链路一键切换 | ✅ | `/api/links/{name}/activate`（热切换，无需重启） |
| FR-12 自定义格式写入 MySQL 8.4 | ✅ | `forwarder.rs`（字段/表名可配） |
| FR-13 上传格式可配置 | ✅ | `config.toml` `forward.targets` + 表名 |
| FR-14 自定义 SQL 模板 | ⏳ 预留 | 目前为固定列，扩展点集中在 `forwarder.rs` |
| FR-15 多目标并行回传 | ⏳ 部分 | 已支持多 `[[forward.targets]]`，HTTP/MQTT 待加 |
| FR-16 断点续传与幂等 | ✅ | `outbox` 发件箱 + `ON DUPLICATE KEY UPDATE` |
| FR-17 回传状态可视化 | ✅ | `/api/forward/status` + `forward` 事件 |
| FR-18 本地后端可配置 | ✅ | `storage.sqlite_path` |
| FR-19 建表脚本 | ✅ | `schema/001_init.sql` + `migrations/001_sqlite.sql` |
| FR-20 兼容 MySQL 8.4 认证/语法 | ✅ | 行别名 `AS new`；账号用默认 `caching_sha2_password` |

---

## 2. 模块结构

`@
backend/
  Cargo.toml               依赖与 release profile（lto/strip）
  config/
    default.toml           真机默认配置（COM3 / 9600 / RS-Modbus）
    simulator.toml         无硬件自测配置（内置模拟器）
  migrations/001_sqlite.sql  本地 SQLite 建表
  schema/001_init.sql        MySQL 8.4 建表（目标库）
  src/
    main.rs                CLI 入口（--config / --bind）
    config.rs              配置模型（链路档案 / 通道 / 回传）
    domain.rs              领域模型（通道、采样、质量位、告警）
    events.rs              事件总线 + 推送事件模型
    collector.rs           轮询、解析、换算（双规约）
    alarm.rs               上下限告警状态机
    store.rs               SQLite：时序 / 发件箱 / 告警
    forwarder.rs           MySQL 幂等批量写入
    state.rs               共享状态
    api/mod.rs             REST 路由与处理器
    api/ws.rs              WebSocket 处理器
    modbus/
      crc.rs               CRC-16/Modbus（含自检向量）
      frame.rs             帧构造 / 解析 / 容错找帧
      transport.rs         串口传输（RTS 换向 + 静默收帧）
      link.rs              链路线程（独占串口、重试、事件）
      sim.rs               内置从站模拟器
`@

### 2.1 关键设计

1. **链路线程独占串口**：串口 I/O 是阻塞的，放在独立 OS 线程（`modbus/link.rs`）里，
   通过无锁通道接收请求、oneshot 返回结果，绝不阻塞 Tokio。
2. **RTS 换向**：该 USB-485 适配器必须“发送前置 RTS 有效、发完立即翻回”（实测报告根因 1）。
   配置 `rts_mode = "flip"` / `rts_active_low` 可调。
3. **静默 + CRC 收帧**：实测“按预期长度收帧”会截断帧尾（2/15 成功），
   因此默认 `recv_mode = "silence"`：边收边找合法 CRC 帧，命中即返回（15/15）。
4. **链路层重试**：默认每点 3 次、间隔 250 ms，兜住约 5% 的首字节同步丢失。
5. **发件箱幂等**：每轮写本地库的同时生成一条 `outbox` 批次；回传成功才标记 `sent_at`，
   失败累加 `attempts` 并记录原因，恢复后按序补传。
6. **双规约**：`rs_modbus`（从站地址 = 通道号，逐通道）与 `std_modbus`（单地址批量读）可配置切换。

---

## 3. 构建与运行

### 3.1 本机（Windows）特殊环境

本机 Windows 的 **schannel 不可用**（`SEC_E_NO_CREDENTIALS`），导致 cargo / git / curl 无法直连 HTTPS，
且 DSH 沙箱不允许写 `%USERPROFILE%\.cargo`。因此仓库内置了两件工具：

- `tools/local_crates_proxy.py`：用 Python(OpenSSL) 把 crates.io 稀疏索引与下载代理到 `http://127.0.0.1:8787`，供 cargo 走明文 HTTP。
- `tools/run-backend.ps1`：自动设置 `CARGO_HOME`、启动代理、把 Qt 自带 MinGW 加入 PATH，然后 `cargo run`。

> Linux / 正常联网的 Windows 无需这些，直接用系统 cargo 即可（本项目依赖均为跨平台 crate）。

`@powershell
# Windows 一键运行（真机）
powershell -ExecutionPolicy Bypass -File tools\run-backend.ps1 -Config config\default.toml

# 无硬件自测（内置模拟器）
powershell -ExecutionPolicy Bypass -File tools\run-backend.ps1 -Config config\simulator.toml
`@

`@bash
# Linux / 通用
cd backend
cargo build --release
./target/release/jdrk-monitor --config config/default.toml --bind 0.0.0.0:8790
`@

### 3.2 首次构建注意事项

- Rust 目标为 `x86_64-pc-windows-gnu`（本机 chocolatey 版 rustc），需要 MinGW 的 `gcc/dlltool`；
  Qt 安装自带的 `C:\Qt\Qt6.11.0\Tools\mingw1310_64\bin` 已足够。
- SQLite 采用 `rusqlite bundled`，首次编译会编译 C 源码（依赖上一条的 gcc）。
- 在 Linux 上建议改用系统 libsqlite3 或保留 bundled（需 `build-essential`）。

---

## 4. 配置说明（config/default.toml）

| 段 | 关键项 | 说明 |
| --- | --- | --- |
| `[server]` | `bind` | REST/WS 监听地址，默认 `127.0.0.1:8790` |
| `[device]` | `sn` `name` `location` | 设备标识，入库时使用 |
| `[poll]` | `interval_ms` `round_timeout_ms` | 轮询周期（FR-01 默认 10000） |
| `[storage]` | `sqlite_path` | 本地缓冲库路径 |
| `[forward]` | `enabled` `batch_size` `max_attempts` | 回传开关与批量 |
| `[[forward.targets]]` | `url` `table` | MySQL 连接串与目标表 |
| `[[links]]` | 见下表 | 链路档案（可多套，热切换） |
| `[[channels]]` | 见 `domain.rs` | 20 路通道档案 |

链路档案字段：

`@toml
[[links]]
name = "debug_485"
kind = "wired"            # wired | lora | simulator
port = "COM3"             # Linux 下如 /dev/ttyUSB0
baud = 9600               # 实测值（说明书 4800 是错的）
data_bits = 8
parity = "none"           # none | even | odd
stop_bits = 1
response_timeout_ms = 300
frame_gap_ms = 10
silence_ms = 200
recv_mode = "silence"     # silence | expected_len
retries = 3
retry_interval_ms = 250
rts_mode = "flip"         # none | auto | flip
rts_active_low = false
protocol = "rs_modbus"    # rs_modbus | std_modbus
std_slave_addr = 1
std_read_mode = "raw"     # raw | processed
chunk_regs = 124
`@

> 实测：单次读寄存器数 ≥ 125 时设备响应字节数会回绕，配置 `chunk_regs ≤ 124`。

---

## 5. 接口

REST 与 WebSocket 的完整定义见 `docs/后端API与WebSocket协议.md`。摘要：

- 健康/状态：`GET /api/health`、`GET /api/stats`、`GET /api/link/status`
- 通道：`GET /api/channels`、`PUT /api/channels/{no}`
- 数据：`GET /api/samples/latest`、`GET /api/samples`、`GET /api/export.csv`
- 链路：`GET /api/links`、`POST /api/links/{name}/activate`、`POST /api/links/reload`
- 调度：`POST /api/poll/now`、`PUT /api/poll`
- 调试台：`GET /api/debug/frames`、`POST /api/debug/raw`、`POST /api/debug/scan`、`POST /api/debug/read`、`POST /api/debug/write`
- 告警：`GET /api/alarms`、`POST /api/alarms/clear`
- 回传：`GET /api/forward/status`、`POST /api/forward/test`
- WebSocket：`GET /api/ws`（事件流 + 客户端命令）

---

## 6. MySQL 目标库

`@bash
mysql -uroot -p < schema/001_init.sql
# 创建账号（不要指定 mysql_native_password，MySQL 8.4 已禁用）
# CREATE USER 'monitor_rw'@'%' IDENTIFIED BY '<强密码>';
# GRANT SELECT,INSERT,UPDATE,DELETE ON env_monitor.* TO 'monitor_rw'@'%';
`@

数据库可用 Docker 起一个本地实例：

`@bash
docker run -d --name jdrk-mysql -e MYSQL_ROOT_PASSWORD=root -p 3307:3306 mysql:8.4
docker exec -i jdrk-mysql mysql -uroot -proot < schema/001_init.sql
`@

然后在配置里把 `[forward].enabled = true`，目标 `url` 指向该实例。

---

## 7. 自测结果

已完成的端到端自测（本机 Windows）：

| 项 | 结果 |
| --- | --- |
| `cargo check` / `cargo build` | 通过（0 error） |
| CRC 自检（厂家文档全部样例） | 通过 |
| 启动 + `GET /api/health` | 通过 |
| 采集循环（10 s / 2 s） | 通过 |
| SQLite 落盘（`/api/samples/latest`） | 通过（20 通道/轮） |
| WebSocket（hello/pong/snapshot/status/poll_now + 事件流） | 通过 |
| 内置模拟器 20 通道全 ok | 通过（一轮 ~90 ms） |
| MySQL 回传 | 未联机验证（本机 Docker 引擎未启动） |

真机 COM3 在本次会话末被其它进程占用（`拒绝访问`），因此真机链路未复测；
一旦串口释放，直接 `config/default.toml` 启动即可。

---

## 8. Linux 兼容性

- 全部依赖（tokio / axum / serialport / rusqlite / mysql / chrono）均支持 Linux。
- 串口名改为 `/dev/ttyUSB0` / `/dev/ttyS0`，并把运行账号加入 `dialout` 组即可。
- 内置模拟器（`kind = "simulator"`）不依赖任何串口，可在 Linux/CI 上完整跑通采集—存储—回传—WS。
- `config/*.toml`、SQLite 路径、MySQL 连接串均与平台无关。

---

## 9. 串口健壮性设计（重要）

> 完整分析与复现过程见 `docs/串口健壮性修复说明.md`。

早期版本有两个真实缺陷，会导致「串口阻塞后进程杀不掉、端口不释放」：

1. **传输层**：`serialport` 在 Windows 上以同步方式打开串口（无 `FILE_FLAG_OVERLAPPED`），
   同步 `ReadFile` 被驱动挂起后 **无法用 CancelIoEx 取消**，`TerminateProcess` 会一直等 IRP，进程卡死。
2. **IPC/策略层**：链路请求没有超时、`round_timeout_ms` 未实现、连续失败不重连、没有停机与取消机制。

现在的实现：

| 机制 | 位置 | 说明 |
| --- | --- | --- |
| Windows 重叠 I/O | `modbus/serial/win.rs` | `FILE_FLAG_OVERLAPPED` + `WaitForSingleObject` + **超时 `CancelIoEx`**；`Drop` 先取消再关闭句柄 |
| 跨线程取消 | `serial::Canceller` | `Arc<句柄>`，任意线程 `cancel()` 即可打断挂起的读写 |
| IPC 请求超时 | `link.rs::LinkHandle::request` | `timeout = response_timeout_ms*(retries+1) + retry_interval_ms*retries + 2000ms`；超时即取消底层 I/O |
| 连续失败重连 | `link.rs::Worker::transact` | 连续失败 >= 5 次自动关闭并重开串口（FR-10） |
| 优雅停机 | `link.rs::LinkRequest::Shutdown` + `app.rs` | Ctrl+C 关闭串口后退出，进程不再卡死 |
| 整轮软超时 | `collector.rs` `deadline` | `poll.round_timeout_ms` 真正生效，到点剩余通道标记 timeout |
| 采样 RTT | `collector.rs` | 从链路状态取 `last_rtt_ms` 写入每条采样 |

Linux 侧 `serial/posix.rs` 继续使用 `serialport`（POSIX VTIME 有界读），`Canceller` 为空实现。

---

## 10. 待办 / 未实现

1. FR-14 自定义 SQL 模板（当前固定列，扩展点在 `forwarder.rs`）。
2. FR-15 的 HTTP / MQTT 回传目标（`Sink` 抽象已预留）。
3. 通道档案的持久化编辑（目前改内存配置，重启回落到 TOML；`channel_config` 表已建好）。
4. Qt6 QML 前端接入（下一阶段）。
5. 真机复测（COM3 释放后）。
