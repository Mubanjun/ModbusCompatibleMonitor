//! 应用共享状态。

use crate::alarm::AlarmEngine;
use crate::config::AppConfig;
use crate::events::{EventBus, ForwardStatus, FrameLog, LinkStatus, RoundSummary};
use crate::forwarder::MysqlSink;
use crate::modbus::link::LinkHandle;
use crate::store::Store;
use chrono::{DateTime, Local};
use std::collections::{HashMap, VecDeque};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use tokio::sync::{Notify, RwLock};

pub const VERSION: &str = env!("CARGO_PKG_VERSION");

#[derive(Debug, Clone, Default)]
pub struct RuntimeInfo {
    pub last_round: Option<RoundSummary>,
    pub last_error: Option<String>,
    pub round_total: u64,
}

pub struct AppState {
    pub bus: EventBus,
    pub store: Store,
    pub link: LinkHandle,
    pub cfg: RwLock<AppConfig>,
    pub runtime: RwLock<RuntimeInfo>,
    pub alarms: Mutex<AlarmEngine>,
    pub sinks: Arc<Mutex<HashMap<String, MysqlSink>>>,
    pub forward_status: RwLock<HashMap<String, ForwardStatus>>,
    pub frames: Mutex<VecDeque<FrameLog>>,
    pub frame_cap: usize,
    pub started_at: DateTime<Local>,
    pub poll_now: Notify,
    round_id: AtomicU64,
}

impl AppState {
    pub fn new(cfg: AppConfig, store: Store, bus: EventBus, link: LinkHandle, frame_cap: usize) -> Arc<Self> {
        let sinks: HashMap<String, MysqlSink> = cfg
            .forward
            .targets
            .iter()
            .map(|t| (t.name.clone(), MysqlSink::new(t)))
            .collect();
        let mut forward_status = HashMap::new();
        for t in &cfg.forward.targets {
            forward_status.insert(
                t.name.clone(),
                ForwardStatus {
                    target: t.name.clone(),
                    enabled: t.enabled,
                    ..Default::default()
                },
            );
        }
        Arc::new(Self {
            bus,
            store,
            link,
            cfg: RwLock::new(cfg),
            runtime: RwLock::new(RuntimeInfo::default()),
            alarms: Mutex::new(AlarmEngine::new()),
            sinks: Arc::new(Mutex::new(sinks)),
            forward_status: RwLock::new(forward_status),
            frames: Mutex::new(VecDeque::new()),
            frame_cap: frame_cap.max(16),
            started_at: Local::now(),
            poll_now: Notify::new(),
            round_id: AtomicU64::new(0),
        })
    }

    pub fn next_round_id(&self) -> u64 {
        self.round_id.fetch_add(1, Ordering::Relaxed) + 1
    }

    pub fn push_frame(&self, log: FrameLog) {
        if let Ok(mut q) = self.frames.lock() {
            if q.len() >= self.frame_cap {
                q.pop_front();
            }
            q.push_back(log);
        }
    }

    pub fn recent_frames(&self, limit: usize) -> Vec<FrameLog> {
        match self.frames.lock() {
            Ok(q) => {
                let n = q.len();
                let start = n.saturating_sub(limit.min(n));
                q.iter().skip(start).cloned().collect()
            }
            Err(_) => Vec::new(),
        }
    }

    pub async fn link_status(&self) -> LinkStatus {
        self.link.snapshot()
    }
}
