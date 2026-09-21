//! 跨平台串口 / 串口转接抽象。
//!
//! - Windows：自实现 **重叠 I/O**（FILE_FLAG_OVERLAPPED），读前先查输入队列，
//!   不产生挂起的内核 IRP；并可被 CancelIoEx 取消。
//! - Unix：使用 serialport crate（POSIX VTIME 有界读）。
//! - TCP：连接到 tools/vserial_bridge.py 之类的转接器，把后端与物理串口解耦（调试用）。

use crate::config::{LinkKind, LinkProfile, Parity};
use crate::error::Result;
use std::io;
use std::time::Duration;

#[cfg(windows)]
mod win;
#[cfg(not(windows))]
mod posix;
pub mod tcp;

/// 统一端口类型：按链路类型分派。
pub enum Port {
    #[cfg(windows)]
    Win(win::Port),
    #[cfg(not(windows))]
    Posix(posix::Port),
    Tcp(tcp::TcpPort),
}

/// 统一取消器：可从任意线程打断挂起的 I/O。
#[derive(Clone)]
pub enum Canceller {
    #[cfg(windows)]
    Win(win::Canceller),
    #[cfg(not(windows))]
    Posix(posix::Canceller),
    Tcp(tcp::TcpCanceller),
}

impl Canceller {
    pub fn cancel(&self) {
        match self {
            #[cfg(windows)]
            Canceller::Win(c) => c.cancel(),
            #[cfg(not(windows))]
            Canceller::Posix(c) => c.cancel(),
            Canceller::Tcp(c) => c.cancel(),
        }
    }
}

impl Port {
    pub fn open(profile: &LinkProfile) -> Result<Self> {
        if profile.kind == LinkKind::Tcp {
            return Ok(Port::Tcp(tcp::TcpPort::connect(&profile.port)?));
        }
        #[cfg(windows)]
        {
            Ok(Port::Win(win::Port::open(profile)?))
        }
        #[cfg(not(windows))]
        {
            Ok(Port::Posix(posix::Port::open(profile)?))
        }
    }

    pub fn name(&self) -> &str {
        match self {
            #[cfg(windows)]
            Port::Win(p) => p.name(),
            #[cfg(not(windows))]
            Port::Posix(p) => p.name(),
            Port::Tcp(p) => p.name(),
        }
    }

    pub fn canceller(&self) -> Canceller {
        match self {
            #[cfg(windows)]
            Port::Win(p) => Canceller::Win(p.canceller()),
            #[cfg(not(windows))]
            Port::Posix(p) => Canceller::Posix(p.canceller()),
            Port::Tcp(p) => Canceller::Tcp(p.canceller()),
        }
    }

    pub fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> io::Result<usize> {
        match self {
            #[cfg(windows)]
            Port::Win(p) => p.read_timeout(buf, timeout),
            #[cfg(not(windows))]
            Port::Posix(p) => p.read_timeout(buf, timeout),
            Port::Tcp(p) => p.read_timeout(buf, timeout),
        }
    }

    pub fn write_all_timeout(&mut self, data: &[u8], timeout: Duration) -> io::Result<()> {
        match self {
            #[cfg(windows)]
            Port::Win(p) => p.write_all_timeout(data, timeout),
            #[cfg(not(windows))]
            Port::Posix(p) => p.write_all_timeout(data, timeout),
            Port::Tcp(p) => p.write_all_timeout(data, timeout),
        }
    }

    pub fn set_rts(&mut self, level: bool) -> io::Result<()> {
        match self {
            #[cfg(windows)]
            Port::Win(p) => p.set_rts(level),
            #[cfg(not(windows))]
            Port::Posix(p) => p.set_rts(level),
            Port::Tcp(p) => p.set_rts(level),
        }
    }

    pub fn clear_input(&mut self) -> io::Result<()> {
        match self {
            #[cfg(windows)]
            Port::Win(p) => p.clear_input(),
            #[cfg(not(windows))]
            Port::Posix(p) => p.clear_input(),
            Port::Tcp(p) => p.clear_input(),
        }
    }
}

/// 构造串口设备名（Windows 需要 \\.\ 前缀）。
pub(crate) fn device_path(port: &str) -> String {
    if port.starts_with('\\') {
        port.to_string()
    } else {
        format!("\\.\{}", port)
    }
}

pub(crate) fn parity_code(p: Parity) -> u8 {
    match p {
        Parity::None => 0,
        Parity::Odd => 1,
        Parity::Even => 2,
    }
}
