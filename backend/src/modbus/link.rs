//! 串口链路工作线程：独占串口、串行执行问答、按档案重试、发布原始帧事件。
//!
//! 关键健壮性设计：
//! - 每个请求都有 **IPC 超时**；超时后由异步侧直接 CancelIoEx 打断底层 I/O。
//! - 连续失败达到阈值后 **关闭并重开串口**（FR-10 自动重连）。
//! - 支持 **Shutdown**，保证进程可以优雅退出、端口一定被释放。

use crate::config::LinkProfile;
use crate::domain::Quality;
use crate::events::{Event, EventBus, FrameLog, LinkStatus};
use crate::modbus::frame::{build_read, build_write_multi, build_write_single, hex, parse_read, FrameError};
use crate::modbus::serial::Canceller;
use crate::modbus::transport::SerialLink;
use chrono::Local;
use std::sync::{Arc, Mutex as StdMutex};
use std::thread;
use std::time::Duration;
use tokio::sync::{mpsc, oneshot, watch};

/// 连续失败多少次后强制关闭并重开串口。
const REOPEN_AFTER_FAILURES: u32 = 5;

#[derive(Debug, Clone)]
pub struct LinkError {
    pub quality: Quality,
    pub message: String,
    pub tx: String,
    pub rx: String,
    pub rtt_ms: u64,
    pub attempts: u32,
}

pub type LinkResult<T> = std::result::Result<T, LinkError>;

pub enum LinkRequest {
    ReadRegisters {
        addr: u8,
        start: u16,
        count: u16,
        reply: oneshot::Sender<LinkResult<Vec<u16>>>,
    },
    WriteRegister {
        addr: u8,
        reg: u16,
        value: u16,
        reply: oneshot::Sender<LinkResult<()>>,
    },
    WriteRegisters {
        addr: u8,
        start: u16,
        values: Vec<u16>,
        reply: oneshot::Sender<LinkResult<()>>,
    },
    Raw {
        tx: Vec<u8>,
        addr: Option<u8>,
        expect_len: Option<usize>,
        reply: oneshot::Sender<LinkResult<Vec<u8>>>,
    },
    Scan {
        from: u8,
        to: u8,
        start: u16,
        count: u16,
        reply: oneshot::Sender<Vec<(u8, LinkResult<Vec<u16>>)>>,
    },
    Reload {
        profile: Box<LinkProfile>,
        reply: oneshot::Sender<Result<(), String>>,
    },
    /// 优雅停机：关闭串口并结束链路线程。
    Shutdown,
}

#[derive(Clone)]
pub struct LinkHandle {
    tx: mpsc::UnboundedSender<LinkRequest>,
    pub status: watch::Receiver<LinkStatus>,
    canceller: Arc<StdMutex<Option<Canceller>>>,
    ipc_timeout: Duration,
}

impl LinkHandle {
    /// 启动链路工作线程（阻塞式串口 I/O 独占一个 OS 线程）。
    pub fn spawn(profile: LinkProfile, bus: EventBus, frames_enabled: bool) -> Self {
        let (tx, rx) = mpsc::unbounded_channel();
        let status = LinkStatus::new(
            &profile.name,
            match profile.kind {
                crate::config::LinkKind::Wired => "wired",
                crate::config::LinkKind::Lora => "lora",
                crate::config::LinkKind::Simulator => "simulator",
                crate::config::LinkKind::Tcp => "tcp",
            },
            &profile.port,
            profile.baud,
            match profile.protocol {
                crate::config::ProtocolKind::RsModbus => "rs_modbus",
                crate::config::ProtocolKind::StdModbus => "std_modbus",
            },
        );
        // IPC 超时 = 链路层最坏耗时 + 宽裕余量
        let ipc_timeout = Duration::from_millis(
            profile.response_timeout_ms * (profile.retries as u64 + 1)
                + profile.retry_interval_ms * profile.retries as u64
                + 2000,
        );
        let (status_tx, status_rx) = watch::channel(status);
        let canceller = Arc::new(StdMutex::new(None));
        let mut worker = Worker {
            link: None,
            sim: None,
            profile,
            bus: bus.sender(),
            frames_enabled,
            status: status_rx.borrow().clone(),
            status_tx,
            canceller: canceller.clone(),
            consecutive_failures: 0,
        };
        thread::Builder::new()
            .name("modbus-link".into())
            .spawn(move || worker.run(rx))
            .expect("启动链路线程失败");
        Self { tx, status: status_rx, canceller, ipc_timeout }
    }

