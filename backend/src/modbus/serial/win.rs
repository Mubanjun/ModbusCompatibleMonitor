//! Windows 重叠 I/O 串口实现（可取消，读有硬超时）。

use super::{device_path, parity_code};
use crate::config::LinkProfile;
use crate::error::{AppError, Result};
use std::io;
use std::sync::atomic::{AtomicIsize, Ordering};
use std::sync::{Arc, Once};
use std::time::Duration;
use winapi::shared::minwindef::{DWORD, FALSE};
use winapi::shared::winerror::ERROR_IO_PENDING;
use winapi::um::commapi::{
    ClearCommError, EscapeCommFunction, GetCommState, PurgeComm, SetCommState, SetCommTimeouts,
    SetupComm,
};
use winapi::um::errhandlingapi::GetLastError;
use winapi::um::fileapi::{CreateFileW, ReadFile, WriteFile, OPEN_EXISTING};
use winapi::um::handleapi::{CloseHandle, INVALID_HANDLE_VALUE};
use winapi::um::ioapiset::{CancelIoEx, GetOverlappedResult};
use winapi::um::minwinbase::OVERLAPPED;
use winapi::um::synchapi::{CreateEventW, ResetEvent, WaitForSingleObject};
use winapi::um::winbase::{
    COMSTAT, COMMTIMEOUTS, DCB, FILE_FLAG_OVERLAPPED, PURGE_RXCLEAR, PURGE_TXCLEAR, WAIT_OBJECT_0,
};
use winapi::um::winnt::{GENERIC_READ, GENERIC_WRITE, HANDLE};

const WAIT_TIMEOUT_VAL: DWORD = 0x0000_0102;
const SETRTS: DWORD = 3;
const CLRRTS: DWORD = 4;

/// 当前打开的串口句柄（用于控制台控制事件时抢先关闭端口）
static ACTIVE_HANDLE: AtomicIsize = AtomicIsize::new(0);
static CTRL_HANDLER_INSTALLED: Once = Once::new();

/// 处理 CTRL_C / CTRL_CLOSE / 注销 / 关机：先取消并关闭串口，保证端口释放。
unsafe extern "system" fn console_ctrl_handler(ctrl_type: DWORD) -> i32 {
    const CTRL_C_EVENT: DWORD = 0;
    const CTRL_BREAK_EVENT: DWORD = 1;
    const CTRL_CLOSE_EVENT: DWORD = 2;
    const CTRL_LOGOFF_EVENT: DWORD = 5;
    const CTRL_SHUTDOWN_EVENT: DWORD = 6;
    match ctrl_type {
        CTRL_C_EVENT | CTRL_BREAK_EVENT | CTRL_CLOSE_EVENT | CTRL_LOGOFF_EVENT
        | CTRL_SHUTDOWN_EVENT => {
            let h = ACTIVE_HANDLE.load(Ordering::SeqCst);
            if h != 0 {
                let handle = h as HANDLE;
                CancelIoEx(handle, std::ptr::null_mut());
                std::thread::sleep(Duration::from_millis(80));
                CloseHandle(handle);
                ACTIVE_HANDLE.store(0, Ordering::SeqCst);
                tracing::info!("控制台退出事件：已关闭串口");
            }
            1
        }
        _ => 0,
    }
}

/// 只持有句柄，用于跨线程取消与最终关闭。
struct HandleHolder(HANDLE);
unsafe impl Send for HandleHolder {}
unsafe impl Sync for HandleHolder {}

impl Drop for HandleHolder {
    fn drop(&mut self) {
        if ACTIVE_HANDLE.load(Ordering::SeqCst) == self.0 as isize {
            ACTIVE_HANDLE.store(0, Ordering::SeqCst);
        }
        unsafe {
            CancelIoEx(self.0, std::ptr::null_mut());
            CloseHandle(self.0);
        }
    }
}

/// 可克隆的取消器：其它线程调用 cancel() 即可中断本句柄上挂起的读写。
#[derive(Clone)]
pub struct Canceller {
    h: Arc<HandleHolder>,
}

impl Canceller {
    pub fn cancel(&self) {
        unsafe {
            CancelIoEx(self.h.0, std::ptr::null_mut());
        }
    }
}

pub struct Port {
    handle: Arc<HandleHolder>,
    event: HANDLE,
    name: String,
}

// Port 只在链路线程内使用；Canceller 负责跨线程取消。句柄的所有权由 Arc 管理。
unsafe impl Send for Port {}

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

