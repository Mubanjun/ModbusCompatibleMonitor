//! 应用装配与后台任务。

use crate::alarm::AlarmChange;
use crate::api;
use crate::collector;
use crate::config::AppConfig;
use crate::domain::Quality;
use crate::events::{Event, EventBus, ForwardStatus, RoundSummary};
use crate::modbus::link::LinkHandle;
use crate::state::{AppState, VERSION};
use crate::store::{OutboxBatch, Store};
use chrono::Local;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::time::sleep;

pub async fn run(config_path: &str, bind_override: Option<String>) -> anyhow::Result<()> {
    let mut cfg = AppConfig::load(config_path)?;
    if let Some(bind) = bind_override {
        cfg.server.bind = bind;
    }
    cfg.normalize();

    let store = Store::new(cfg.storage.sqlite_path.clone());
    store.init().await?;

    let bus = EventBus::new(4096);
    let active = cfg.active_profile()?.clone();
    let link = LinkHandle::spawn(active, bus.clone(), cfg.debug.frame_log_enabled);
    let frame_cap = cfg.debug.frame_log_capacity;
    let state = AppState::new(cfg, store, bus.clone(), link, frame_cap);

    tracing::info!(version = VERSION, "JDRK 监控平台后端启动");

    spawn_frame_capture(state.clone());
    spawn_collector(state.clone());
    spawn_forwarder(state.clone());
    spawn_shutdown_handler(state.clone());

    let app = api::router(state.clone()).layer(
        tower_http::cors::CorsLayer::permissive(),
    );
    let addr: std::net::SocketAddr = state.cfg.read().await.server.bind.parse()?;
    let listener = tokio::net::TcpListener::bind(addr).await?;
    tracing::info!(%addr, "REST/WebSocket 服务已启动");
    println!("REST  : http://{addr}/api/health");
    println!("WS    : ws://{addr}/api/ws");
    axum::serve(listener, app).await?;
    Ok(())
}

/// 收到退出信号时优雅停机：关闭串口并退出，避免端口被占用/进程卡死。
///
/// Windows：Ctrl+C / 控制台关闭事件（另有 serial/win.rs 的 SetConsoleCtrlHandler 兜底）。
/// Linux/macOS：SIGINT 与 SIGTERM —— systemd 的 `systemctl stop` 发的是 SIGTERM，
/// 若只监听 SIGINT，服务停止会退化为直接杀进程（串口虽由内核回收，但拿不到优雅路径）。
fn spawn_shutdown_handler(state: Arc<AppState>) {
    tokio::spawn(async move {
        let sig = wait_shutdown_signal().await;
        tracing::info!(signal = sig, "收到退出信号，正在关闭串口…");
        state.link.shutdown().await;
        std::process::exit(0);
    });
}

#[cfg(unix)]
async fn wait_shutdown_signal() -> &'static str {
    use tokio::signal::unix::{signal, SignalKind};
    match signal(SignalKind::terminate()) {
        Ok(mut term) => tokio::select! {
            _ = tokio::signal::ctrl_c() => "SIGINT",
            _ = term.recv() => "SIGTERM",
        },
        Err(e) => {
            tracing::warn!(error = %e, "注册 SIGTERM 处理失败，仅监听 SIGINT");
            let _ = tokio::signal::ctrl_c().await;
            "SIGINT"
        }
    }
}

#[cfg(not(unix))]
async fn wait_shutdown_signal() -> &'static str {
    let _ = tokio::signal::ctrl_c().await;
    "Ctrl+C / 控制台关闭"
}

fn spawn_frame_capture(state: Arc<AppState>) {
    tokio::spawn(async move {
        let mut sub = state.bus.subscribe();
        while let Ok(ev) = sub.recv().await {
            if let Event::Frame(f) = ev {
                state.push_frame(f);
            }
        }
    });
}

fn spawn_collector(state: Arc<AppState>) {
    tokio::spawn(async move {
        loop {
            let (enabled, interval, round_timeout_ms, profile, channels, sn, forward_targets) = {
                let cfg = state.cfg.read().await;
                let profile = cfg.active_profile().cloned().unwrap_or_default();
                let targets: Vec<String> = cfg
                    .forward
                    .targets
                    .iter()
                    .filter(|t| t.enabled)
                    .map(|t| t.name.clone())
                    .collect();
                (
                    cfg.poll.enabled,
                    cfg.poll.interval_ms.max(200),
                    cfg.poll.round_timeout_ms.max(500),
                    profile,
                    cfg.channels.clone(),
                    cfg.device.sn.clone(),
                    targets,
                )
            };

            if !enabled {
                tokio::select! {
                    _ = sleep(Duration::from_millis(500)) => {}
                    _ = state.poll_now.notified() => {}
                }
                continue;
            }

            let started_at = Local::now();
            let round_id = state.next_round_id();
            let deadline = Instant::now() + Duration::from_millis(round_timeout_ms);
            let outcome = collector::poll_round(&state.link, &profile, &channels, &sn, Some(deadline)).await;

            // 本地落盘 + 发件箱（FR-16）
            if let Err(e) = state.store.insert_round(&forward_targets, &outcome.samples).await {
                tracing::error!(error = %e, "本地入库失败");
                state.runtime.write().await.last_error = Some(e.to_string());
            }

            // 告警判定（FR-06）
            let mut changes = Vec::new();
            {
                let mut engine = match state.alarms.lock() {
                    Ok(g) => g,
                    Err(_) => {
                        tracing::error!("告警引擎锁失败");
                        continue;
                    }
                };
                for sample in &outcome.samples {
                    if let Some(cfg) = channels.iter().find(|c| c.no == sample.channel_no) {
                        changes.extend(engine.check(sample, cfg));
                    }
                }
            }
            for change in changes {
                let record = match change {
                    AlarmChange::Raised(a) => a,
                    AlarmChange::Cleared(a) => a,
                };
                match state.store.insert_alarm(&record).await {
                    Ok(id) => {
                        let mut rec = record.clone();
                        rec.id = id;
                        state.bus.publish(Event::Alarm(rec));
                    }
                    Err(e) => tracing::warn!(error = %e, "告警入库失败"),
                }
            }

            // 推送
            for sample in &outcome.samples {
                state.bus.publish(Event::Sample(sample.clone()));
            }
            let summary = RoundSummary {
                round_id,
                started_at: Some(started_at),
                elapsed_ms: outcome.elapsed_ms,
                total: outcome.samples.len() as u32,
                ok: outcome.ok,
                fail: outcome.fail,
                avg_rtt_ms: outcome.avg_rtt_ms,
            };
            {
                let mut rt = state.runtime.write().await;
                rt.last_round = Some(summary.clone());
                rt.round_total += 1;
            }
            if summary.ok > 0 || summary.fail > 0 {
                tracing::info!(
                    round = round_id,
                    ok = summary.ok,
                    fail = summary.fail,
                    elapsed_ms = summary.elapsed_ms,
                    "采集一轮完成"
                );
            }
            state.bus.publish(Event::Round(summary));

            let elapsed = (Local::now() - started_at).num_milliseconds().max(0) as u64;
            let wait = interval.saturating_sub(elapsed);
            tokio::select! {
                _ = sleep(Duration::from_millis(wait)) => {}
                _ = state.poll_now.notified() => {}
            }
        }
    });
}

