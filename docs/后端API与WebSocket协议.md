# JDRK 后端 · REST 与 WebSocket 接口协议

| 项目 | 内容 |
| --- | --- |
| 服务 | `jdrk-monitor-backend` |
| 默认监听 | `127.0.0.1:8790` |
| 数据格式 | JSON（UTF-8）；时间为 RFC3339 带时区 |
| 跨域 | 已开启 `CorsLayer::permissive()`（便于 Qt/Web 调试） |
| 鉴权 | 暂无（局域网工具；如需可在 `api/mod.rs` 加中间件） |

统一返回约定：

- 成功：业务 JSON；列表接口直接返回数组。
- 失败（REST）：HTTP 4xx/5xx + `{"ok":false,"error":"..."}`。
- 调试类接口即使链路失败也返回 **HTTP 200**，用 `ok:false` 表示，便于前端展示原始报文。

---

## 1. REST

### 1.1 健康与统计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 存活、版本、运行时长、链路连通、行数 |
| GET | `/api/version` | 名称与版本 |
| GET | `/api/stats` | 行数、发件箱、活动告警、轮次、最近一轮、链路状态 |

`@json
// GET /api/health
{"ok":true,"version":"0.1.0","uptime_s":29,"link_connected":true,"link_profile":"sim","rows":80,"outbox":0}
`@

`@json
// GET /api/stats
{
  "rows": 80, "outbox": 0, "active_alarms": 0, "rounds": 4,
  "last_round": {"round_id":4,"elapsed_ms":90,"total":20,"ok":20,"fail":0,"avg_rtt_ms":0},
  "link": {"profile":"sim","kind":"simulator","connected":true,"ok_count":80,"last_error":null}
}
`@

### 1.2 配置与通道

| 方法 | 路径 | 入参 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/config` | — | 完整配置 |
| GET | `/api/channels` | — | 20 路通道档案 |
| PUT | `/api/channels` | 通道数组 | 整体替换 |
| PUT | `/api/channels/{no}` | 部分字段 | 更新单个通道 |

`@json
// PUT /api/channels/1
{"name":"溶解氧","unit":"mg/L","sensor_model":"RS-LDO-N01-2",
 "data_type":"f32","coef_a":1.0,"coef_b":0.0,"decimals":2,
 "upper_limit":12.0,"lower_limit":2.0,"enabled":true,"slave_addr":1}
`@

### 1.3 数据查询

| 方法 | 路径 | 查询参数 |
| --- | --- | --- |
| GET | `/api/samples/latest` | `per_channel`（每通道取最近 N 条，默认 1） |
| GET | `/api/samples` | `channel`、`from`、`to`、`limit` |
| GET | `/api/export.csv` | 同上，返回 `text/csv` |

采样对象：

`@json
{"record_time":"2026-09-20T22:18:12.372+08:00","device_sn":"SIM-QXZ-0001",
 "channel_no":1,"channel_name":"通道1","unit":"","value":23.0,"raw_value":230,
 "quality":"ok","rtt_ms":4}
`@

`quality` 取值：`ok / timeout / crc_error / illegal_addr / slave_fault / scaling / disabled / no_data`。

### 1.4 链路管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/link/status` | 当前链路状态与链路质量计数 |
| GET | `/api/links` | 全部链路档案 |
| POST | `/api/links/{name}/activate` | **热切换**到指定档案（FR-11） |
| POST | `/api/links/reload` | 按当前配置重开串口 |

### 1.5 调度控制

| 方法 | 路径 | 入参 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/poll/now` | — | 立即触发一轮采集 |
| PUT | `/api/poll` | `{"enabled":true,"interval_ms":10000}` | 修改轮询参数 |

### 1.6 报文调试台（FR-08）

| 方法 | 路径 | 入参 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/debug/frames` | `?limit=200` | 最近原始 TX/RX 帧 |
| POST | `/api/debug/raw` | `{"hex":"01 03 00 00 00 02 C4 0B","addr":1,"expect_len":9}` | 手工发帧 |
| POST | `/api/debug/scan` | `{"from":1,"to":32,"start":0,"count":2}` | 地址扫描 |
| POST | `/api/debug/read` | `{"addr":1,"start":0,"count":2}` | 读寄存器 |
| POST | `/api/debug/write` | `{"addr":1,"reg":690,"value":0}` | 写单寄存器 |

