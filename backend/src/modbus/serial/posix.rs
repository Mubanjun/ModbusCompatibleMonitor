//! Unix 串口实现（基于 serialport crate，POSIX VTIME 有界读）。

use crate::config::LinkProfile;
use crate::error::{AppError, Result};
use serialport::{ClearBuffer, DataBits, FlowControl, Parity as SerParity, SerialPort, StopBits};
use std::io::{Read, Write};
use std::time::Duration;

/// Unix 下无法跨线程取消同步 read，取消器为空操作（VTIME 保证读有界返回）。
#[derive(Clone)]
pub struct Canceller;

impl Canceller {
    pub fn cancel(&self) {}
}

pub struct Port {
    inner: Box<dyn SerialPort>,
    name: String,
}

// Port 只在链路线程内使用。
unsafe impl Send for Port {}

impl Port {
    pub fn open(profile: &LinkProfile) -> Result<Self> {
        let port = serialport::new(&profile.port, profile.baud)
            .data_bits(match profile.data_bits {
                5 => DataBits::Five,
                6 => DataBits::Six,
                7 => DataBits::Seven,
                _ => DataBits::Eight,
            })
            .parity(match profile.parity {
                crate::config::Parity::Even => SerParity::Even,
                crate::config::Parity::Odd => SerParity::Odd,
                crate::config::Parity::None => SerParity::None,
            })
            .stop_bits(match profile.stop_bits {
                2 => StopBits::Two,
                _ => StopBits::One,
            })
            .flow_control(FlowControl::None)
            .timeout(Duration::from_millis(profile.response_timeout_ms.clamp(20, 60_000)))
            .open()
            .map_err(|e| AppError::Modbus(format!("打开串口 {} 失败: {e}", profile.port)))?;
        Ok(Self { inner: port, name: profile.port.clone() })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn canceller(&self) -> Canceller {
        Canceller
    }

    pub fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> std::io::Result<usize> {
        let _ = self.inner.set_timeout(timeout.max(Duration::from_millis(1)));
        match self.inner.read(buf) {
            Ok(n) => Ok(n),
            Err(e) if e.kind() == std::io::ErrorKind::TimedOut => Ok(0),
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => Ok(0),
            Err(e) => Err(e),
        }
    }

    pub fn write_all_timeout(&mut self, data: &[u8], _timeout: Duration) -> std::io::Result<()> {
        self.inner.write_all(data)?;
        self.inner.flush()
    }

    pub fn set_rts(&mut self, level: bool) -> std::io::Result<()> {
        self.inner.write_request_to_send(level).map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))
    }

    pub fn clear_input(&mut self) -> std::io::Result<()> {
        self.inner.clear(ClearBuffer::Input).map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))
    }
}
