//! 本地缓冲存储（SQLite）：时序数据 + 发件箱 + 告警 + 回传日志。

use crate::domain::{AlarmRecord, Quality, Sample};
use crate::error::{AppError, Result};
use chrono::{DateTime, Local};
use rusqlite::{params, Connection, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

pub const MIGRATION: &str = include_str!("../migrations/001_sqlite.sql");

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OutboxRow {
    pub id: i64,
    pub target: String,
    pub payload: String,
    pub rows: i64,
    pub attempts: i64,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct OutboxBatch {
    pub samples: Vec<Sample>,
}

#[derive(Clone)]
pub struct Store {
    path: Arc<String>,
}

fn parse_time(s: &str) -> DateTime<Local> {
    DateTime::parse_from_rfc3339(s)
        .map(|d| d.with_timezone(&Local))
        .unwrap_or_else(|_| Local::now())
}

impl Store {
    pub fn new(path: impl Into<String>) -> Self {
        Self { path: Arc::new(path.into()) }
    }

    pub fn path(&self) -> &str {
        self.path.as_str()
    }

    async fn with_conn<T, F>(&self, f: F) -> Result<T>
    where
        F: FnOnce(&Connection) -> Result<T> + Send + 'static,
        T: Send + 'static,
    {
        let path = self.path.clone();
        tokio::task::spawn_blocking(move || {
            let conn = Connection::open(path.as_str())?;
            conn.pragma_update(None, "journal_mode", "WAL")?;
            conn.pragma_update(None, "synchronous", "NORMAL")?;
            conn.busy_timeout(std::time::Duration::from_secs(5))?;
            f(&conn)
        })
        .await
        .map_err(|e| AppError::Other(format!("数据库任务失败: {e}")))?
    }

    pub async fn init(&self) -> Result<()> {
        if let Some(parent) = std::path::Path::new(self.path.as_str()).parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)?;
            }
        }
        self.with_conn(|conn| {
            conn.execute_batch(MIGRATION)?;
            Ok(())
        })
        .await
    }

    /// 写入一轮采样，并生成一条发件箱批次（FR-16）。
    pub async fn insert_round(&self, targets: &[String], samples: &[Sample]) -> Result<()> {
        let targets = targets.to_vec();
        let samples = samples.to_vec();
        self.with_conn(move |conn| {
            let tx = conn.unchecked_transaction()?;
            {
                let mut stmt = tx.prepare(
                    "INSERT INTO ts_data
                       (record_time, device_sn, channel_no, channel_name, unit, value, raw_value, quality, rtt_ms)
                     VALUES (?1,?2,?3,?4,?5,?6,?7,?8,?9)
                     ON CONFLICT(record_time, device_sn, channel_no) DO UPDATE SET
                       value=excluded.value, raw_value=excluded.raw_value,
                       quality=excluded.quality, rtt_ms=excluded.rtt_ms, channel_name=excluded.channel_name, unit=excluded.unit",
                )?;
                for s in &samples {
                    stmt.execute(params![
                        s.record_time.to_rfc3339(),
                        s.device_sn,
                        s.channel_no as i64,
                        s.channel_name,
                        s.unit,
                        s.value,
                        s.raw_value,
                        s.quality.as_str(),
                        s.rtt_ms.map(|v| v as i64),
                    ])?;
                }
            }
            if !targets.is_empty() {
                let batch = OutboxBatch { samples: samples.clone() };
                let payload = serde_json::to_string(&batch)?;
                let now = Local::now().to_rfc3339();
                for target in &targets {
                    tx.execute(
                        "INSERT INTO outbox (target, payload, rows, attempts, created_at) VALUES (?1,?2,?3,0,?4)",
                        params![target, payload, samples.len() as i64, now],
                    )?;
                }
            }
            tx.commit()?;
            Ok(())
        })
        .await
    }

    pub async fn latest(&self, per_channel: u32) -> Result<Vec<Sample>> {
        let limit = per_channel.max(1) as i64;
        self.with_conn(move |conn| {
            let mut stmt = conn.prepare(
                "SELECT record_time, device_sn, channel_no, channel_name, unit, value, raw_value, quality, rtt_ms
                 FROM (
                   SELECT *, ROW_NUMBER() OVER (PARTITION BY channel_no ORDER BY record_time DESC) AS rn
                   FROM ts_data
                 ) WHERE rn <= ?1
                 ORDER BY channel_no, record_time DESC",
            )?;
            let rows = stmt.query_map(params![limit], row_to_sample)?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
        .await
    }

    pub async fn history(
        &self,
        device_sn: Option<String>,
        channel: Option<u16>,
        from: Option<String>,
        to: Option<String>,
        limit: u32,
    ) -> Result<Vec<Sample>> {
        self.with_conn(move |conn| {
            let mut sql = String::from(
                "SELECT record_time, device_sn, channel_no, channel_name, unit, value, raw_value, quality, rtt_ms
                 FROM ts_data WHERE 1=1",
            );
            let mut args: Vec<Box<dyn rusqlite::ToSql>> = Vec::new();
            if let Some(sn) = device_sn {
                sql.push_str(" AND device_sn = ?");
                args.push(Box::new(sn));
            }
            if let Some(ch) = channel {
                sql.push_str(" AND channel_no = ?");
                args.push(Box::new(ch as i64));
            }
            if let Some(f) = from {
                sql.push_str(" AND record_time >= ?");
                args.push(Box::new(f));
            }
            if let Some(t) = to {
                sql.push_str(" AND record_time <= ?");
                args.push(Box::new(t));
            }
            sql.push_str(" ORDER BY record_time DESC LIMIT ?");
            args.push(Box::new(limit.clamp(1, 100_000) as i64));
            let mut stmt = conn.prepare(&sql)?;
            let refs: Vec<&dyn rusqlite::ToSql> = args.iter().map(|b| b.as_ref()).collect();
            let rows = stmt.query_map(refs.as_slice(), row_to_sample)?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
        .await
    }

    pub async fn latest_alarms(&self, limit: u32) -> Result<Vec<AlarmRecord>> {
        self.with_conn(move |conn| {
            let mut stmt = conn.prepare(
                "SELECT id, device_sn, channel_no, channel_name, alarm_type, value, threshold, raised_at, cleared_at
                 FROM alarm ORDER BY raised_at DESC LIMIT ?1",
            )?;
            let rows = stmt.query_map(params![limit.clamp(1, 1000) as i64], row_to_alarm)?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
        .await
    }

    pub async fn insert_alarm(&self, a: &AlarmRecord) -> Result<i64> {
        let a = a.clone();
        self.with_conn(move |conn| {
            conn.execute(
                "INSERT INTO alarm (device_sn, channel_no, channel_name, alarm_type, value, threshold, raised_at, cleared_at)
                 VALUES (?1,?2,?3,?4,?5,?6,?7,?8)",
                params![
                    a.device_sn,
                    a.channel_no as i64,
                    a.channel_name,
                    a.alarm_type,
                    a.value,
                    a.threshold,
                    a.raised_at.to_rfc3339(),
                    a.cleared_at.map(|t| t.to_rfc3339()),
                ],
            )?;
            Ok(conn.last_insert_rowid())
        })
        .await
    }

    pub async fn clear_alarms(&self, device_sn: &str, channel_no: u16) -> Result<u32> {
        let device_sn = device_sn.to_string();
        self.with_conn(move |conn| {
            let n = conn.execute(
                "UPDATE alarm SET cleared_at = ?1 WHERE device_sn = ?2 AND channel_no = ?3 AND cleared_at IS NULL",
                params![Local::now().to_rfc3339(), device_sn, channel_no as i64],
            )?;
            Ok(n as u32)
        })
        .await
    }

    pub async fn pending_outbox(&self, target: &str, limit: u32, max_attempts: u32) -> Result<Vec<OutboxRow>> {
        let target = target.to_string();
        self.with_conn(move |conn| {
            let mut stmt = conn.prepare(
                "SELECT id, target, payload, rows, attempts, created_at FROM outbox
                 WHERE target = ?1 AND sent_at IS NULL AND attempts < ?2
                 ORDER BY id LIMIT ?3",
            )?;
            let rows = stmt.query_map(
                params![target, max_attempts as i64, limit.clamp(1, 5000) as i64],
                |row| {
                    Ok(OutboxRow {
                        id: row.get(0)?,
                        target: row.get(1)?,
                        payload: row.get(2)?,
                        rows: row.get(3)?,
                        attempts: row.get(4)?,
                        created_at: row.get(5)?,
                    })
                },
            )?;
            let mut out = Vec::new();
            for r in rows {
                out.push(r?);
            }
            Ok(out)
        })
        .await
    }

    pub async fn mark_sent(&self, ids: &[i64]) -> Result<()> {
        let ids: Vec<i64> = ids.to_vec();
        self.with_conn(move |conn| {
            let tx = conn.unchecked_transaction()?;
            for id in ids {
                tx.execute("UPDATE outbox SET sent_at = ?1 WHERE id = ?2", params![Local::now().to_rfc3339(), id])?;
            }
            tx.commit()?;
            Ok(())
        })
        .await
    }

    pub async fn mark_failed(&self, ids: &[i64], err: &str) -> Result<()> {
        let ids: Vec<i64> = ids.to_vec();
        let err = err.to_string();
        self.with_conn(move |conn| {
            let tx = conn.unchecked_transaction()?;
            for id in ids {
                tx.execute(
                    "UPDATE outbox SET attempts = attempts + 1, last_error = ?1 WHERE id = ?2",
                    params![err, id],
                )?;
            }
            tx.commit()?;
            Ok(())
        })
        .await
    }

    pub async fn outbox_stats(&self, target: &str) -> Result<(u64, u64, u64)> {
        let target = target.to_string();
        self.with_conn(move |conn| {
            let pending: i64 = conn.query_row(
                "SELECT COUNT(*) FROM outbox WHERE target = ?1 AND sent_at IS NULL",
                params![target],
                |r| r.get(0),
            )?;
            let sent: i64 = conn.query_row(
                "SELECT COUNT(*) FROM outbox WHERE target = ?1 AND sent_at IS NOT NULL",
                params![target],
                |r| r.get(0),
            )?;
            let failed: i64 = conn.query_row(
                "SELECT COUNT(*) FROM outbox WHERE target = ?1 AND sent_at IS NULL AND attempts > 0",
                params![target],
                |r| r.get(0),
            )?;
            Ok((pending as u64, sent as u64, failed as u64))
        })
        .await
    }

    pub async fn purge_before(&self, days: u32) -> Result<u64> {
        self.with_conn(move |conn| {
            let cutoff = (Local::now() - chrono::Duration::days(days as i64)).to_rfc3339();
            let n = conn.execute("DELETE FROM ts_data WHERE record_time < ?1", params![cutoff])?;
            let m = conn.execute(
                "DELETE FROM outbox WHERE sent_at IS NOT NULL AND created_at < ?1",
                params![cutoff],
            )?;
            Ok((n + m) as u64)
        })
        .await
    }

    pub async fn count(&self) -> Result<(u64, u64)> {
        self.with_conn(|conn| {
            let data: i64 = conn.query_row("SELECT COUNT(*) FROM ts_data", [], |r| r.get(0))?;
            let outbox: i64 = conn.query_row("SELECT COUNT(*) FROM outbox", [], |r| r.get(0))?;
            Ok((data as u64, outbox as u64))
        })
        .await
    }

    pub async fn get_meta(&self, key: &str) -> Result<Option<String>> {
        let key = key.to_string();
        self.with_conn(move |conn| {
            let v = conn
                .query_row("SELECT value FROM meta WHERE key = ?1", params![key], |r| r.get::<_, String>(0))
                .optional()?;
            Ok(v)
        })
        .await
    }

    pub async fn set_meta(&self, key: &str, value: &str) -> Result<()> {
        let key = key.to_string();
        let value = value.to_string();
        self.with_conn(move |conn| {
            conn.execute(
                "INSERT INTO meta (key, value) VALUES (?1, ?2) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                params![key, value],
            )?;
            Ok(())
        })
        .await
    }
}

fn row_to_sample(row: &rusqlite::Row<'_>) -> rusqlite::Result<Sample> {
    let q: String = row.get(7)?;
    let quality = match q.as_str() {
        "ok" => Quality::Ok,
        "timeout" => Quality::Timeout,
        "crc_error" => Quality::CrcError,
        "illegal_addr" => Quality::IllegalAddr,
        "slave_fault" => Quality::SlaveFault,
        "scaling" => Quality::Scaling,
        "disabled" => Quality::Disabled,
        _ => Quality::NoData,
    };
    Ok(Sample {
        record_time: parse_time(&row.get::<_, String>(0)?),
        device_sn: row.get(1)?,
        channel_no: row.get::<_, i64>(2)? as u16,
        channel_name: row.get(3)?,
        unit: row.get(4)?,
        value: row.get(5)?,
        raw_value: row.get(6)?,
        quality,
        rtt_ms: row.get::<_, Option<i64>>(8)?.map(|v| v as u32),
    })
}

fn row_to_alarm(row: &rusqlite::Row<'_>) -> rusqlite::Result<AlarmRecord> {
    Ok(AlarmRecord {
        id: row.get(0)?,
        device_sn: row.get(1)?,
        channel_no: row.get::<_, i64>(2)? as u16,
        channel_name: row.get(3)?,
        alarm_type: row.get(4)?,
        value: row.get(5)?,
        threshold: row.get(6)?,
        raised_at: parse_time(&row.get::<_, String>(7)?),
        cleared_at: row.get::<_, Option<String>>(8)?.map(|s| parse_time(&s)),
    })
}
