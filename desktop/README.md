# desktop/ —— 终端管理端（TUI）

> **V1.1 需求变更（2026-10）**：取消 Qt 图形界面与 Tauri 托盘壳，客户端改为 **TUI 终端管理端**。
> 原 `desktop/src-tauri/`（Tauri 2 托盘壳）、`desktop/ui/`（WebView 资产）、`desktop/BUILD.md` 与 Tauri 专属
> 构建脚本已移除；可复用逻辑（配置解析、HTTP、服务控制、超时、离线重置、单测）已迁移至 `desktop/tui/`。

| 路径 | 说明 |
|---|---|
| `tui/` | Rust + ratatui 终端管理端（FR-QT-01~10），详见 `tui/README.md` |

进程管理沿用项目自研方案：后端由**独立看门狗**守护，Linux 走 systemd、Windows 走 WinSW/计划任务；
**不引入 pm2**，TUI 只下发控制指令。

变更依据：`docs/需求变更-TUI与自研进程管理工作计划.md`、`docs/仁科兼容环境监测平台需求规格说明书.md`（V1.1 §4.2）。
