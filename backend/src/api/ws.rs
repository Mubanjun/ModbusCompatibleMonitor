//! WebSocket 实时推送接口。
//!
//! 服务端 -> 客户端：事件对象（type 字段区分）：hello / round / sample / link_status /
//!                    frame / alarm / forward / notice
//! 客户端 -> 服务端：{"action":"ping"} / {"action":"poll_now"} / {"action":"snapshot"}

use crate::events::Event;
use crate::state::{AppState, VERSION};
use axum::extract::ws::{Message, WebSocket, WebSocketUpgrade};
use axum::extract::State;
use axum::response::IntoResponse;
use chrono::Local;
use futures_util::{SinkExt, StreamExt};
use serde_json::json;
use std::sync::Arc;
use tokio::sync::broadcast::error::RecvError;
use tokio::sync::mpsc;

pub async fn ws_handler(ws: WebSocketUpgrade, State(state): State<Arc<AppState>>) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle(socket, state))
}

pub async fn handle(socket: WebSocket, state: Arc<AppState>) {
    let (mut sink, mut stream) = socket.split();
    let (out_tx, mut out_rx) = mpsc::channel::<String>(2048);

    // 事件总线 -> 发送队列
    let mut sub = state.bus.subscribe();
    let out_tx_events = out_tx.clone();
    tokio::spawn(async move {
        loop {
            match sub.recv().await {
                Ok(ev) => {
                    let txt = serde_json::to_string(&ev).unwrap_or_default();
                    if out_tx_events.send(txt).await.is_err() {
                        break;
                    }
                }
                Err(RecvError::Lagged(_)) => continue,
                Err(RecvError::Closed) => break,
            }
        }
    });

    // 连接欢迎帧
    let hello = {
        let cfg = state.cfg.read().await;
        let profile = cfg.active_link.clone().unwrap_or_default();
        let protocol = cfg
            .active_profile()
            .map(|p| match p.protocol {
                crate::config::ProtocolKind::RsModbus => "rs_modbus",
                crate::config::ProtocolKind::StdModbus => "std_modbus",
            })
            .unwrap_or("unknown")
            .to_string();
        Event::Hello {
            server_time: Local::now(),
            version: VERSION.to_string(),
            device_sn: cfg.device.sn.clone(),
            active_link: profile,
            protocol,
        }
    };
    let _ = sink
        .send(Message::Text(serde_json::to_string(&hello).unwrap_or_default().into()))
        .await;

    loop {
        tokio::select! {
            maybe = out_rx.recv() => {
                match maybe {
                    Some(txt) => {
                        if sink.send(Message::Text(txt.into())).await.is_err() {
                            break;
                        }
                    }
                    None => break,
                }
            }
            msg = stream.next() => {
                match msg {
                    Some(Ok(Message::Text(text))) => {
                        if let Some(reply) = handle_client(text.as_str(), &state, &out_tx).await {
                            let _ = out_tx.send(reply).await;
                        }
                    }
                    Some(Ok(Message::Ping(p))) => {
                        let _ = sink.send(Message::Pong(p)).await;
                    }
                    Some(Ok(Message::Close(_))) | None => break,
                    Some(Ok(_)) => {}
                    Some(Err(_)) => break,
                }
            }
        }
    }
}

async fn handle_client(text: &str, state: &Arc<AppState>, out_tx: &mpsc::Sender<String>) -> Option<String> {
    let v: serde_json::Value = match serde_json::from_str(text) {
        Ok(v) => v,
        Err(_) => return None,
    };
    match v.get("action").and_then(|a| a.as_str()) {
        Some("ping") => Some(json!({ "type": "pong", "server_time": Local::now() }).to_string()),
        Some("poll_now") => {
            state.poll_now.notify_one();
            Some(json!({ "type": "notice", "level": "info", "message": "已触发立即采集" }).to_string())
        }
        Some("snapshot") => match state.store.latest(1).await {
            Ok(samples) => Some(json!({ "type": "snapshot", "samples": samples }).to_string()),
            Err(e) => Some(json!({ "type": "notice", "level": "error", "message": e.to_string() }).to_string()),
        },
        Some("status") => {
            let ls = state.link_status().await;
            let (rows, outbox) = state.store.count().await.unwrap_or((0, 0));
            Some(json!({ "type": "status", "link": ls, "rows": rows, "outbox": outbox }).to_string())
        }
        _ => {
            let _ = out_tx.send(
                json!({ "type": "notice", "level": "warn", "message": "未知 action" }).to_string(),
            ).await;
            None
        }
    }
}
