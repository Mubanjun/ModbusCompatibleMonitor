# JDRK 水质监控平台 · 部署与打包

本目录提供在**其他电脑上安装部署**所需的打包脚本：

| 平台 | 方式 | 脚本 | 产物 |
| --- | --- | --- | --- |
| Debian / Ubuntu（及任何装有 dpkg 的系统） | .deb 包 | linux/build-package.sh | jdrk-monitor_<版本>_<架构>.deb |
| Fedora / RHEL / openSUSE | .rpm 包 | 同上（--formats rpm） | jdrk-monitor-<版本>-<release>.<dist>.rpm |
| Windows 10/11 x64 | Inno Setup 安装程序 | windows/build-installer.ps1 | JDRK-Monitor-<版本>-setup.exe |

两边都会安装 **后端服务**（常驻采集 + REST/WebSocket）和**可选的 Qt6 上位机**；
上位机可以装在同一台机器，也可以装在另一台机器上通过 --server 连接远端后端。

```
packaging/
  README.md                     本文档
  linux/
    build-package.sh            打包主脚本（deb + rpm）
    jdrk-monitor.service        systemd 服务单元（安装为 /usr/lib/systemd/system/）
    jdrk-monitor.desktop        上位机桌面快捷方式
    deb/control.in              Debian 控制文件模板
    deb/{postinst,prerm,postrm}.sh  Debian 维护脚本（建用户/装服务/卸载）
    rpm/jdrk-monitor.spec.in    RPM spec 模板
  windows/
    build-installer.ps1         一键：编译 -> windeployqt -> ISCC -> 安装包
    jdrk-monitor.iss            Inno Setup 脚本（组件/任务/服务注册）
    jdrk-service.ps1            Windows 服务管理（计划任务托管 + 优雅停机）
```

---

## 一、Linux（.deb / .rpm）

### 1.1 构建依赖

```bash
# Debian / Ubuntu
sudo apt install build-essential pkg-config dpkg-dev rpm \
                 qt6-base-dev qt6-declarative-dev qml6-module-qtquick-controls
# Fedora / RHEL
sudo dnf install gcc-c++ make pkgconf rpm-build \
                 qt6-qtbase-devel qt6-qtdeclarative-devel qt6-qtquickcontrols2
# Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### 1.2 打包

```bash
./packaging/linux/build-package.sh                  # 后端 + 前端，生成 deb 与 rpm
./packaging/linux/build-package.sh --formats deb    # 只生成 deb
./packaging/linux/build-package.sh --no-frontend    # 只装后端（无 Qt 依赖，适合纯采集服务器）
./packaging/linux/build-package.sh --skip-build     # 复用已有构建产物，只重新打包
./packaging/linux/build-package.sh --stage-only     # 只生成 staging 目录（排错用）
./packaging/linux/build-package.sh --version 0.2.0 --release 2 --outdir /tmp/dist
```

常用参数：`--version`（默认取 `backend/Cargo.toml`）、`--release`（RPM 的 release 号）、
`--prefix`、`--jobs`、`--outdir`。执行 `--help` 查看全部。

> 打包脚本会把 `backend/config/default.toml` **派生**成安装配置（改监听地址、SQLite 路径、
> 串口设备名），因此配置只有一份数据源，不会出现两份配置漂移。

### 1.3 安装后的文件布局

| 路径 | 内容 |
| --- | --- |
| /usr/bin/jdrk-monitor | 后端可执行文件 |
| /usr/bin/jdrk-monitor-ui | 上位机（--no-frontend 时不安装） |
| /etc/jdrk-monitor/config.toml | 配置（**conffile**：升级不覆盖，管理员可改） |
| /usr/lib/systemd/system/jdrk-monitor.service | systemd 服务单元 |
| /usr/share/applications/jdrk-monitor.desktop | 桌面入口（上位机） |
| /usr/share/doc/jdrk-monitor/ | 文档、copyright |
| /usr/share/jdrk-monitor/examples/ | 示例配置（simulator / hardware 等） |
| /var/lib/jdrk-monitor/ | 运行数据（SQLite 缓冲 monitor.db） |
| /var/log/jdrk-monitor/ | 保留给日志（当前后端日志走 stdout -> journald） |

### 1.4 安装、升级、卸载

```bash
sudo dpkg -i jdrk-monitor_0.1.0_amd64.deb          # 缺依赖时：sudo apt -f install
sudo apt install ./jdrk-monitor_0.1.0_amd64.deb    # 推荐：自动装依赖
sudo rpm -ivh jdrk-monitor-0.1.0-1.x86_64.rpm
sudo systemctl status jdrk-monitor
sudo nano /etc/jdrk-monitor/config.toml            # 改串口/地址
sudo systemctl restart jdrk-monitor

