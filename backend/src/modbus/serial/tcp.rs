//! TCP 传输：把「虚拟串口 / 串口中转器」当成串口用。
//!
//! 调试期用它把后端与物理串口解耦：
//!   [物理 COM3] <-> tools/vserial_bridge.py <-> TCP 127.0.0.1:8907 <-> 后端
//! 后端只持有 TCP 连接，即使被强杀也不会锁住物理串口；转接器侧还可做日志与故障注入。

use crate::error::{AppError, Result};
use std::io::{ErrorKind, Read, Write};
use std::net::{Shutdown, TcpStream};
use std::sync::Arc;
use std::time::Duration;

struct Holder(TcpStream);

impl Holder {
    fn shutdown(&self) {
        let _ = self.0.shutdown(Shutdown::Both);
    }
}

/// 取消器：从其它线程 shutdown 套接字即可打断挂起的读写。
#[derive(Clone)]
pub struct TcpCanceller {
    h: Arc<Holder>,
}

impl TcpCanceller {
    pub fn cancel(&self) {
        self.h.shutdown();
    }
}

pub struct TcpPort {
    stream: TcpStream,
    canceller: TcpCanceller,
    name: String,
}

impl TcpPort {
    pub fn connect(addr: &str) -> Result<Self> {
        let stream = TcpStream::connect(addr)
            .map_err(|e| AppError::Modbus(format!("连接串口转接器 {addr} 失败: {e}")))?;
        let _ = stream.set_nodelay(true);
        let clone = stream
            .try_clone()
            .map_err(|e| AppError::Modbus(format!("复制 TCP 句柄失败: {e}")))?;
        Ok(Self {
            stream,
            canceller: TcpCanceller { h: Arc::new(Holder(clone)) },
            name: format!("tcp://{addr}"),
        })
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn canceller(&self) -> TcpCanceller {
        self.canceller.clone()
    }

    pub fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> std::io::Result<usize> {
        self.stream
            .set_read_timeout(Some(timeout.max(Duration::from_millis(1))))?;
        match self.stream.read(buf) {
            Ok(n) => Ok(n),
            Err(e) if e.kind() == ErrorKind::TimedOut || e.kind() == ErrorKind::WouldBlock => Ok(0),
            Err(e) => Err(e),
        }
    }

    pub fn write_all_timeout(&mut self, data: &[u8], timeout: Duration) -> std::io::Result<()> {
        self.stream
            .set_write_timeout(Some(timeout.max(Duration::from_millis(1))))?;
        self.stream.write_all(data)?;
        self.stream.flush()
    }

    /// TCP 无 RTS 概念（由转接器/物理适配器决定）
    pub fn set_rts(&mut self, _level: bool) -> std::io::Result<()> {
        Ok(())
    }

    /// 尽力排空内核接收缓冲
    pub fn clear_input(&mut self) -> std::io::Result<()> {
        self.stream.set_nonblocking(true)?;
        let mut tmp = [0u8; 1024];
        loop {
            match self.stream.read(&mut tmp) {
                Ok(0) => break,
                Ok(_) => continue,
                Err(_) => break,
            }
        }
        self.stream.set_nonblocking(false)
    }
}
