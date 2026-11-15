# jdrk-tui —— 终端管理端（TUI）

> 取代原 Qt/Tauri 桌面壳（见 `docs/桌面端FR-QT专项决策.md`、`docs/需求变更-TUI与自研进程管理工作计划.md`）。
> 需求编号 FR-QT-01~10 保持稳定，语义已由 Qt 图形界面调整为 TUI 终端界面。

## 设计原则

1. **只做壳**：进程守护与自恢复始终由独立看门狗负责，TUI 只下发指令、展示状态（FR-QT-02、FR-WD-01）。
2. **无图形依赖**：Rust + ratatui + crossterm，单二进制，本地控制台与 SSH 通用；无 WebView/Qt 运行时。
3. **开源复用**：ratatui/crossterm（MIT）、sysinfo（MIT，指标）、clap（参数）、webbrowser（打开看板）、reqwest（HTTP）。

## FR-QT 覆盖

| 需求 | 实现 |
|---|---|
| FR-QT-01 状态总览 | 心跳（pid/运行时长/自检项）+ CPU/内存/磁盘实时刷新（sysinfo） |
| FR-QT-02 服务启停 | 「服务控制」页启动/停止/重启，二次确认后经看门狗/系统服务下发 |
| FR-QT-03 依赖检查与修复 | 展示心跳中的自检项，回车打开看门狗页 |
| FR-QT-04 离线重置超管密码 | 「离线重置」页，前端校验后端已停机后复用后端 CLI |
| FR-QT-05 日志查看与导出 | 「日志」页列日志文件、尾行查看、级别/关键字过滤 |
| FR-QT-06 系统参数配置 | 「参数」页展示环境变量化配置，回车打开 Web 配置页 |
| FR-QT-07 备份恢复入口 | 「备份恢复」页，回车打开 Web 备份页 |
| FR-QT-08 终端驻留与无头模式 | `--headless` 非交互输出；可在 tmux/screen 内驻留 |
| FR-QT-09 一键打开看板 | 「服务控制」页「打开看板」调用系统默认浏览器 |
| FR-QT-10 版本检查 | 「关于」页展示 `/monitor/system/version` |

## 环境变量（现场免改代码）

| 变量 | 默认 | 说明 |
|---|---|---|
| JDRK_HOME | 用户主目录 → exe 目录 | 工作目录（决定脚本与 jar 相对路径） |
| JDRK_API_BASE | http://127.0.0.1:8000 | 后端地址 |
| JDRK_WEB_BASE | 同 API | 看板地址 |
| JDRK_JAR | 自动候选 | 后端包路径 |
| JDRK_JAVA | 随包 runtime → PATH | 离线重置用的 java |
| JDRK_START_CMD / JDRK_STOP_CMD / JDRK_RESTART_CMD | 空 | 自定义启停命令，缺省走看门狗/systemd |
| JDRK_SYSTEMD_UNIT | jdrk-watchdog.service | Linux systemd 单元名 |
| JDRK_MONITOR_INTERVAL_SECONDS | 2 | 状态刷新周期（秒），亦可用 `--interval` |
| JDRK_RESET_TENANT | 0 | 离线重置租户 |
| JDRK_RESET_TIMEOUT_SECONDS | 120 | 离线重置 CLI 超时 |
| JDRK_SERVICE_TIMEOUT_SECONDS | 180 | 启停/重启命令超时 |
| JDRK_LOG_DIR | `<JDRK_HOME>/logs` | 日志目录 |

## 构建

```powershell
# 常规（目标机，有网络与 MSVC/MinGW）
cd desktop/tui
cargo build --release      # 产物 target/release/jdrk-tui(.exe)

# 本沙箱（crates.io 直连被拦 + 需 gcc 编译 ring）
$env:CARGO_HOME='<repo>\.cargo-home'
$env:PATH='<repo>\.cargo-home\toolchain\w64devkit\bin;' + $env:PATH
$env:CC_x86_64_pc_windows_gnu='gcc'
py -3 _extract/crates_proxy.py        # 另开一个终端，起本地稀疏索引代理
cargo build --release
```

## 运行

```bash
./jdrk-tui                 # 交互式 TUI
./jdrk-tui --headless      # 非交互：输出一次状态后退出
./jdrk-tui --interval 2    # 刷新周期（秒）
```

键位：`Tab`/`←→` 切换页，`1`~`7` 直达，`r` 刷新，`Enter` 执行，`f` 日志过滤，`q` 退出；启停/重启需 `y` 二次确认。

## 测试

```bash
cargo test    # 8 项：路径解析/超时钳位/命令超时/正常路径/日志过滤/日志只读尾部
```

## 与旧桌面壳的关系

原 `desktop/src-tauri/`（Tauri 2 托盘壳）在 V1.1 需求变更后**废弃**：托盘/WebView 与「无需图形桌面」的目标冲突。
可复用逻辑（配置解析、HTTP、服务控制、超时、离线重置、单测）已迁移至本 crate；`desktop/ui/`（WebView 资产）随旧壳一并废弃。
