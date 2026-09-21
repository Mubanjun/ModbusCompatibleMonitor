# JDRK 水质监控平台 · Qt6 QML 前端

通过 **REST + WebSocket** 对接 Rust 后端（`backend/`）的水质监控上位机，涵盖实时看板、历史曲线、
告警、通道配置、链路管理与报文调试台六个页面。

| 项目 | 内容 |
| --- | --- |
| 框架 | Qt 6.11（Qt Quick / QML）+ C++17 |
| 通信 | REST（QNetworkAccessManager）+ 自研 WebSocket（QTcpSocket，RFC6455） |
| 图表 | QML Canvas 手绘折线图（不依赖 QtCharts） |
| 样式 | Qt Quick Controls 2 · Fusion（可定制非原生样式） |
| 架构 | 后端服务常驻，前端仅作为客户端；两者可分别部署 |

> 之所以自己实现 WebSocket、图表与主题：本机 Qt 安装未包含 `QtWebSockets` / `QtCharts` 模块，
> 且自研方案依赖更少、跨平台更稳。若你的 Qt 安装了对应模块，也可平滑替换。

---

## 1. 目录结构

`@
frontend/
  jdrk-frontend.pro        qmake 工程文件
  qml.qrc                  QML 资源清单
  src/
    main.cpp               入口（消息处理器 + Fusion 样式 + 加载 Main.qml）
    rest_client.{h,cpp}    REST 客户端
    ws_client.{h,cpp}      自研 WebSocket 客户端
    channel_model.{h,cpp}  20 路通道实时值模型
    alarm_model.{h,cpp}    告警模型
    frame_model.{h,cpp}    报文日志模型
    app_controller.{h,cpp} 核心控制器（对接后端、聚合状态）
  qml/
    Main.qml               主框架（侧边导航 + 状态栏 + 页面栈 + 消息条）
    Theme.qml              主题常量
    components/
      ChannelCard.qml      通道卡片
      LineChart.qml        Canvas 折线图
      StatusPill.qml       状态胶囊
      AppButton.qml        按钮
    pages/
      DashboardPage.qml    实时看板
      HistoryPage.qml      历史曲线
      AlarmPage.qml        告警记录
      ChannelConfigPage.qml 通道配置
      LinkPage.qml         链路管理
      DebugPage.qml        报文调试台
`@

---

## 2. 构建

### 2.1 Windows（本机已验证）

`@powershell
powershell -ExecutionPolicy Bypass -File tools\build-frontend.ps1
`@

脚本会：设置 Qt/MinGW 的 PATH、把 `TEMP` 指到工作区内、预置 `.qmake.stash`，然后执行 `qmake + mingw32-make`。

> ⚠️ **本机特有**：受 DSH 沙箱限制，qmake 无法运行编译器探测（CreateFile 拒绝访问），
> 因此 `build-frontend.ps1` 会预置编译器宏与 include/lib 路径到 `frontend/build/.qmake.stash`。
> 换机器/换 Qt 版本时重新生成即可（或直接改用 CMake，见 2.3）。

### 2.2 Linux

`@bash
cd frontend
mkdir -p build && cd build
qmake ../jdrk-frontend.pro && make -j4      # 或使用 CMake（2.3）
`@

Linux 下 qmake 的编译器探测正常，无需 stash。

### 2.3 可选：CMake

若安装了 CMake，可用 `qt_add_executable` + `qt_add_qml_module` 替换 qmake；
源码与 QML 无需改动，仅需把 QML 文件加入 QML 模块资源。

---

## 3. 运行

1. 先启动后端（真机或模拟器）：

`@powershell
# 模拟器（无硬件）
powershell -ExecutionPolicy Bypass -File tools\run-backend.ps1 -Config config\simulator.toml
`@

2. 再启动前端：

`@powershell
powershell -ExecutionPolicy Bypass -File tools\run-frontend.ps1
`@

前端默认连接 `http://127.0.0.1:8790`，对应 WebSocket `ws://127.0.0.1:8790/api/ws`。
后端地址可在 `AppController::setBaseUrl()` 或 `app.baseUrl` 中调整。

运行所需环境变量（`run-frontend.ps1` 已代设）：

`@text
PATH            含 C:\Qt\Qt6.11.0\6.11.0\mingw_64\bin 与 Tools\mingw1310_64\bin
QML2_IMPORT_PATH  C:\Qt\Qt6.11.0\6.11.0\mingw_64\qml
QT_PLUGIN_PATH    C:\Qt\Qt6.11.0\6.11.0\mingw_64\plugins
`@

---

## 4. 与后端的对接

| 前端能力 | 后端接口 |
| --- | --- |
| 连接状态、版本 | WS `hello`；`GET /api/health` |
| 实时看板 | WS `sample` / `round` / `link_status`；`GET /api/samples/latest` |
| 历史曲线 | `GET /api/samples?channel=&limit=`（CSV 导出走 `/api/export.csv`） |
| 告警 | WS `alarm`；`GET /api/alarms`；`POST /api/alarms/clear` |
| 通道配置 | `GET /api/channels`；`PUT /api/channels/{no}` |
| 链路管理 | `GET /api/links`；`POST /api/links/{name}/activate`；`POST /api/links/reload`；`PUT /api/poll`；`POST /api/poll/now` |
| 报文调试台 | WS `frame`；`GET /api/debug/frames`；`POST /api/debug/raw|scan|read|write` |
| 数据库回传 | `GET /api/forward/status`；`POST /api/forward/test` |

WebSocket 事件类型与字段详见 `docs/后端API与WebSocket协议.md`。

---

## 5. 页面说明

1. **实时看板**：20 路通道卡片（当前值/单位/质量位/原始值/RTT/更新时间）+ 本轮耗时与成功率。
2. **历史曲线**：通道与条数选择，Canvas 折线图 + 明细表。
3. **告警记录**：上下限告警列表，支持按通道复归。
4. **通道配置**：名称/单位/型号/数据类型/系数A/B/小数位/上下限/从站地址/启用，逐行保存（FR-07）。
5. **链路管理**：链路档案一键切换（FR-11）、链路质量统计、轮询调度、数据库回传状态与试连。
6. **报文调试台**：手工发帧、地址扫描、读/写寄存器、原始 TX/RX 日志与结果 JSON。

---

## 6. 已知限制与待办

- 通道配置页使用本地输入框 + 保存按钮，尚未做字段级校验与批量保存。
- 历史曲线为简化折线图，未做缩放/多通道叠加/游标。
- 未实现曲线导出（后端已有 CSV 接口，可加按钮）。
- 告警仅展示与复归，未做弹窗/声音提示。
- 未做国际化（当前仅中文）。
- 未使用 `windeployqt` 打包成独立目录（发布时执行 `windeployqt jdrk-monitor-ui.exe` 即可）。