fn last_err(what: &str) -> io::Error {
    let e = io::Error::last_os_error();
    io::Error::new(e.kind(), format!("{what}: {e}"))
}

impl Port {
    pub fn open(profile: &LinkProfile) -> Result<Self> {
        let path = device_path(&profile.port);
        let name = wide(&path);
        let handle = unsafe {
            CreateFileW(
                name.as_ptr(),
                GENERIC_READ | GENERIC_WRITE,
                0,
                std::ptr::null_mut(),
                OPEN_EXISTING,
                FILE_FLAG_OVERLAPPED,
                std::ptr::null_mut(),
            )
        };
        if handle == INVALID_HANDLE_VALUE {
            return Err(AppError::Modbus(format!(
                "打开串口 {} 失败: {}",
                profile.port,
                io::Error::last_os_error()
            )));
        }
        let holder = Arc::new(HandleHolder(handle));
        unsafe {
            SetupComm(handle, 8192, 8192);

            let mut dcb: DCB = std::mem::zeroed();
            dcb.DCBlength = std::mem::size_of::<DCB>() as DWORD;
            if GetCommState(handle, &mut dcb) == 0 {
                return Err(AppError::Modbus(format!(
                    "GetCommState 失败: {}",
                    io::Error::last_os_error()
                )));
            }
            dcb.BaudRate = profile.baud;
            dcb.ByteSize = profile.data_bits;
            dcb.Parity = parity_code(profile.parity);
            dcb.StopBits = if profile.stop_bits == 2 { 2 } else { 0 };
            dcb.set_fBinary(1);
            dcb.set_fDtrControl(1); // DTR_CONTROL_ENABLE
            dcb.set_fRtsControl(1); // RTS_CONTROL_ENABLE（可再用 EscapeCommFunction 手动翻转）
            if SetCommState(handle, &mut dcb) == 0 {
                return Err(AppError::Modbus(format!(
                    "SetCommState 失败: {}",
                    io::Error::last_os_error()
                )));
            }

            // 关键：采用与 pyserial 相同的超时配方
            //   ReadIntervalTimeout        = MAXDWORD -> 一有字节就返回
            //   ReadTotalTimeoutMultiplier = 0        -> 总超时 = Constant（有界！）
            //   ReadTotalTimeoutConstant   = 20       -> 无数据时最多挂 20ms
            // 绝不能把 Multiplier 设为 MAXDWORD，否则总超时 = MAXDWORD*字节数 ≈ 无限，
            // 会留下永不完成的内核 IRP，导致进程无法终止、串口无法释放。
            let mut timeouts = COMMTIMEOUTS {
                ReadIntervalTimeout: u32::MAX,
                ReadTotalTimeoutMultiplier: 0,
                ReadTotalTimeoutConstant: 20,
                WriteTotalTimeoutMultiplier: 0,
                WriteTotalTimeoutConstant: 2000,
            };
            if SetCommTimeouts(handle, &mut timeouts) == 0 {
                return Err(AppError::Modbus(format!(
                    "SetCommTimeouts 失败: {}",
                    io::Error::last_os_error()
                )));
            }

            let event = CreateEventW(std::ptr::null_mut(), 1, 0, std::ptr::null());
            if event.is_null() {
                return Err(AppError::Modbus(format!(
                    "CreateEvent 失败: {}",
                    io::Error::last_os_error()
                )));
            }
            // 注册控制台控制处理器：关窗/注销/关机时也能先释放串口
            CTRL_HANDLER_INSTALLED.call_once(|| unsafe {
                winapi::um::consoleapi::SetConsoleCtrlHandler(Some(console_ctrl_handler), 1);
            });
            ACTIVE_HANDLE.store(handle as isize, Ordering::SeqCst);
            Ok(Self { handle: holder, event, name: path })
        }
    }

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn canceller(&self) -> Canceller {
        Canceller { h: self.handle.clone() }
    }

    fn overlapped(&self) -> OVERLAPPED {
        let mut o: OVERLAPPED = unsafe { std::mem::zeroed() };
        o.hEvent = self.event;
        o
    }