sudo dpkg -r jdrk-monitor        # 卸载，保留配置与采集数据
sudo dpkg -P jdrk-monitor        # purge：删除配置 + /var/lib 数据（谨慎）
sudo rpm -e jdrk-monitor
```

安装脚本会：创建系统用户 `jdrk-monitor`（无登录权限）-> 加入串口组
（`dialout`/`uucp`/`tty` 中存在的那些）-> 创建数据与日志目录 -> 注册并启动服务。
升级时若服务在运行会自动 restart，从而用上新二进制。

### 1.5 服务行为（重要）

- 服务以 `Type=simple` 常驻，`Restart=on-failure`。**串口没插或占用时后端不会退出**，
  它会自动重连（连续 5 次失败后重开端口），所以设备后接入也能恢复。
- **优雅停机**：后端同时监听 `SIGINT` 与 `SIGTERM`，收到后先关闭串口再退出；
  `systemctl stop` 走的就是 SIGTERM 路径。也可用 `POST /api/shutdown`。
  请优先用 `systemctl stop`，不要 `kill -9`（强杀可能让 USB-CDC 驱动锁住端口，需拔插）。
- 服务单元带轻量加固：`ProtectSystem=strict` + `ReadWritePaths=/var/lib/jdrk-monitor`，
  所以 **SQLite 路径必须位于 /var/lib/jdrk-monitor**（默认已是），否则写库会失败。
- 默认监听 `0.0.0.0:8790`（便于远端上位机连接）。仅本机使用时改回 `127.0.0.1:8790`。
  对外暴露时记得放行防火墙：`sudo ufw allow 8790/tcp` 或
  `sudo firewall-cmd --add-port=8790/tcp --permanent`。

### 1.6 串口设备

- 默认配置里 485 直连为 `/dev/ttyUSB0`、LoRa 为 `/dev/ttyUSB1`。
- USB 转 485 适配器的编号会随插拔变化，**强烈建议改用固定软链接**：
  `ls -l /dev/serial/by-id/`，然后把 config.toml 的 port 改为
  `/dev/serial/by-id/usb-XXXX-if00-port0`。
- 设备权限应为 `root:dialout 0660`；若自建 udev 规则，确保 `jdrk-monitor` 用户在对应组内。

---

## 二、Windows（Inno Setup）

### 2.1 构建依赖

| 组件 | 说明 |
| --- | --- |
| Rust（cargo） | 编译后端 |
| Qt 6 + MinGW | 编译上位机（默认自动探测 `C:\Qt\Qt6.*\*\mingw_64`） |
| Inno Setup 6.3+ | 生成安装包（ISCC.exe，自动探测常见安装路径） |

### 2.2 打包

```powershell
.\packaging\windows\build-installer.ps1                # 全自动
.\packaging\windows\build-installer.ps1 -SkipBuild     # 复用已有产物，只重新打包
.\packaging\windows\build-installer.ps1 -SkipFrontend  # 只打包后端（安装包不含 GUI）
.\packaging\windows\build-installer.ps1 -Version 0.2.0
```

可用参数：`-QtDir`、`-InnoSetupPath`、`-OutDir`、`-StageDir`、`-SkipBackend`。

脚本流程：编译后端(release) -> 编译前端(qmake) -> 生成配置 -> windeployqt 收集 Qt
运行库与 QML 模块（`--qmldir frontend\qml`，自研 QML 打进 qrc，必须让 windeployqt
扫描源码才知道要拷哪些模块）-> 通过环境变量传入版本号与路径 -> 调用 ISCC。

> 版本号与路径通过**环境变量 + ISPP `GetEnv()`** 传给 Inno Setup（`JDRK_APP_VERSION` /
> `JDRK_STAGE_DIR` / `JDRK_OUT_DIR` / `JDRK_WITH_UI`）。既不用 `iscc /D<name>=<含反斜杠路径>`
> （会被 PowerShell 的参数转义破坏），也不用 `#include` 的 ISPP 定义文件
> （受 ISPP 文件编码与行处理影响，行为不稳定）。

