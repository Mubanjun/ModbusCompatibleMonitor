//! Modbus RTU 协议层：CRC、帧构造/解析、串口传输、链路工作线程。

pub mod crc;
pub mod frame;
pub mod link;
pub mod serial;
pub mod sim;
pub mod transport;
