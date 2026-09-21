//! Modbus RTU 帧构造与解析。

use super::crc::{append_crc, crc_ok};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FrameError {
    Short,
    AddrMismatch { want: u8, got: u8 },
    FuncMismatch { want: u8, got: u8 },
    Crc,
    ByteCount,
    Exception(u8),
}

impl std::fmt::Display for FrameError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FrameError::Short => write!(f, "帧太短"),
            FrameError::AddrMismatch { want, got } => write!(f, "从站地址不匹配: 期望 {want} 实际 {got}"),
            FrameError::FuncMismatch { want, got } => write!(f, "功能码不匹配: 期望 {want} 实际 {got}"),
            FrameError::Crc => write!(f, "CRC 校验失败"),
            FrameError::ByteCount => write!(f, "字节数非法"),
            FrameError::Exception(c) => write!(f, "从站异常应答, 异常码 0x{c:02X}"),
        }
    }
}

pub fn build_read(addr: u8, func: u8, start: u16, count: u16) -> Vec<u8> {
    let mut v = vec![
        addr,
        func,
        (start >> 8) as u8,
        (start & 0xFF) as u8,
        (count >> 8) as u8,
        (count & 0xFF) as u8,
    ];
    append_crc(&mut v);
    v
}

pub fn build_write_single(addr: u8, reg: u16, value: u16) -> Vec<u8> {
    let mut v = vec![
        addr,
        0x06,
        (reg >> 8) as u8,
        (reg & 0xFF) as u8,
        (value >> 8) as u8,
        (value & 0xFF) as u8,
    ];
    append_crc(&mut v);
    v
}

pub fn build_write_multi(addr: u8, start: u16, values: &[u16]) -> Vec<u8> {
    let mut v = vec![
        addr,
        0x10,
        (start >> 8) as u8,
        (start & 0xFF) as u8,
        (values.len() >> 8) as u8,
        (values.len() & 0xFF) as u8,
        (values.len() * 2) as u8,
    ];
    for value in values {
        v.push((value >> 8) as u8);
        v.push((value & 0xFF) as u8);
    }
    append_crc(&mut v);
    v
}

/// 解析读寄存器应答（FC03/04），返回寄存器数组（大端）。
pub fn parse_read(frame: &[u8], addr: u8, func: u8) -> Result<Vec<u16>, FrameError> {
    if frame.len() < 5 {
        return Err(FrameError::Short);
    }
    if frame[0] != addr {
        return Err(FrameError::AddrMismatch { want: addr, got: frame[0] });
    }
    if frame[1] & 0x80 != 0 {
        return Err(FrameError::Exception(frame[2]));
    }
    if frame[1] != func {
        return Err(FrameError::FuncMismatch { want: func, got: frame[1] });
    }
    if !crc_ok(frame) {
        return Err(FrameError::Crc);
    }
    let bc = frame[2] as usize;
    if bc % 2 != 0 || frame.len() < 3 + bc + 2 {
        return Err(FrameError::ByteCount);
    }
    let mut out = Vec::with_capacity(bc / 2);
    for i in 0..bc / 2 {
        out.push(u16::from_be_bytes([frame[3 + 2 * i], frame[4 + 2 * i]]));
    }
    Ok(out)
}

/// 在接收缓冲区中寻找第一个以 addr 开头且 CRC 合法的完整帧。
/// 返回 (offset, length)；用于容忍半双工换向造成的帧首字节丢失。
pub fn find_valid_frame(buf: &[u8], addr: u8) -> Option<(usize, usize)> {
    if buf.len() < 5 {
        return None;
    }
    for off in 0..=buf.len().saturating_sub(5) {
        if buf[off] != addr {
            continue;
        }
        let func = buf[off + 1];
        if func & 0x80 != 0 {
            if off + 5 <= buf.len() && crc_ok(&buf[off..off + 5]) {
                return Some((off, 5));
            }
            continue;
        }
        if func == 0x03 || func == 0x04 {
            if off + 3 > buf.len() {
                continue;
            }
            let bc = buf[off + 2] as usize;
            if bc % 2 != 0 {
                continue;
            }
            let total = 3 + bc + 2;
            if total <= 4 || off + total > buf.len() {
                continue;
            }
            if crc_ok(&buf[off..off + total]) {
                return Some((off, total));
            }
            continue;
        }
        if matches!(func, 0x01 | 0x02 | 0x05 | 0x06 | 0x0F | 0x10) {
            if off + 8 <= buf.len() && crc_ok(&buf[off..off + 8]) {
                return Some((off, 8));
            }
            continue;
        }
        for total in 5..=(buf.len() - off).min(260) {
            if crc_ok(&buf[off..off + total]) {
                return Some((off, total));
            }
        }
    }
    None
}

pub fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02X}")).collect::<Vec<_>>().join(" ")
}

pub fn parse_hex(text: &str) -> Result<Vec<u8>, String> {
    let cleaned: String = text
        .chars()
        .filter(|c| !c.is_whitespace() && *c != ',' && *c != '-')
        .collect();
    if cleaned.len() % 2 != 0 {
        return Err("十六进制串长度必须为偶数".into());
    }
    (0..cleaned.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(&cleaned[i..i + 2], 16).map_err(|e| e.to_string()))
        .collect()
}