`@json
// POST /api/debug/scan
{"ok":true,"online":2,"results":[
  {"addr":1,"online":true,"regs":[658,-101]},
  {"addr":2,"online":false,"quality":"timeout","error":"无应答（超时）"}
]}
`@

### 1.7 告警

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/alarms` | `?limit=200` 最近告警 |
| POST | `/api/alarms/clear` | `{"channel_no":1}` 复归该通道未复归告警 |

### 1.8 回传

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/forward/status` | 各目标：连通、滞后条数、累计成功/失败、最近错误 |
| POST | `/api/forward/test` | `{"target":"mysql_main"}` 连通性试连 |

---

## 2. WebSocket

### 2.1 连接

`@text
ws://127.0.0.1:8790/api/ws
`@

连接建立后服务端**立即**下发一条 `hello`。

### 2.2 服务端 → 客户端事件

所有事件都是 JSON 对象，用 `type` 区分：

| type | 触发时机 | 关键字段 |
| --- | --- | --- |
| `hello` | 连接建立 | `server_time` `version` `device_sn` `active_link` `protocol` |
| `round` | 每轮采集结束 | `round_id` `elapsed_ms` `ok` `fail` `avg_rtt_ms` |
| `sample` | 每条通道采样 | 同 REST 采样对象 |
| `link_status` | 链路计数变化 | `connected` `ok_count` `timeout_count` `crc_error_count` `last_rtt_ms` `last_error` |
| `frame` | 每次原始收发 | `direction`(tx/rx) `addr` `func` `bytes` `rtt_ms` `quality` |
| `alarm` | 告警产生/复归 | `channel_no` `alarm_type` `value` `threshold` `raised_at` `cleared_at` |
| `forward` | 回传状态变化 | `target` `connected` `pending` `sent_total` `failed_total` `last_error` |
| `notice` | 提示 | `level` `message` |

`@json
{"type":"sample","record_time":"2026-09-20T22:18:12.372+08:00","device_sn":"SIM-QXZ-0001",
 "channel_no":1,"channel_name":"通道1","unit":"","value":23.0,"raw_value":230,"quality":"ok","rtt_ms":4}
`@

`@json
{"type":"round","round_id":4,"started_at":"2026-09-20T22:18:12.372+08:00",
 "elapsed_ms":90,"total":20,"ok":20,"fail":0,"avg_rtt_ms":0}
`@

`@json
{"type":"frame","at":"2026-09-20T22:18:12.376+08:00","direction":"tx","addr":1,"func":3,
 "bytes":"01 03 00 00 00 02 C4 0B","rtt_ms":null,"quality":null,"note":"第 1/2 次"}
`@

### 2.3 客户端 → 服务端命令

| action | 说明 |
| --- | --- |
| `{"action":"ping"}` | 返回 `pong` |
| `{"action":"snapshot"}` | 返回 `{"type":"snapshot","samples":[...]}`（各通道最新一条） |
| `{"action":"status"}` | 返回 `{"type":"status","link":{...},"rows":N,"outbox":N}` |
| `{"action":"poll_now"}` | 触发立即采集，返回 `notice` |

Qt 侧建议：用一个 `WebSocketChannel` 接收事件、按 `type` 分发到对应模型；
断线重连后先发一次 `snapshot` / `status` 补齐状态。

### 2.4 背压与丢帧

事件总线为 `tokio::broadcast`（容量 4096）。客户端消费慢时只丢弃**旧事件**（`Lagged`），
不会阻塞采集；帧事件另外保留在服务端环形缓冲区（`debug.frame_log_capacity`，默认 2000），
可通过 `GET /api/debug/frames` 回捞。

---

## 3. 质量位与异常语义（来自实测）

| quality | 含义 | 处置建议 |
| --- | --- | --- |
| `ok` | 正常 | 采用 |
| `timeout` | 无应答（设备对不支持的功能码/越界地址**静默**，不返回异常帧） | 重试；连续失败置离线 |
| `crc_error` | 收到字节但无法组成合法 CRC 帧（多见于半双工换向首字节丢失） | 重试 |
| `illegal_addr` | 应答地址与请求不符 | 检查地址规划 |
| `slave_fault` | 从站返回异常码 | 查看异常码 |
| `no_data` | 批量读回退后仍无该通道数据 | 检查通道配置 |
