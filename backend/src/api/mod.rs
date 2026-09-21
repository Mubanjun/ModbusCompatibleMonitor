//! REST + WebSocket 接口。

pub mod ws;

use crate::config::{AppConfig, LinkProfile};
use crate::domain::{ChannelConfig, Sample};
use crate::events::{ForwardStatus, FrameLog, LinkStatus, RoundSummary};
use crate::modbus::frame::{hex, parse_hex};
use crate::state::{AppState, VERSION};
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::routing::{get, post, put};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::collections::HashMap;
use std::sync::Arc;

// ---------------- 错误 ----------------

pub enum ApiError {
    NotFound(String),
    BadRequest(String),
    Internal(String),
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (code, msg) = match self {
            ApiError::NotFound(m) => (StatusCode::NOT_FOUND, m),
            ApiError::BadRequest(m) => (StatusCode::BAD_REQUEST, m),
            ApiError::Internal(m) => (StatusCode::INTERNAL_SERVER_ERROR, m),
        };
        (code, Json(json!({ "ok": false, "error": msg }))).into_response()
    }
}

impl From<crate::error::AppError> for ApiError {
    fn from(e: crate::error::AppError) -> Self {
        ApiError::Internal(e.to_string())
    }
}

type ApiResult<T> = std::result::Result<Json<T>, ApiError>;

// ---------------- 路由 ----------------

pub fn router(state: Arc<AppState>) -> Router {
    Router::new()
        .route("/api/health", get(health))
        .route("/api/version", get(version))
        .route("/api/config", get(get_config))
        .route("/api/channels", get(list_channels).put(replace_channels))
        .route("/api/channels/{no}", put(update_channel))
        .route("/api/samples/latest", get(latest))
        .route("/api/samples", get(history))
        .route("/api/export.csv", get(export_csv))
        .route("/api/link/status", get(link_status))
        .route("/api/links", get(list_links))
        .route("/api/links/{name}/activate", post(activate_link))
        .route("/api/links/reload", post(reload_link))
        .route("/api/poll/now", post(poll_now))
        .route("/api/shutdown", post(shutdown))
        .route("/api/poll", put(set_poll))
        .route("/api/debug/frames", get(debug_frames))
        .route("/api/debug/raw", post(debug_raw))
        .route("/api/debug/scan", post(debug_scan))
        .route("/api/debug/read", post(debug_read))
        .route("/api/debug/write", post(debug_write))
        .route("/api/alarms", get(list_alarms))
        .route("/api/alarms/clear", post(clear_alarms))
        .route("/api/forward/status", get(forward_status))
        .route("/api/forward/test", post(forward_test))
        .route("/api/stats", get(stats))
        .route("/api/ws", get(ws::ws_handler))
        .with_state(state)
}

// ---------------- 基础 ----------------

async fn health(State(state): State<Arc<AppState>>) -> ApiResult<serde_json::Value> {
    let ls = state.link_status().await;
    let (rows, outbox) = state.store.count().await?;
    Ok(Json(json!({
        "ok": true,
        "version": VERSION,
        "uptime_s": (chrono::Local::now() - state.started_at).num_seconds(),
        "link_connected": ls.connected,
        "link_profile": ls.profile,
        "rows": rows,
        "outbox": outbox,
    })))
}

async fn version() -> Json<serde_json::Value> {
    Json(json!({ "name": "jdrk-monitor-backend", "version": VERSION }))
}

async fn get_config(State(state): State<Arc<AppState>>) -> ApiResult<AppConfig> {
    Ok(Json(state.cfg.read().await.clone()))
}

async fn list_channels(State(state): State<Arc<AppState>>) -> ApiResult<Vec<ChannelConfig>> {
    Ok(Json(state.cfg.read().await.channels.clone()))
}

async fn replace_channels(
    State(state): State<Arc<AppState>>,
    Json(channels): Json<Vec<ChannelConfig>>,
) -> ApiResult<Vec<ChannelConfig>> {
    let mut cfg = state.cfg.write().await;
    cfg.channels = channels;
    cfg.normalize();
    let out = cfg.channels.clone();
    Ok(Json(out))
}