> **维护提醒**：`packaging/windows/*.ps1` 与 `jdrk-monitor.iss` 都含中文，必须保存为
> **UTF-8 with BOM**。安装程序调用的是 Windows PowerShell 5.1，它会把无 BOM 的 UTF-8
> 按系统 ANSI 解码，导致中文乱码甚至语法错误；Inno Setup 同理（无 BOM 时按 ANSI 读，
> 安装向导会显示乱码）。若编辑器把 BOM 去掉了，补回方式：

```powershell
$p = "packaging\windows\jdrk-service.ps1"
$t = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($p))
[IO.File]::WriteAllText($p, $t, (New-Object Text.UTF8Encoding($true)))
```

### 2.3 安装后的文件布局

| 路径 | 内容 |
| --- | --- |
| %ProgramFiles%\JDRK Monitor\backend\jdrk-monitor.exe | 后端 |
| %ProgramFiles%\JDRK Monitor\ui\ | 上位机 + Qt 运行库/QML 模块（windeployqt） |
| %ProgramFiles%\JDRK Monitor\tools\jdrk-service.ps1 | 服务管理脚本 |
| %ProgramFiles%\JDRK Monitor\docs\ | 文档 |
| %ProgramData%\JDRK\config.toml | 配置（**升级不覆盖、卸载不删除**） |
| %ProgramData%\JDRK\data\monitor.db | 采集数据（SQLite） |
| %ProgramData%\JDRK\logs\backend.log | 后端 stdout/stderr 日志 |

### 2.4 安装程序选项

- **组件**：`backend`（必装）、`ui`（上位机）、`docs`。
- **任务**：
  - 注册为开机自启动服务（计划任务，SYSTEM 权限）—— 默认勾选；
  - 添加防火墙入站规则放行 8790 —— 默认勾选（远端上位机需要）；
  - 创建桌面快捷方式。
- 安装结束可直接勾选「启动 JDRK 水质监控平台」。

### 2.5 服务托管方式（为什么不用 Windows 服务）

后端是控制台程序，**没有实现 Windows 服务控制管理器（SCM）接口**，
用 `New-Service` / `sc create` 注册会报 **1053（服务没有及时响应启动或控制请求）**，
也无法通过 SCM 正常停止。因此安装程序注册的是**计划任务**：

- 触发：开机；身份：`SYSTEM`（不需要用户登录，关掉控制台也能采）；
- 失败自动重启：间隔 1 分钟、最多 5 次；
- 无执行时长上限，重复启动时忽略新实例；
- 任务实际执行 `%ProgramData%\JDRK\logs\run-backend.cmd`（把 stdout/stderr 重定向到
  `backend.log`，并固定工作目录）。

管理命令（`-Action`）：

```powershell
powershell -ExecutionPolicy Bypass -File "$env:ProgramFiles\JDRK Monitor\tools\jdrk-service.ps1" -Action Status
powershell -ExecutionPolicy Bypass -File "...\jdrk-service.ps1" -Action Restart
powershell -ExecutionPolicy Bypass -File "...\jdrk-service.ps1" -Action Stop      # 优先 /api/shutdown
powershell -ExecutionPolicy Bypass -File "...\jdrk-service.ps1" -Action Uninstall # 停服务+注销任务+删防火墙规则
```

