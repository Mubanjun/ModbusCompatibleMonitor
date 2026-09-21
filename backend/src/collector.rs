//! 采集调度核心：按规约读取通道、解析、换算。

use crate::config::{LinkProfile, ProtocolKind, StdReadMode};
use crate::domain::{ChannelConfig, DataType, Quality, Sample};
use crate::modbus::link::{LinkError, LinkHandle};
use chrono::{DateTime, Local};
use std::time::Instant;

#[derive(Debug, Clone, Default)]
pub struct RoundOutcome {
    pub samples: Vec<Sample>,
    pub elapsed_ms: u64,
    pub ok: u32,
    pub fail: u32,
    pub avg_rtt_ms: Option<u64>,
}

fn round_to(v: f64, d: u8) -> f64 {
    let m = 10f64.powi(d as i32);
    (v * m).round() / m
}

/// 16 位原始值 → (raw, 工程量)。
pub fn compute_from_16(ch: &ChannelConfig, ai1: i16, ai2: u16) -> (i64, f64) {
    match ch.data_type {
        DataType::Ai1 | DataType::Ai1Ai2 => {
            let r = ai1 as i64;
            (r, ch.scale(r as f64))
        }
        DataType::Ai2 => {
            let r = ai2 as i64;
            (r, ch.scale(ai2 as f64))
        }
        DataType::U32 => {
            let r = (((ai1 as u16 as u32) << 16) | ai2 as u32) as i64;
            (r, ch.scale(r as f64))
        }
        DataType::I32 => {
            let r = ((((ai1 as u16 as u32) << 16) | ai2 as u32) as i32) as i64;
            (r, ch.scale(r as f64))
        }
        DataType::F32 => {
            let bits = ((ai1 as u16 as u32) << 16) | ai2 as u32;
            let f = f32::from_bits(bits) as f64;
            (bits as i64, ch.scale(f))
        }
        DataType::Switch => {
            let r = (ai1 != 0) as i64;
            (r, r as f64)
        }
    }
}

fn base_sample(ch: &ChannelConfig, device_sn: &str, t: DateTime<Local>, quality: Quality) -> Sample {
    Sample::new(ch, device_sn, t, quality)
}

fn ok_sample(ch: &ChannelConfig, device_sn: &str, t: DateTime<Local>, ai1: i16, ai2: u16, rtt: u32) -> Sample {
    let (raw, value) = compute_from_16(ch, ai1, ai2);
    let mut s = base_sample(ch, device_sn, t, Quality::Ok);
    s.raw_value = Some(raw);
    s.value = Some(round_to(value, ch.decimals));
    s.rtt_ms = Some(rtt);
    s
}

fn err_sample(ch: &ChannelConfig, device_sn: &str, t: DateTime<Local>, quality: Quality, rtt: u64) -> Sample {
    let mut s = base_sample(ch, device_sn, t, quality);
    s.rtt_ms = Some(rtt as u32);
    s
}

/// 执行一轮采集。
pub async fn poll_round(
    link: &LinkHandle,
    profile: &LinkProfile,
    channels: &[ChannelConfig],
    device_sn: &str,
    deadline: Option<Instant>,
) -> RoundOutcome {
    let started = Instant::now();
    let now = Local::now();
    let enabled: Vec<&ChannelConfig> = channels.iter().filter(|c| c.enabled).collect();
    let mut samples: Vec<Sample> = Vec::with_capacity(enabled.len());

    match profile.protocol {
        ProtocolKind::RsModbus => {
            // 从站地址 = 通道号，逐通道读 0x0000 起 2 个寄存器
            for ch in &enabled {
                if let Some(d) = deadline {
                    if Instant::now() >= d {
                        samples.push(err_sample(ch, device_sn, now, Quality::Timeout, 0));
                        continue;
                    }
                }
                let addr = if ch.slave_addr == 0 { ch.no as u8 } else { ch.slave_addr };
                match link.read_registers(addr, 0, 2).await {
                    Ok(regs) if regs.len() >= 2 => {
                        // RS-Modbus：reg0 = 模拟量2（无符号），reg1 = 模拟量1（有符号）
                        let ai2 = regs[0];
                        let ai1 = regs[1] as i16;
                        let rtt = link.status.borrow().last_rtt_ms.unwrap_or(0) as u32;
                        samples.push(ok_sample(ch, device_sn, now, ai1, ai2, rtt));
                    }
                    Ok(_) => samples.push(err_sample(ch, device_sn, now, Quality::CrcError, 0)),
                    Err(e) => samples.push(err_sample(ch, device_sn, now, e.quality, e.rtt_ms)),
                }
            }
        }
        ProtocolKind::StdModbus => {
            std_round(link, profile, &enabled, device_sn, now, deadline, &mut samples).await;
        }
    }

    let ok = samples.iter().filter(|s| s.quality == Quality::Ok).count() as u32;
    let fail = samples.len() as u32 - ok;
    let rtts: Vec<u64> = samples.iter().filter_map(|s| s.rtt_ms).map(|v| v as u64).collect();
    let avg = if rtts.is_empty() { None } else { Some(rtts.iter().sum::<u64>() / rtts.len() as u64) };

    RoundOutcome {
        samples,
        elapsed_ms: started.elapsed().as_millis() as u64,
        ok,
        fail,
        avg_rtt_ms: avg,
    }
}

