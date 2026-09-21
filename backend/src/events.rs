//! 事件总线与推送给前端的事件模型。

use crate::domain::{AlarmRecord, Sample};
use chrono::{DateTime, Local};
use serde::Serialize;
use tokio::sync::broadcast;

#[derive(Clone, Debug, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Event {
    Hello {
        server_time: DateTime<Local>,
        version: String,
        device_sn: String,
        active_link: String,
        protocol: String,
    },
    Round(RoundSummary),
    Sample(Sample),
    LinkStatus(LinkStatus),
    Frame(FrameLog),
    Alarm(AlarmRecord),
    Forward(ForwardStatus),
    Notice {
        level: String,
        message: String,
    },
}

#[derive(Clone, Debug, Serialize, Default)]
pub struct RoundSummary {
    pub round_id: u64,
    pub started_at: Option<DateTime<Local>>,
    pub elapsed_ms: u64,
    pub total: u32,
    pub ok: u32,
    pub fail: u32,
    pub avg_rtt_ms: Option<u64>,
}

#[derive(Clone, Debug, Serialize)]
pub struct FrameLog {
    pub at: DateTime<Local>,
    /// tx / rx
    pub direction: String,
    pub addr: Option<u8>,
    pub func: Option<u8>,
    pub bytes: String,
    pub rtt_ms: Option<u64>,
    pub quality: Option<String>,
    pub note: Option<String>,
}

#[derive(Clone, Debug, Serialize)]
pub struct LinkStatus {
    pub profile: String,
    pub kind: String,
    pub port: String,
    pub baud: u32,
    pub protocol: String,
    pub connected: bool,
    pub last_error: Option<String>,
    pub tx_count: u64,
    pub rx_count: u64,
    pub ok_count: u64,
    pub timeout_count: u64,
    pub crc_error_count: u64,
    pub retry_count: u64,
    pub last_rtt_ms: Option<u64>,
    pub updated_at: DateTime<Local>,
}

impl LinkStatus {
    pub fn new(profile: &str, kind: &str, port: &str, baud: u32, protocol: &str) -> Self {
        Self {
            profile: profile.to_string(),
            kind: kind.to_string(),
            port: port.to_string(),
            baud,
            protocol: protocol.to_string(),
            connected: false,
            last_error: None,
            tx_count: 0,
            rx_count: 0,
            ok_count: 0,
            timeout_count: 0,
            crc_error_count: 0,
            retry_count: 0,
            last_rtt_ms: None,
            updated_at: Local::now(),
        }
    }
}

#[derive(Clone, Debug, Serialize, Default)]
pub struct ForwardStatus {
    pub target: String,
    pub enabled: bool,
    pub connected: bool,
    pub pending: u64,
    pub sent_total: u64,
    pub failed_total: u64,
    pub last_error: Option<String>,
    pub last_ok_at: Option<DateTime<Local>>,
    pub updated_at: Option<DateTime<Local>>,
}

/// 广播式事件总线（容量满时丢弃最旧事件，不阻塞采集）。
#[derive(Clone)]
pub struct EventBus {
    tx: broadcast::Sender<Event>,
}

impl EventBus {
    pub fn new(capacity: usize) -> Self {
        let (tx, _) = broadcast::channel(capacity.max(16));
        Self { tx }
    }

    pub fn subscribe(&self) -> broadcast::Receiver<Event> {
        self.tx.subscribe()
    }

    pub fn sender(&self) -> broadcast::Sender<Event> {
        self.tx.clone()
    }

    pub fn publish(&self, event: Event) {
        let _ = self.tx.send(event);
    }
}