    /// 通用请求：带 IPC 超时；超时则强制取消底层 I/O。
    async fn request<T, F>(&self, build: F) -> LinkResult<T>
    where
        F: FnOnce(oneshot::Sender<LinkResult<T>>) -> LinkRequest,
    {
        let (reply, rx) = oneshot::channel();
        if self.tx.send(build(reply)).is_err() {
            return Err(LinkError::internal("链路线程已退出"));
        }
        match tokio::time::timeout(self.ipc_timeout, rx).await {
            Ok(Ok(result)) => result,
            Ok(Err(_)) => Err(LinkError::internal("链路线程已退出")),
            Err(_) => {
                // 关键：打断可能卡死的底层读，避免整个后端被拖住
                self.cancel_pending_io();
                Err(LinkError {
                    quality: Quality::Timeout,
                    message: format!(
                        "链路 I/O 超时（>{} ms），已强制取消底层操作并触发端口重建",
                        self.ipc_timeout.as_millis()
                    ),
                    tx: String::new(),
                    rx: String::new(),
                    rtt_ms: self.ipc_timeout.as_millis() as u64,
                    attempts: 0,
                })
            }
        }
    }

    /// 强制取消底层未完成的串口 I/O（可从任意线程调用）。
    pub fn cancel_pending_io(&self) {
        if let Ok(guard) = self.canceller.lock() {
            if let Some(c) = guard.as_ref() {
                c.cancel();
            }
        }
    }

    /// 优雅停机：让 worker 关闭串口并退出线程。
    pub async fn shutdown(&self) {
        let _ = self.tx.send(LinkRequest::Shutdown);
        // 给 worker 一点时间释放端口；随后由进程退出兜底
        tokio::time::sleep(Duration::from_millis(200)).await;
    }

    pub async fn read_registers(&self, addr: u8, start: u16, count: u16) -> LinkResult<Vec<u16>> {
        self.request(|reply| LinkRequest::ReadRegisters { addr, start, count, reply })
            .await
    }

    pub async fn write_register(&self, addr: u8, reg: u16, value: u16) -> LinkResult<()> {
        self.request(|reply| LinkRequest::WriteRegister { addr, reg, value, reply })
            .await
    }

    pub async fn write_registers(&self, addr: u8, start: u16, values: Vec<u16>) -> LinkResult<()> {
        self.request(|reply| LinkRequest::WriteRegisters { addr, start, values, reply })
            .await
    }

    pub async fn raw(&self, tx: Vec<u8>, addr: Option<u8>, expect_len: Option<usize>) -> LinkResult<Vec<u8>> {
        self.request(|reply| LinkRequest::Raw { tx, addr, expect_len, reply })
            .await
    }

    pub async fn scan(&self, from: u8, to: u8, start: u16, count: u16) -> Vec<(u8, LinkResult<Vec<u16>>)> {
        let (reply, rx) = oneshot::channel();
        if self.tx.send(LinkRequest::Scan { from, to, start, count, reply }).is_err() {
            return Vec::new();
        }
        match tokio::time::timeout(self.ipc_timeout * (to.saturating_sub(from) as u32 + 2), rx).await {
            Ok(Ok(v)) => v,
            Ok(Err(_)) => Vec::new(),
            Err(_) => {
                self.cancel_pending_io();
                Vec::new()
            }
        }
    }

    pub async fn reload(&self, profile: LinkProfile) -> Result<(), String> {
        let (reply, rx) = oneshot::channel();
        if self.tx.send(LinkRequest::Reload { profile: Box::new(profile), reply }).is_err() {
            return Err("链路线程已退出".into());
        }
        match tokio::time::timeout(Duration::from_secs(5), rx).await {
            Ok(Ok(r)) => r,
            Ok(Err(_)) => Err("链路线程已退出".into()),
            Err(_) => {
                self.cancel_pending_io();
                Err("重载链路超时（端口可能被占用）".into())
            }
        }
    }

    pub fn snapshot(&self) -> LinkStatus {
        self.status.borrow().clone()
    }
}

impl LinkError {
    pub fn internal(message: impl Into<String>) -> Self {
        Self {
            quality: Quality::Timeout,
            message: message.into(),
            tx: String::new(),
            rx: String::new(),
            rtt_ms: 0,
            attempts: 0,
        }
    }
}