#[derive(Deserialize)]
struct ChannelPatch {
    name: Option<String>,
    unit: Option<String>,
    sensor_model: Option<String>,
    data_type: Option<crate::domain::DataType>,
    coef_a: Option<f64>,
    coef_b: Option<f64>,
    decimals: Option<u8>,
    upper_limit: Option<f64>,
    lower_limit: Option<f64>,
    enabled: Option<bool>,
    slave_addr: Option<u8>,
}

async fn update_channel(
    State(state): State<Arc<AppState>>,
    Path(no): Path<u16>,
    Json(patch): Json<ChannelPatch>,
) -> ApiResult<ChannelConfig> {
    let mut cfg = state.cfg.write().await;
    let ch = cfg
        .channels
        .iter_mut()
        .find(|c| c.no == no)
        .ok_or_else(|| ApiError::NotFound(format!("通道 {no} 不存在")))?;
    if let Some(v) = patch.name { ch.name = v; }
    if let Some(v) = patch.unit { ch.unit = v; }
    if let Some(v) = patch.sensor_model { ch.sensor_model = v; }
    if let Some(v) = patch.data_type { ch.data_type = v; }
    if let Some(v) = patch.coef_a { ch.coef_a = v; }
    if let Some(v) = patch.coef_b { ch.coef_b = v; }
    if let Some(v) = patch.decimals { ch.decimals = v; }
    if let Some(v) = patch.upper_limit { ch.upper_limit = Some(v); }
    if let Some(v) = patch.lower_limit { ch.lower_limit = Some(v); }
    if let Some(v) = patch.enabled { ch.enabled = v; }
    if let Some(v) = patch.slave_addr { ch.slave_addr = v; }
    Ok(Json(ch.clone()))
}

// ---------------- 数据 ----------------

#[derive(Deserialize)]
struct LatestQuery {
    #[serde(default = "one")]
    per_channel: u32,
}
fn one() -> u32 { 1 }

async fn latest(State(state): State<Arc<AppState>>, Query(q): Query<LatestQuery>) -> ApiResult<Vec<Sample>> {
    Ok(Json(state.store.latest(q.per_channel.min(1000)).await?))
}

#[derive(Deserialize)]
struct HistoryQuery {
    channel: Option<u16>,
    from: Option<String>,
    to: Option<String>,
    limit: Option<u32>,
}

async fn history(State(state): State<Arc<AppState>>, Query(q): Query<HistoryQuery>) -> ApiResult<Vec<Sample>> {
    let sn = state.cfg.read().await.device.sn.clone();
    Ok(Json(
        state
            .store
            .history(Some(sn), q.channel, q.from, q.to, q.limit.unwrap_or(500))
            .await?,
    ))
}

async fn export_csv(State(state): State<Arc<AppState>>, Query(q): Query<HistoryQuery>) -> Response {
    let sn = state.cfg.read().await.device.sn.clone();
    match state.store.history(Some(sn), q.channel, q.from, q.to, q.limit.unwrap_or(10000)).await {
        Ok(rows) => {
            let mut out = String::from("record_time,device_sn,channel_no,channel_name,unit,value,raw_value,quality,rtt_ms\n");
            for s in rows {
                out.push_str(&format!(
                    "{},{},{},\"{}\",\"{}\",{},{},{},{}\n",
                    s.record_time.to_rfc3339(),
                    s.device_sn,
                    s.channel_no,
                    s.channel_name,
                    s.unit,
                    s.value.map(|v| v.to_string()).unwrap_or_default(),
                    s.raw_value.map(|v| v.to_string()).unwrap_or_default(),
                    s.quality.as_str(),
                    s.rtt_ms.map(|v| v.to_string()).unwrap_or_default(),
                ));
            }
            (
                [
                    ("content-type", "text/csv; charset=utf-8"),
                    ("content-disposition", "attachment; filename=export.csv"),
                ],
                out,
            )
                .into_response()
        }
        Err(e) => ApiError::from(e).into_response(),
    }
}

#[derive(Serialize)]
struct Stats {
    rows: u64,
    outbox: u64,
    active_alarms: usize,
    rounds: u64,
    last_round: Option<RoundSummary>,
    link: LinkStatus,
}

async fn stats(State(state): State<Arc<AppState>>) -> ApiResult<Stats> {
    let (rows, outbox) = state.store.count().await?;
    let rt = state.runtime.read().await.clone();
    let active = state.alarms.lock().map(|a| a.active_count()).unwrap_or(0);
    Ok(Json(Stats {
        rows,
        outbox,
        active_alarms: active,
        rounds: rt.round_total,
        last_round: rt.last_round.clone(),
        link: state.link_status().await,
    }))
}

