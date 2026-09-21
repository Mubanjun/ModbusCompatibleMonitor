//! 领域模型：通道档案、采样值、质量位。

use chrono::{DateTime, Local};
use serde::{Deserialize, Serialize};

/// 数据质量位。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Quality {
    Ok,
    Timeout,
    CrcError,
    IllegalAddr,
    SlaveFault,
    Scaling,
    Disabled,
    NoData,
}

impl Quality {
    pub fn as_str(self) -> &'static str {
        match self {
            Quality::Ok => "ok",
            Quality::Timeout => "timeout",
            Quality::CrcError => "crc_error",
            Quality::IllegalAddr => "illegal_addr",
            Quality::SlaveFault => "slave_fault",
            Quality::Scaling => "scaling",
            Quality::Disabled => "disabled",
            Quality::NoData => "no_data",
        }
    }
}

/// 通道数据类型（决定两路模拟量的组合与解析方式）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum DataType {
    /// 只取模拟量 1（16 位有符号）
    #[default]
    Ai1,
    /// 只取模拟量 2（16 位无符号）
    Ai2,
    /// 模拟量 1 为主值
    Ai1Ai2,
    /// 模拟量 1 高 16 位 + 模拟量 2 低 16 位 → uint32
    U32,
    /// 同上 → int32
    I32,
    /// 同上 → float32（大端字序）
    F32,
    /// 开关量
    Switch,
}

/// 通道档案（FR-07）。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChannelConfig {
    pub no: u16,
    pub name: String,
    #[serde(default)]
    pub unit: String,
    #[serde(default)]
    pub sensor_model: String,
    #[serde(default)]
    pub data_type: DataType,
    #[serde(default = "one")]
    pub coef_a: f64,
    #[serde(default)]
    pub coef_b: f64,
    #[serde(default = "two")]
    pub decimals: u8,
    #[serde(default)]
    pub upper_limit: Option<f64>,
    #[serde(default)]
    pub lower_limit: Option<f64>,
    #[serde(default = "yes")]
    pub enabled: bool,
    /// RS-Modbus 模式下该通道对应的从站地址（默认 = 通道号）
    #[serde(default)]
    pub slave_addr: u8,
}

fn one() -> f64 { 1.0 }
fn two() -> u8 { 2 }
fn yes() -> bool { true }

impl Default for ChannelConfig {
    fn default() -> Self {
        Self {
            no: 1,
            name: String::new(),
            unit: String::new(),
            sensor_model: String::new(),
            data_type: DataType::Ai1,
            coef_a: 1.0,
            coef_b: 0.0,
            decimals: 2,
            upper_limit: None,
            lower_limit: None,
            enabled: true,
            slave_addr: 1,
        }
    }
}

impl ChannelConfig {
    /// 生成 20 路默认占位通道。
    pub fn default_set(count: u16) -> Vec<ChannelConfig> {
        (1..=count)
            .map(|n| ChannelConfig {
                no: n,
                name: format!("通道{n}"),
                slave_addr: n as u8,
                ..Default::default()
            })
            .collect()
    }

    /// 工程量换算：value = A * raw + B
    pub fn scale(&self, raw: f64) -> f64 {
        self.coef_a * raw + self.coef_b
    }
}

/// 一次采集得到的通道读数。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Sample {
    pub record_time: DateTime<Local>,
    pub device_sn: String,
    pub channel_no: u16,
    pub channel_name: String,
    pub unit: String,
    pub value: Option<f64>,
    pub raw_value: Option<i64>,
    pub quality: Quality,
    pub rtt_ms: Option<u32>,
}

impl Sample {
    pub fn new(cfg: &ChannelConfig, device_sn: &str, record_time: DateTime<Local>, quality: Quality) -> Self {
        Self {
            record_time,
            device_sn: device_sn.to_string(),
            channel_no: cfg.no,
            channel_name: cfg.name.clone(),
            unit: cfg.unit.clone(),
            value: None,
            raw_value: None,
            quality,
            rtt_ms: None,
        }
    }
}

/// 告警记录。
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AlarmRecord {
    pub id: i64,
    pub device_sn: String,
    pub channel_no: u16,
    pub channel_name: String,
    /// high / low
    pub alarm_type: String,
    pub value: Option<f64>,
    pub threshold: Option<f64>,
    pub raised_at: DateTime<Local>,
    pub cleared_at: Option<DateTime<Local>>,
}