    /// 有界读：最多等 timeout；Ok(0) 表示超时无数据。
    ///
    /// 关键：先用 ClearCommError 查询输入队列，**只有确实有数据时才发 ReadFile**。
    /// 这样 ReadFile 立即完成，不会在驱动里留下挂起的内核 IRP；
    /// 即使进程被强杀，串口也能立即释放（这是"端口卡死"的根治点）。
    pub fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> io::Result<usize> {
        let deadline = std::time::Instant::now() + timeout;
        let avail: usize = loop {
            let mut errs: DWORD = 0;
            let mut stat: COMSTAT = unsafe { std::mem::zeroed() };
            if unsafe { ClearCommError(self.handle.0, &mut errs, &mut stat) } == 0 {
                return Err(last_err("ClearCommError"));
            }
            if stat.cbInQue > 0 {
                break stat.cbInQue as usize;
            }
            if std::time::Instant::now() >= deadline {
                return Ok(0);
            }
            std::thread::sleep(Duration::from_millis(2));
        };

        let n_want = buf.len().min(avail).max(1);
        let mut ovl = self.overlapped();
        unsafe { ResetEvent(self.event) };
        let mut n: DWORD = 0;
        let r = unsafe {
            ReadFile(
                self.handle.0,
                buf.as_mut_ptr() as *mut _,
                n_want as DWORD,
                &mut n,
                &mut ovl,
            )
        };
        if r != 0 {
            return Ok(n as usize);
        }
        let err = unsafe { GetLastError() };
        if err != ERROR_IO_PENDING {
            return Err(last_err("ReadFile"));
        }
        // 有数据时几乎必然立即完成；仍保留短等待 + 取消兜底
        let w = unsafe { WaitForSingleObject(self.event, 50) };
        if w == WAIT_OBJECT_0 {
            let mut got: DWORD = 0;
            if unsafe { GetOverlappedResult(self.handle.0, &mut ovl, &mut got, FALSE) } != 0 {
                Ok(got as usize)
            } else {
                Err(last_err("GetOverlappedResult(read)"))
            }
        } else {
            let c = unsafe { CancelIoEx(self.handle.0, &mut ovl) };
            let mut got: DWORD = 0;
            let g = unsafe { GetOverlappedResult(self.handle.0, &mut ovl, &mut got, 1) };
            tracing::debug!(cancel = c, completed = g, got, "read pending -> cancelled");
            Ok(0)
        }
    }

    pub fn write_all_timeout(&mut self, data: &[u8], timeout: Duration) -> io::Result<()> {
        let mut ovl = self.overlapped();
        unsafe { ResetEvent(self.event) };
        let mut n: DWORD = 0;
        let r = unsafe {
            WriteFile(
                self.handle.0,
                data.as_ptr() as *mut _,
                data.len() as DWORD,
                &mut n,
                &mut ovl,
            )
        };
        if r != 0 {
            return Ok(());
        }
        let err = unsafe { GetLastError() };
        if err != ERROR_IO_PENDING {
            return Err(last_err("WriteFile"));
        }
        let ms = timeout.as_millis().min(60_000) as u32;
        let w = unsafe { WaitForSingleObject(self.event, ms) };
        if w == WAIT_OBJECT_0 {
            Ok(())
        } else if w == WAIT_TIMEOUT_VAL {
            unsafe { CancelIoEx(self.handle.0, &mut ovl) };
            let mut got: DWORD = 0;
            unsafe { GetOverlappedResult(self.handle.0, &mut ovl, &mut got, 1) };
            Err(io::Error::new(io::ErrorKind::TimedOut, "串口写超时"))
        } else {
            Err(last_err("WaitForSingleObject(write)"))
        }
    }

    pub fn set_rts(&mut self, level: bool) -> io::Result<()> {
        let func = if level { SETRTS } else { CLRRTS };
        let r = unsafe { EscapeCommFunction(self.handle.0, func) };
        if r == 0 {
            Err(last_err("EscapeCommFunction"))
        } else {
            Ok(())
        }
    }

    pub fn clear_input(&mut self) -> io::Result<()> {
        let r = unsafe { PurgeComm(self.handle.0, PURGE_RXCLEAR | PURGE_TXCLEAR) };
        if r == 0 {
            Err(last_err("PurgeComm"))
        } else {
            Ok(())
        }
    }
}

impl Drop for Port {
    fn drop(&mut self) {
        unsafe {
            // 先取消该句柄上所有未完成 I/O，再关闭事件/句柄，确保端口立即释放
            CancelIoEx(self.handle.0, std::ptr::null_mut());
            CloseHandle(self.event);
        }
        tracing::debug!(port = %self.name, "serial port closed");
    }
}