或直接用「开始菜单 -> 后端服务状态」快捷方式查看。

### 2.6 升级与卸载

- 升级：直接运行新版本安装包，配置与数据保留（`%ProgramData%\JDRK` 不动）。
- 卸载：控制面板卸载 -> 自动停服务、注销计划任务、删除防火墙规则；
  `%ProgramData%\JDRK`（配置+数据）**默认保留**，确认不需要时手工删除。

---

## 三、前后端分离部署

后端只需要一台常驻机器（可无图形界面），上位机可以装在办公电脑上：

```bash
# 服务器：仅后端，监听所有网卡
sudo dpkg -i jdrk-monitor_0.1.0_amd64.deb
sudo sed -i 's/^bind = .*/bind = "0.0.0.0:8790"/' /etc/jdrk-monitor/config.toml
sudo systemctl restart jdrk-monitor
```

```powershell
# 上位机：指向服务器地址（命令行 > 环境变量 > 上次保存 > 默认 127.0.0.1:8790）
jdrk-monitor-ui.exe --server http://192.168.1.10:8790
# 或
$env:JDRK_SERVER = "http://192.168.1.10:8790"; jdrk-monitor-ui.exe
```

在界面「链路管理」页切换服务地址时，地址会被记住（QSettings）并自动重连 REST + WebSocket。

---

## 四、常见问题（排错）

| 现象 | 原因与处理 |
| --- | --- |
| 服务已启动但串口打开失败 | `journalctl -u jdrk-monitor -n 100` 看日志；确认 `port` 设备存在、`id jdrk-monitor` 已在 dialout 组、设备权限为 `root:dialout 0660`；USB 编号漂移请改用 `/dev/serial/by-id/...`。 |
| `dpkg -i` 报依赖缺失 | 改用 `sudo apt install ./xxx.deb`（自动装依赖）或 `sudo apt -f install`。 |
| 服务启动后写库失败 | 服务单元带 `ProtectSystem=strict`，SQLite 必须位于 `/var/lib/jdrk-monitor` 下。 |
| 远端上位机连不上 | 确认 `bind = "0.0.0.0:8790"`、防火墙放行 8790，并在上位机执行 `curl http://<主机>:8790/api/health` 验证。 |
| 别的电脑上 GUI 打不开 | 安装时需勾选 `ui` 组件；检查 `%ProgramFiles%\JDRK Monitor\ui` 下有 `Qt6Quick.dll` 与 `qml\QtQuick\Controls`；启动日志在用户目录 `jdrk-ui.log`。 |
| 编译时报 `Cannot acquire license to use qtframework` | 该 Qt 安装带商用许可证服务且被并发占用（Qt 6.11 在线安装版会出现）。先重启 Qt License Service，或编译前设 `$env:QTFRAMEWORK_BYPASS_LICENSE_CHECK = "1"`。 |
| `New-Service` / `sc create` 报 1053 | 预期行为：后端未实现 SCM 接口，请用 `jdrk-service.ps1 -Action Install`（计划任务托管）。 |

---

## 五、已知限制与后续可做

- **前端未做跨发行版二进制分发**：.deb/.rpm 在目标发行版上编译（Qt 与 glibc 版本差异），
  运行库依赖由发行版提供；若需要免依赖分发，可改用 linuxdeploy / AppImage 或静态链接 Qt。
- **openSUSE** 的 RPM 依赖名与 Fedora 不同（qt6-qtbase / libQt6Core6），
  需要时改 `build-package.sh` 里的 `RPM_QT_REQUIRES`，或只用 `--formats deb`。
- 未做代码签名（Windows 会弹 SmartScreen 提示；Linux 未做 GPG 签名与软件源）。
- 未做自动更新；升级靠重新安装包。
- 后端日志只输出到 stdout（Linux 交给 journald，Windows 重定向到 backend.log），
  未做按大小/日期轮转；`run-backend.cmd` 可自行追加轮转命令。
- 数据库只有初始建表（`backend/migrations/001_sqlite.sql`），
  后续加字段需要版本化迁移 + 打包时的升级脚本。