// ---------------- 链路 ----------------

async fn link_status(State(state): State<Arc<AppState>>) -> ApiResult<LinkStatus> {
    Ok(Json(state.link_status().await))
}

async fn list_links(State(state): State<Arc<AppState>>) -> ApiResult<Vec<LinkProfile>> {
    Ok(Json(state.cfg.read().await.links.clone()))
}

async fn activate_link(State(state): State<Arc<AppState>>, Path(name): Path<String>) -> ApiResult<serde_json::Value> {
    let profile = {
        let mut cfg = state.cfg.write().await;
        let p = cfg
            .profile(&name)
            .cloned()
            .ok_or_else(|| ApiError::NotFound(format!("链路档案 {name} 不存在")))?;
        cfg.active_link = Some(name.clone());
        p
    };
    state
        .link
        .reload(profile)
        .await
        .map_err(|e| ApiError::Internal(format!("切换链路失败: {e}")))?;
    Ok(Json(json!({ "ok": true, "active_link": name })))
}

async fn reload_link(State(state): State<Arc<AppState>>) -> ApiResult<serde_json::Value> {
    let name = state.cfg.read().await.active_link.clone().unwrap_or_default();
    let profile = state
        .cfg
        .read()
        .await
        .profile(&name)
        .cloned()
        .ok_or_else(|| ApiError::BadRequest("没有活动链路".into()))?;
    state
        .link
        .reload(profile)
        .await
        .map_err(|e| ApiError::Internal(format!("重载链路失败: {e}")))?;
    Ok(Json(json!({ "ok": true })))
}

/// 优雅停机：关闭串口后退出（供服务管理 / 测试脚本调用）。
async fn shutdown(State(state): State<Arc<AppState>>) -> ApiResult<serde_json::Value> {
    tracing::info!("收到 /api/shutdown，正在关闭串口并退出");
    state.link.shutdown().await;
    tokio::spawn(async {
        tokio::time::sleep(std::time::Duration::from_millis(150)).await;
        std::process::exit(0);
    });
    Ok(Json(json!({ "ok": true, "message": "正在关机" })))
}

async fn poll_now(State(state): State<Arc<AppState>>) -> Json<serde_json::Value> {
    state.poll_now.notify_one();
    Json(json!({ "ok": true }))
}

#[derive(Deserialize)]
struct PollPatch {
    enabled: Option<bool>,
    interval_ms: Option<u64>,
}

async fn set_poll(State(state): State<Arc<AppState>>, Json(p): Json<PollPatch>) -> ApiResult<serde_json::Value> {
    let mut cfg = state.cfg.write().await;
    if let Some(v) = p.enabled { cfg.poll.enabled = v; }
    if let Some(v) = p.interval_ms { cfg.poll.interval_ms = v.max(200); }
    Ok(Json(json!({ "ok": true, "enabled": cfg.poll.enabled, "interval_ms": cfg.poll.interval_ms })))
}

// ---------------- 调试台 ----------------

#[derive(Deserialize)]
struct FrameQuery {
    limit: Option<usize>,
}

async fn debug_frames(State(state): State<Arc<AppState>>, Query(q): Query<FrameQuery>) -> ApiResult<Vec<FrameLog>> {
    Ok(Json(state.recent_frames(q.limit.unwrap_or(200).min(2000))))
}

#[derive(Deserialize)]
struct RawReq {
    hex: String,
    #[serde(default)]
    addr: Option<u8>,
    #[serde(default)]
    expect_len: Option<usize>,
}

async fn debug_raw(State(state): State<Arc<AppState>>, Json(req): Json<RawReq>) -> ApiResult<serde_json::Value> {
    let bytes = parse_hex(&req.hex).map_err(ApiError::BadRequest)?;
    if bytes.len() < 4 {
        return Err(ApiError::BadRequest("报文至少 4 字节".into()));
    }
    match state.link.raw(bytes, req.addr, req.expect_len).await {
        Ok(rx) => Ok(Json(json!({ "ok": true, "rx": hex(&rx), "rx_len": rx.len() }))),
        Err(e) => Ok(Json(json!({
            "ok": false, "quality": e.quality.as_str(), "error": e.message,
            "tx": e.tx, "rx": e.rx, "rtt_ms": e.rtt_ms, "attempts": e.attempts
        }))),
    }
}

