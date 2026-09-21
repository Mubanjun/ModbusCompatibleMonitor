# ModbusCompatibleMonitor · JDRK 水质监控平台

针对 **RS-QXZ-M / RS-XJZ 系列环境监控主机**的采集与监控平台：
**Rust 后端**（双 Modbus 规约采集 / SQLite 缓冲 / MySQL 回传 / REST+WebSocket）+ **Qt6 QML 前端**。

> 本仓库同时包含对厂家 RS-Modbus 规约文档的**实测勘误**：
> 文档写的 4800 波特率、仅 0x03 功能码、只有 2 个寄存器，均与实测不符。

---

## 目录结构

| 目录 | 内容 |
| --- | --- |
| `backend/` | Rust 后端（axum + tokio + 重叠串口 I/O + rusqlite + mysql） |
| `frontend/` | Qt 6 QML 前端（自研 WebSocket、Canvas 图表） |
| `docs/` | 需求、实测协议、后端接口、串口健壮性修复等文档 |
| `probe/` | 通讯探测脚本与实测数据（含 `hardware_result.json`） |
| `tools/` | 构建/运行/停机/验证脚本、crates 本地代理、串口探测 |

## 关键文档

- 主机通讯协议（实测）：`docs/主机通讯协议文档.md`
- 后端 API 与 WebSocket：`docs/后端API与WebSocket协议.md`
- 串口阻塞/端口释放根因与修复：`docs/串口健壮性修复说明.md`
- 需求与架构选型：`docs/需求与架构选型.md`
- 后端说明：`backend/README.md`　前端说明：`frontend/README.md`

## 快速开始

### 后端（无硬件：内置从站模拟器）

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-backend.ps1 -Config config\simulator.toml
```

### 后端（真机：经 HHD 虚拟串口 COM1 路由到物理 COM3）

```powershell
backend\target\debug\jdrk-monitor.exe --config config\hardware_com1.toml
```

### 前端

```powershell
powershell -ExecutionPolicy Bypass -File tools\build-frontend.ps1
powershell -ExecutionPolicy Bypass -File tools\run-frontend.ps1
```

## 实测要点（详见文档）

| 项目 | 厂家文档 | 实测 |
| --- | --- | --- |
| 从站口波特率 | 4800 | **9600 8N1** |
| 规约 | RS-Modbus | **RS-Modbus**（地址=通道，1~32 在线；37~42 为 RTC） |
| 功能码 | 仅 0x03 | **0x03 / 0x04 / 0x06 / 0x10** |
| 寄存器 | 仅 0000/0001 | **每通道 0~63 共 64 个** |
| 异常应答 | 未说明 | 非法功能码/越界地址**静默、无异常帧** |
| 单次读上限 | 未说明 | 寄存器数 >=125 时字节数回绕，建议 <=124 |

## 串口健壮性（重要）

- Windows 侧使用**重叠 I/O**（FILE_FLAG_OVERLAPPED），**读前先查 COMSTAT.cbInQue**，不产生挂起的内核 IRP；
- IPC 请求超时 + 强制 CancelIoEx；连续失败自动关闭并重连；Ctrl+C / 控制台关闭事件先释放串口；
- 提供 `POST /api/shutdown` 与 `tools/stop-backend.ps1`：
  **停止服务请用优雅停机，不要用任务管理器强杀**（强杀可能让 USB-CDC 驱动锁住端口，需拔插模块）。

## 许可证

待定（如无特殊要求建议 MIT）。