async fn std_round(
    link: &LinkHandle,
    profile: &LinkProfile,
    enabled: &[&ChannelConfig],
    device_sn: &str,
    now: DateTime<Local>,
    deadline: Option<Instant>,
    out: &mut Vec<Sample>,
) {
    if enabled.is_empty() {
        return;
    }
    let regs_per: u16 = match profile.std_read_mode {
        StdReadMode::Raw => 2,
        StdReadMode::Processed => 4,
    };
    let base: u16 = match profile.std_read_mode {
        StdReadMode::Raw => 0,
        StdReadMode::Processed => 64,
    };
    let max_no = enabled.iter().map(|c| c.no).max().unwrap_or(0);
    let total = regs_per * max_no;
    let chunk = profile.chunk_regs.clamp(1, 124);
    let mut buf = vec![0u16; total as usize];
    let mut bulk_err: Option<LinkError> = None;
    let mut bulk_rtt: u32 = 0;
    let mut start: u16 = 0;
    while start < total {
        if let Some(d) = deadline {
            if Instant::now() >= d {
                bulk_err = Some(LinkError::internal("轮询软超时，剩余通道跳过"));
                break;
            }
        }
        let cnt = chunk.min(total - start);
        match link.read_registers(profile.std_slave_addr, base + start, cnt).await {
            Ok(mut regs) => {
                regs.resize(cnt as usize, 0);
                buf[start as usize..(start + cnt) as usize].copy_from_slice(&regs);
                bulk_rtt = link.status.borrow().last_rtt_ms.unwrap_or(0) as u32;
            }
            Err(e) => {
                bulk_err = Some(e);
                break;
            }
        }
        start += cnt;
    }

    if let Some(e) = bulk_err {
        // 批量失败 → 逐通道回退
        for ch in enabled {
            if let Some(d) = deadline {
                if Instant::now() >= d {
                    out.push(err_sample(ch, device_sn, now, Quality::Timeout, 0));
                    continue;
                }
            }
            let reg_start = base + regs_per * (ch.no - 1);
            match link.read_registers(profile.std_slave_addr, reg_start, regs_per).await {
                Ok(regs) if regs.len() >= 2 => {
                    let ai1 = regs[0] as i16;
                    let ai2 = regs[1];
                    let rtt = link.status.borrow().last_rtt_ms.unwrap_or(0) as u32;
                    out.push(ok_sample(ch, device_sn, now, ai1, ai2, rtt));
                }
                Ok(_) => out.push(err_sample(ch, device_sn, now, Quality::CrcError, 0)),
                Err(err) => out.push(err_sample(ch, device_sn, now, err.quality, err.rtt_ms)),
            }
        }
        let _ = e;
        return;
    }

    for ch in enabled {
        let idx = regs_per as usize * (ch.no.saturating_sub(1)) as usize;
        match profile.std_read_mode {
            StdReadMode::Raw => {
                if idx + 1 >= buf.len() {
                    out.push(err_sample(ch, device_sn, now, Quality::NoData, 0));
                    continue;
                }
                let ai1 = buf[idx] as i16;
                let ai2 = buf[idx + 1];
                out.push(ok_sample(ch, device_sn, now, ai1, ai2, bulk_rtt));
            }
            StdReadMode::Processed => {
                if idx + 3 >= buf.len() {
                    out.push(err_sample(ch, device_sn, now, Quality::NoData, 0));
                    continue;
                }
                let f1 = f32::from_bits(((buf[idx] as u32) << 16) | buf[idx + 1] as u32) as f64;
                let f2 = f32::from_bits(((buf[idx + 2] as u32) << 16) | buf[idx + 3] as u32) as f64;
                let v = match ch.data_type {
                    DataType::Ai2 => f2,
                    _ => f1,
                };
                let mut s = base_sample(ch, device_sn, now, Quality::Ok);
                s.value = Some(round_to(ch.scale(v), ch.decimals));
                s.raw_value = Some(v as i64);
                s.rtt_ms = Some(bulk_rtt);
                out.push(s);
            }
        }
    }
}
