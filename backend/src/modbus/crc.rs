//! CRC-16/Modbus（多项式 0xA001 反射，低字节先发）。

pub fn crc16(data: &[u8]) -> u16 {
    let mut crc: u16 = 0xFFFF;
    for &b in data {
        crc ^= b as u16;
        for _ in 0..8 {
            if crc & 1 != 0 {
                crc = (crc >> 1) ^ 0xA001;
            } else {
                crc >>= 1;
            }
        }
    }
    crc
}

/// 在报文尾部追加 CRC（低字节在前）。
pub fn append_crc(body: &mut Vec<u8>) {
    let c = crc16(body);
    body.push((c & 0xFF) as u8);
    body.push((c >> 8) as u8);
}

/// 校验整帧（最后两字节为 CRC）。
pub fn crc_ok(frame: &[u8]) -> bool {
    if frame.len() < 4 {
        return false;
    }
    let n = frame.len();
    let expect = u16::from_le_bytes([frame[n - 2], frame[n - 1]]);
    crc16(&frame[..n - 2]) == expect
}

/// CRC 计算自检向量（取自厂家文档样例，已独立复算）。
pub fn self_test() -> bool {
    let samples: &[(&[u8], u16)] = &[
        (&[0x02, 0x03, 0x00, 0x00, 0x00, 0x02], 0x38C4),
        (&[0x0C, 0x03, 0x00, 0x00, 0x00, 0x02], 0x16C5),
        (&[0x02, 0x03, 0x04, 0x02, 0x92, 0xFF, 0x9B], 0x3D69),
        (&[0x0C, 0x03, 0x04, 0x02, 0x92, 0xFF, 0x9B], 0xFD86),
        (&[0x05, 0x03, 0x00, 0x02, 0x00, 0x04], 0x4DE4),
        (&[0x05, 0x03, 0x08, 0x00, 0xED, 0x02, 0x7B, 0x00, 0xE0, 0x00, 0xF9], 0xB599),
    ];
    samples.iter().all(|(data, want)| crc16(data) == *want)
}