fn spawn_forwarder(state: Arc<AppState>) {
    tokio::spawn(async move {
        loop {
            let (enabled, interval, targets, batch_size, max_attempts) = {
                let cfg = state.cfg.read().await;
                (
                    cfg.forward.enabled,
                    cfg.forward.interval_ms.max(500),
                    cfg.forward.targets.clone(),
                    cfg.forward.batch_size,
                    cfg.forward.max_attempts,
                )
            };

            if enabled {
                for target in targets.iter().filter(|t| t.enabled) {
                    if let Err(e) = forward_once(&state, &target.name, &target.url, &target.table, batch_size, max_attempts).await {
                        tracing::warn!(target = %target.name, error = %e, "回传失败");
                    }
                }
            }
            sleep(Duration::from_millis(interval)).await;
        }
    });
}

async fn forward_once(
    state: &Arc<AppState>,
    target: &str,
    _url: &str,
    _table: &str,
    batch_size: u32,
    max_attempts: u32,
) -> anyhow::Result<()> {
    let rows = state.store.pending_outbox(target, batch_size, max_attempts).await?;
    let (pending, sent_total, failed_total) = state.store.outbox_stats(target).await.unwrap_or((0, 0, 0));

    if rows.is_empty() {
        update_forward_status(state, target, true, pending, sent_total, failed_total, None).await;
        return Ok(());
    }

    let sinks = state.sinks.clone();
    let target_owned = target.to_string();
    let rows_moved = rows.clone();
    let result = tokio::task::spawn_blocking(move || {
        let mut guard = match sinks.lock() {
            Ok(g) => g,
            Err(_) => return Err((Vec::<i64>::new(), "sink 锁失败".to_string())),
        };
        let sink = match guard.get_mut(&target_owned) {
            Some(s) => s,
            None => return Err((Vec::<i64>::new(), format!("回传目标 {target_owned} 未配置"))),
        };
        let mut done: Vec<i64> = Vec::new();
        for row in &rows_moved {
            let batch: OutboxBatch = match serde_json::from_str(&row.payload) {
                Ok(b) => b,
                Err(e) => return Err((done, format!("发件箱载荷解析失败: {e}"))),
            };
            match sink.send_batch(&batch) {
                Ok(_) => done.push(row.id),
                Err(e) => {
                    sink.reset();
                    return Err((done, e.to_string()));
                }
            }
        }
        Ok((done, String::new()))
    })
    .await;

    let (done, err) = match result {
        Ok(Ok((done, _))) => (done, None),
        Ok(Err((done, e))) => (done, Some(e)),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };

    if !done.is_empty() {
        state.store.mark_sent(&done).await?;
    }
    if let Some(e) = &err {
        let failed_ids: Vec<i64> = rows.iter().map(|r| r.id).filter(|id| !done.contains(id)).collect();
        if !failed_ids.is_empty() {
            state.store.mark_failed(&failed_ids, e).await?;
        }
    }
    let (pending, sent_total, failed_total) = state.store.outbox_stats(target).await.unwrap_or((0, 0, 0));
    update_forward_status(state, target, err.is_none(), pending, sent_total, failed_total, err.clone()).await;
    if let Some(e) = err {
        anyhow::bail!(e);
    }
    Ok(())
}

async fn update_forward_status(
    state: &Arc<AppState>,
    target: &str,
    connected: bool,
    pending: u64,
    sent_total: u64,
    failed_total: u64,
    last_error: Option<String>,
) {
    let mut map = state.forward_status.write().await;
    let st = map.entry(target.to_string()).or_insert_with(|| ForwardStatus {
        target: target.to_string(),
        ..Default::default()
    });
    st.connected = connected;
    st.pending = pending;
    st.sent_total = sent_total;
    st.failed_total = failed_total;
    st.last_error = last_error;
    if connected {
        st.last_ok_at = Some(Local::now());
    }
    st.updated_at = Some(Local::now());
    let snapshot = st.clone();
    drop(map);
    state.bus.publish(Event::Forward(snapshot));
}