#[derive(Deserialize)]
struct ScanReq {
    from: Option<u8>,
    to: Option<u8>,
    start: Option<u16>,
    count: Option<u16>,
}

async fn debug_scan(State(state): State<Arc<AppState>>, Json(req): Json<ScanReq>) -> ApiResult<serde_json::Value> {
    let from = req.from.unwrap_or(1);
    let to = req.to.unwrap_or(32).max(from);
    let start = req.start.unwrap_or(0);
    let count = req.count.unwrap_or(2).clamp(1, 124);
    let hits = state.link.scan(from, to, start, count).await;
    let result: Vec<serde_json::Value> = hits
        .into_iter()
        .map(|(addr, r)| match r {
            Ok(regs) => json!({ "addr": addr, "online": true, "regs": regs }),
            Err(e) => json!({ "addr": addr, "online": false, "quality": e.quality.as_str(), "error": e.message }),
        })
        .collect();
    let online = result.iter().filter(|v| v["online"] == true).count();
    Ok(Json(json!({ "ok": true, "online": online, "results": result })))
}

#[derive(Deserialize)]
struct ReadReq {
    addr: u8,
    start: u16,
    count: u16,
}

async fn debug_read(State(state): State<Arc<AppState>>, Json(req): Json<ReadReq>) -> ApiResult<serde_json::Value> {
    let count = req.count.clamp(1, 124);
    match state.link.read_registers(req.addr, req.start, count).await {
        Ok(regs) => Ok(Json(json!({ "ok": true, "addr": req.addr, "start": req.start, "regs": regs }))),
        Err(e) => Ok(Json(json!({
            "ok": false, "quality": e.quality.as_str(), "error": e.message,
            "tx": e.tx, "rx": e.rx, "rtt_ms": e.rtt_ms
        }))),
    }
}

#[derive(Deserialize)]
struct WriteReq {
    addr: u8,
    reg: u16,
    value: u16,
}

async fn debug_write(State(state): State<Arc<AppState>>, Json(req): Json<WriteReq>) -> ApiResult<serde_json::Value> {
    match state.link.write_register(req.addr, req.reg, req.value).await {
        Ok(()) => Ok(Json(json!({ "ok": true }))),
        Err(e) => Ok(Json(json!({ "ok": false, "quality": e.quality.as_str(), "error": e.message }))),
    }
}

// ---------------- 告警 / 回传 ----------------

#[derive(Deserialize)]
struct LimitQuery {
    limit: Option<u32>,
}

async fn list_alarms(State(state): State<Arc<AppState>>, Query(q): Query<LimitQuery>) -> ApiResult<Vec<crate::domain::AlarmRecord>> {
    Ok(Json(state.store.latest_alarms(q.limit.unwrap_or(200)).await?))
}

#[derive(Deserialize)]
struct ClearReq {
    channel_no: u16,
}

async fn clear_alarms(State(state): State<Arc<AppState>>, Json(req): Json<ClearReq>) -> ApiResult<serde_json::Value> {
    let sn = state.cfg.read().await.device.sn.clone();
    let n = state.store.clear_alarms(&sn, req.channel_no).await?;
    Ok(Json(json!({ "ok": true, "cleared": n })))
}

async fn forward_status(State(state): State<Arc<AppState>>) -> ApiResult<HashMap<String, ForwardStatus>> {
    Ok(Json(state.forward_status.read().await.clone()))
}

#[derive(Deserialize)]
struct TestReq {
    target: String,
}

async fn forward_test(State(state): State<Arc<AppState>>, Json(req): Json<TestReq>) -> ApiResult<serde_json::Value> {
    let sinks = state.sinks.clone();
    let target = req.target.clone();
    let res = tokio::task::spawn_blocking(move || {
        let mut guard = sinks.lock().map_err(|_| "sink 锁失败".to_string())?;
        let sink = guard.get_mut(&target).ok_or_else(|| format!("回传目标 {target} 不存在"))?;
        sink.test().map_err(|e| e.to_string())
    })
    .await
    .map_err(|e| ApiError::Internal(e.to_string()))?;
    match res {
        Ok(msg) => Ok(Json(json!({ "ok": true, "message": msg }))),
        Err(e) => Ok(Json(json!({ "ok": false, "error": e }))),
    }
}
