//! 上下限告警引擎：产生 / 复归（FR-06）。

use crate::domain::{AlarmRecord, ChannelConfig, Quality, Sample};
use chrono::Local;
use std::collections::HashMap;

#[derive(Debug, Clone)]
pub enum AlarmChange {
    Raised(AlarmRecord),
    Cleared(AlarmRecord),
}

#[derive(Default)]
pub struct AlarmEngine {
    /// (device_sn, channel_no) -> alarm_type ("high"/"low")
    active: HashMap<(String, u16), String>,
}

impl AlarmEngine {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn active_count(&self) -> usize {
        self.active.len()
    }

    /// 对一条采样做上下限判定，返回需要产生/复归的告警。
    pub fn check(&mut self, sample: &Sample, cfg: &ChannelConfig) -> Vec<AlarmChange> {
        let key = (sample.device_sn.clone(), sample.channel_no);
        let mut changes = Vec::new();

        if sample.quality != Quality::Ok {
            return changes;
        }
        let Some(value) = sample.value else {
            return changes;
        };

        let now = Local::now();
        let breach: Option<(&str, f64)> = match (cfg.upper_limit, cfg.lower_limit) {
            (Some(hi), _) if value > hi => Some(("high", hi)),
            (_, Some(lo)) if value < lo => Some(("low", lo)),
            _ => None,
        };

        match breach {
            Some((kind, threshold)) => {
                if self.active.get(&key).map(|s| s.as_str()) != Some(kind) {
                    self.active.insert(key.clone(), kind.to_string());
                    changes.push(AlarmChange::Raised(AlarmRecord {
                        id: 0,
                        device_sn: sample.device_sn.clone(),
                        channel_no: sample.channel_no,
                        channel_name: sample.channel_name.clone(),
                        alarm_type: kind.to_string(),
                        value: Some(value),
                        threshold: Some(threshold),
                        raised_at: now,
                        cleared_at: None,
                    }));
                }
            }
            None => {
                if let Some(kind) = self.active.remove(&key) {
                    changes.push(AlarmChange::Cleared(AlarmRecord {
                        id: 0,
                        device_sn: sample.device_sn.clone(),
                        channel_no: sample.channel_no,
                        channel_name: sample.channel_name.clone(),
                        alarm_type: kind,
                        value: Some(value),
                        threshold: None,
                        raised_at: now,
                        cleared_at: Some(now),
                    }));
                }
            }
        }
        changes
    }
}
