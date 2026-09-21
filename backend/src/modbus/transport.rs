//! 串口传输：RTS 方向控制 + 静默/预期长度收帧（基于可取消的重叠 I/O）。

use super::frame::{find_valid_frame, hex};
use super::serial::{Canceller, Port};
use crate::config::{LinkProfile, RecvMode, RtsMode};
use crate::domain::Quality;
use crate::error::Result;
use std::io::ErrorKind;
use std::time::{Duration, Instant};

pub struct TransactOutcome {
    pub rx: Vec<u8>,
    pub rtt_ms: u64,
    pub quality: Quality,
    pub error: Option<String>,
}

pub struct SerialLink {
    port: Port,
    profile: LinkProfile,
}

impl SerialLink {
    pub fn open(profile: &LinkProfile) -> Result<Self> {
        let port = Port::open(profile)?;
        Ok(Self { port, profile: profile.clone() })
    }

    /// 取得可跨线程使用的取消器（用于把卡死的读打断）。
    pub fn canceller(&self) -> Canceller {
        self.port.canceller()
    }

    pub fn name(&self) -> &str {
        self.port.name()
    }

    fn set_rts(&mut self, active: bool) {
        if self.profile.rts_mode != RtsMode::Flip {
            return;
        }
        let level = if self.profile.rts_active_low { !active } else { active };
        let _ = self.port.set_rts(level);
    }

    /// 执行一次问答。addr 为期望应答地址；None 表示不校验地址（手工发帧）。
    pub fn transact(&mut self, tx: &[u8], addr: Option<u8>, expected_len: Option<usize>) -> TransactOutcome {
        let started = Instant::now();
        let timeout = Duration::from_millis(self.profile.response_timeout_ms.max(1));
        let silence = Duration::from_millis(self.profile.silence_ms.max(1));
        let write_timeout = Duration::from_millis(self.profile.response_timeout_ms.max(1000));

        let _ = self.port.clear_input();

        self.set_rts(true);
        let write_res = self.port.write_all_timeout(tx, write_timeout);
        self.set_rts(false);
        if let Err(e) = write_res {
            return TransactOutcome {
                rx: Vec::new(),
                rtt_ms: started.elapsed().as_millis() as u64,
                quality: Quality::Timeout,
                error: Some(format!("串口写失败: {e}")),
            };
        }

        if self.profile.frame_gap_ms > 0 {
            std::thread::sleep(Duration::from_millis(self.profile.frame_gap_ms));
        }

        let mut buf: Vec<u8> = Vec::with_capacity(256);
        let mut last_rx: Option<Instant> = None;
        let mut io_error: Option<String> = None;

        loop {
            if started.elapsed() >= timeout {
                break;
            }
            let remaining = timeout.saturating_sub(started.elapsed());
            let chunk_wait = match self.profile.recv_mode {
                RecvMode::ExpectedLen => remaining.min(Duration::from_millis(50)),
                RecvMode::Silence => Duration::from_millis(15),
            };

            let mut tmp = [0u8; 512];
            match self.port.read_timeout(&mut tmp, chunk_wait.max(Duration::from_millis(1))) {
                Ok(0) => {}
                Ok(n) => {
                    buf.extend_from_slice(&tmp[..n]);
                    last_rx = Some(Instant::now());
                }
                Err(e) if e.kind() == ErrorKind::TimedOut || e.kind() == ErrorKind::WouldBlock => {}
                Err(e) => {
                    io_error = Some(format!("串口读失败: {e}"));
                    break;
                }
            }

            if let Some(a) = addr {
                if let Some((off, len)) = find_valid_frame(&buf, a) {
                    let frame = buf[off..off + len].to_vec();
                    return TransactOutcome {
                        rx: frame,
                        rtt_ms: started.elapsed().as_millis() as u64,
                        quality: Quality::Ok,
                        error: None,
                    };
                }
            }

            if let (RecvMode::ExpectedLen, Some(want)) = (self.profile.recv_mode, expected_len) {
                if buf.len() >= want {
                    break;
                }
            }

            if let Some(t) = last_rx {
                if t.elapsed() >= silence {
                    break;
                }
            }
        }

        let rtt_ms = started.elapsed().as_millis() as u64;
        if let Some(a) = addr {
            if let Some((off, len)) = find_valid_frame(&buf, a) {
                return TransactOutcome {
                    rx: buf[off..off + len].to_vec(),
                    rtt_ms,
                    quality: Quality::Ok,
                    error: None,
                };
            }
        }
        if let Some(msg) = io_error {
            return TransactOutcome { rx: buf, rtt_ms, quality: Quality::Timeout, error: Some(msg) };
        }
        if buf.is_empty() {
            TransactOutcome {
                rx: buf,
                rtt_ms,
                quality: Quality::Timeout,
                error: Some("无应答（超时）".into()),
            }
        } else {
            let msg = format!("应答无法通过 CRC 校验: {}", hex(&buf));
            TransactOutcome {
                rx: buf,
                rtt_ms,
                quality: Quality::CrcError,
                error: Some(msg),
            }
        }
    }

    pub fn profile(&self) -> &LinkProfile {
        &self.profile
    }

    pub fn set_profile(&mut self, profile: LinkProfile) -> Result<()> {
        *self = SerialLink::open(&profile)?;
        Ok(())
    }
}