struct Worker {
    link: Option<SerialLink>,
    sim: Option<crate::modbus::sim::SimDevice>,
    profile: LinkProfile,
    bus: tokio::sync::broadcast::Sender<Event>,
    frames_enabled: bool,
    status: LinkStatus,
    status_tx: watch::Sender<LinkStatus>,
    canceller: Arc<StdMutex<Option<Canceller>>>,
    consecutive_failures: u32,
}

impl Worker {
    fn run(&mut self, mut rx: mpsc::UnboundedReceiver<LinkRequest>) {
        while let Some(req) = rx.blocking_recv() {
            match req {
                LinkRequest::ReadRegisters { addr, start, count, reply } => {
                    let _ = reply.send(self.read_registers(addr, start, count));
                }
                LinkRequest::WriteRegister { addr, reg, value, reply } => {
                    let _ = reply.send(self.write_register(addr, reg, value));
                }
                LinkRequest::WriteRegisters { addr, start, values, reply } => {
                    let _ = reply.send(self.write_registers(addr, start, &values));
                }
                LinkRequest::Raw { tx, addr, expect_len, reply } => {
                    let _ = reply.send(self.raw(&tx, addr, expect_len));
                }
                LinkRequest::Scan { from, to, start, count, reply } => {
                    let mut out = Vec::new();
                    for a in from..=to {
                        let r = self.read_registers(a, start, count);
                        out.push((a, r));
                        thread::sleep(Duration::from_millis(self.profile.retry_interval_ms.min(100)));
                    }
                    let _ = reply.send(out);
                }
                LinkRequest::Reload { profile, reply } => {
                    self.profile = *profile;
                    self.status.profile = self.profile.name.clone();
                    self.status.port = self.profile.port.clone();
                    self.status.baud = self.profile.baud;
                    let r = self.reopen();
                    let _ = reply.send(r);
                }
                LinkRequest::Shutdown => {
                    tracing::info!("链路线程收到停机指令，关闭串口");
                    self.link = None;
                    self.sim = None;
                    *self.canceller.lock().unwrap() = None;
                    self.status.connected = false;
                    self.publish_status();
                    break;
                }
            }
        }
    }

    /// 关闭并重开串口；返回结果。
    fn reopen(&mut self) -> Result<(), String> {
        self.link = None;
        *self.canceller.lock().unwrap() = None;
        if self.profile.kind == crate::config::LinkKind::Simulator {
            self.sim = Some(crate::modbus::sim::SimDevice::new());
            self.link = None;
            self.status.connected = true;
            self.status.last_error = None;
            self.publish_status();
            return Ok(());
        }
        self.sim = None;
        match SerialLink::open(&self.profile) {
            Ok(l) => {
                *self.canceller.lock().unwrap() = Some(l.canceller());
                self.link = Some(l);
                self.status.connected = true;
                self.status.last_error = None;
                self.consecutive_failures = 0;
                self.publish_status();
                Ok(())
            }
            Err(e) => {
                self.status.connected = false;
                self.status.last_error = Some(e.to_string());
                self.publish_status();
                Err(e.to_string())
            }
        }
    }

    fn ensure_open(&mut self) -> bool {
        if self.profile.kind == crate::config::LinkKind::Simulator {
            if self.sim.is_none() {
                self.sim = Some(crate::modbus::sim::SimDevice::new());
            }
            self.link = None;
            *self.canceller.lock().unwrap() = None;
            self.status.connected = true;
            self.status.last_error = None;
            return true;
        }
        if self.link.is_some() {
            return true;
        }
        self.reopen().is_ok()
    }

    fn publish_status(&mut self) {
        self.status.updated_at = Local::now();
        let _ = self.status_tx.send(self.status.clone());
        let _ = self.bus.send(Event::LinkStatus(self.status.clone()));
    }

    fn emit_frame(&self, log: FrameLog) {
        if self.frames_enabled {
            let _ = self.bus.send(Event::Frame(log));
        }
    }

