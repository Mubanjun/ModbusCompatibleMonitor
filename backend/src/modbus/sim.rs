//! 内置 Modbus 从站模拟器（无硬件 / Linux 自测用）。
//!
//! 模拟一台处于 RS-Modbus 规约的主机：从站地址 1~32 均在线，
//! 每个通道 64 个寄存器，reg0/reg1 为缓变的模拟量，reg37~42 为全局 RTC。

use super::crc::{append_crc, crc16};
use super::transport::TransactOutcome;
use crate::domain::Quality;
use chrono::{Datelike, Local, Timelike};
use std::collections::HashMap;
use std::f64::consts::PI;
use std::time::{Duration, Instant};

const MAX_ADDR: u8 = 32;
const REGS: usize = 64;

pub struct SimDevice {
    file: HashMap<u8, Vec<u16>>,
    tick: u64,
}

impl SimDevice {
    pub fn new() -> Self {
        let mut file = HashMap::new();
        for a in 1..=MAX_ADDR {
            let mut regs = vec![0u16; REGS];
            regs[8] = 1;
            regs[9] = 19;
            regs[12] = 999 + a as u16;
            regs[13] = 1;
            regs[15] = 1;
            regs[16] = 1000 + a as u16;
            regs[17] = 1;
            regs[19] = 1;
            regs[20] = 30;
            regs[21] = 3;
            regs[31] = 30;
            regs[32] = 30;
            regs[33] = 3;
            file.insert(a, regs);
        }
        Self { file, tick: 0 }
    }

    fn refresh(&mut self) {
        self.tick += 1;
        let now = Local::now();
        let t = self.tick as f64;
        let addrs: Vec<u8> = self.file.keys().copied().collect();
        for a in addrs {
            let regs = self.file.get_mut(&a).unwrap();
            // 模拟量1（有符号，温度类）：15~35
            let v1 = 25.0 + 8.0 * (t / 12.0 + a as f64).sin();
            regs[1] = v1.round() as i16 as u16;
            // 模拟量2（无符号，湿度类）：40~80
            let v2 = 60.0 + 15.0 * (t / 17.0 + a as f64 * 0.7 + PI).cos();
            regs[0] = v2.round() as u16;
            // 全局 RTC
            regs[37] = now.year() as u16;
            regs[38] = now.month() as u16;
            regs[39] = now.day() as u16;
            regs[40] = now.hour() as u16;
            regs[41] = now.minute() as u16;
            regs[42] = now.second() as u16;
        }
    }

    pub fn transact(&mut self, tx: &[u8], _expect_addr: Option<u8>) -> TransactOutcome {
        let started = Instant::now();
        std::thread::sleep(Duration::from_millis(4));
        self.refresh();

        if tx.len() < 4 {
            return TransactOutcome { rx: Vec::new(), rtt_ms: 0, quality: Quality::CrcError, error: Some("模拟器：请求过短".into()) };
        }
        let req_addr = tx[0];
        let func = tx[1];
        if req_addr == 0 || req_addr > MAX_ADDR {
            // 离线地址：静默（超时）
            return TransactOutcome {
                rx: Vec::new(),
                rtt_ms: started.elapsed().as_millis() as u64,
                quality: Quality::Timeout,
                error: Some(format!("模拟器：地址 {req_addr} 不在线")),
            };
        }
        if crc16(&tx[..tx.len() - 2]) != u16::from_le_bytes([tx[tx.len() - 2], tx[tx.len() - 1]]) {
            return TransactOutcome {
                rx: Vec::new(),
                rtt_ms: started.elapsed().as_millis() as u64,
                quality: Quality::Timeout,
                error: Some("模拟器：请求 CRC 错误，已丢弃".into()),
            };
        }
        let regs = self.file.get(&req_addr).cloned().unwrap_or_else(|| vec![0u16; REGS]);

        let mut out: Vec<u8> = Vec::new();
        match func {
            0x03 | 0x04 => {
                if tx.len() < 6 {
                    return TransactOutcome { rx: Vec::new(), rtt_ms: 0, quality: Quality::CrcError, error: Some("模拟器：读请求过短".into()) };
                }
                let start = u16::from_be_bytes([tx[2], tx[3]]);
                let count = u16::from_be_bytes([tx[4], tx[5]]).clamp(1, 124);
                out.push(req_addr);
                out.push(0x03);
                out.push((count * 2) as u8);
                for i in 0..count {
                    let idx = start as usize + i as usize;
                    let v = regs.get(idx).copied().unwrap_or(0);
                    out.push((v >> 8) as u8);
                    out.push((v & 0xFF) as u8);
                }
                append_crc(&mut out);
            }
            0x06 => {
                if tx.len() >= 6 {
                    let reg = u16::from_be_bytes([tx[2], tx[3]]);
                    let val = u16::from_be_bytes([tx[4], tx[5]]);
                    if let Some(r) = self.file.get_mut(&req_addr) {
                        if (reg as usize) < r.len() {
                            r[reg as usize] = val;
                        }
                    }
                }
                out.extend_from_slice(&tx[..8.min(tx.len())]);
            }
            0x10 => {
                out.push(req_addr);
                out.push(0x10);
                out.extend_from_slice(&tx[2..6]);
                append_crc(&mut out);
            }
            _ => {
                return TransactOutcome {
                    rx: Vec::new(),
                    rtt_ms: started.elapsed().as_millis() as u64,
                    quality: Quality::Timeout,
                    error: Some(format!("模拟器：不支持功能码 0x{func:02X}")),
                };
            }
        }

        TransactOutcome {
            rx: out,
            rtt_ms: started.elapsed().as_millis() as u64,
            quality: Quality::Ok,
            error: None,
        }
    }
}