    fn transact(&mut self, tx: &[u8], addr: Option<u8>, expected_len: Option<usize>) -> LinkResult<Vec<u8>> {
        if !self.ensure_open() {
            return Err(LinkError {
                quality: Quality::Timeout,
                message: self.status.last_error.clone().unwrap_or_else(|| "串口未打开".into()),
                tx: hex(tx),
                rx: String::new(),
                rtt_ms: 0,
                attempts: 0,
            });
        }
        let attempts_max = self.profile.retries.saturating_add(1);
        let mut last: Option<LinkError> = None;

        for attempt in 1..=attempts_max {
            if attempt > 1 {
                self.status.retry_count += 1;
                thread::sleep(Duration::from_millis(self.profile.retry_interval_ms));
            }
            self.status.tx_count += 1;
            self.emit_frame(FrameLog {
                at: Local::now(),
                direction: "tx".into(),
                addr,
                func: Some(tx[1]),
                bytes: hex(tx),
                rtt_ms: None,
                quality: None,
                note: Some(format!("第 {attempt}/{attempts_max} 次")),
            });

            let out = if let Some(sim) = self.sim.as_mut() {
                sim.transact(tx, addr)
            } else {
                self.link.as_mut().expect("link opened").transact(tx, addr, expected_len)
            };
            self.status.rx_count += 1;
            self.status.last_rtt_ms = Some(out.rtt_ms);
            self.emit_frame(FrameLog {
                at: Local::now(),
                direction: "rx".into(),
                addr: out.rx.first().copied(),
                func: out.rx.get(1).copied(),
                bytes: hex(&out.rx),
                rtt_ms: Some(out.rtt_ms),
                quality: Some(out.quality.as_str().to_string()),
                note: out.error.clone(),
            });

            match out.quality {
                Quality::Ok => {
                    self.status.ok_count += 1;
                    self.status.last_error = None;
                    self.consecutive_failures = 0;
                    self.publish_status();
                    return Ok(out.rx);
                }
                Quality::CrcError => self.status.crc_error_count += 1,
                _ => self.status.timeout_count += 1,
            }
            self.status.last_error = out.error.clone();
            last = Some(LinkError {
                quality: out.quality,
                message: out.error.unwrap_or_else(|| "未知错误".into()),
                tx: hex(tx),
                rx: hex(&out.rx),
                rtt_ms: out.rtt_ms,
                attempts: attempt,
            });
        }

        self.consecutive_failures += 1;
        if self.consecutive_failures >= REOPEN_AFTER_FAILURES {
            tracing::warn!(
                profile = %self.profile.name,
                failures = self.consecutive_failures,
                "连续失败达到阈值，关闭并重开串口"
            );
            self.consecutive_failures = 0;
            let _ = self.reopen();
        }
        self.publish_status();
        Err(last.unwrap_or_else(|| LinkError::internal("事务失败")))
    }

    fn read_registers(&mut self, addr: u8, start: u16, count: u16) -> LinkResult<Vec<u16>> {
        let tx = build_read(addr, 0x03, start, count);
        let expected = 5 + (count as usize) * 2;
        let rx = self.transact(&tx, Some(addr), Some(expected))?;
        parse_read(&rx, addr, 0x03).map_err(|e| self.frame_error(addr, &tx, &rx, e))
    }

    fn write_register(&mut self, addr: u8, reg: u16, value: u16) -> LinkResult<()> {
        let tx = build_write_single(addr, reg, value);
        let rx = self.transact(&tx, Some(addr), Some(8))?;
        if rx.len() >= 8 && rx[0] == addr && rx[1] == 0x06 {
            Ok(())
        } else {
            Err(self.frame_error(addr, &tx, &rx, FrameError::ByteCount))
        }
    }

    fn write_registers(&mut self, addr: u8, start: u16, values: &[u16]) -> LinkResult<()> {
        let tx = build_write_multi(addr, start, values);
        let rx = self.transact(&tx, Some(addr), Some(8))?;
        if rx.len() >= 8 && rx[0] == addr && rx[1] == 0x10 {
            Ok(())
        } else {
            Err(self.frame_error(addr, &tx, &rx, FrameError::ByteCount))
        }
    }

    fn raw(&mut self, tx: &[u8], addr: Option<u8>, expect_len: Option<usize>) -> LinkResult<Vec<u8>> {
        self.transact(tx, addr, expect_len)
    }

    fn frame_error(&self, _addr: u8, tx: &[u8], rx: &[u8], err: FrameError) -> LinkError {
        let quality = match err {
            FrameError::Crc => Quality::CrcError,
            FrameError::Exception(_) => Quality::SlaveFault,
            FrameError::AddrMismatch { .. } => Quality::IllegalAddr,
            _ => Quality::CrcError,
        };
        LinkError {
            quality,
            message: err.to_string(),
            tx: hex(tx),
            rx: hex(rx),
            rtt_ms: 0,
            attempts: 1,
        }
    }
}
