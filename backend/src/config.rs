//! 配置模型与加载。

use crate::domain::{ChannelConfig, DataType};
use crate::error::{AppError, Result};
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum Parity {
    #[default]
    None,
    Even,
    Odd,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum RecvMode {
    /// 静默判界 + 收齐合法 CRC 立即返回（实测推荐）
    #[default]
    Silence,
    /// 按请求推导的预期长度收帧
    ExpectedLen,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum RtsMode {
    /// 不操作 RTS
    None,
    /// 由系统/适配器自动换向
    #[default]
    Auto,
    /// 发送前置 RTS 有效、发完翻回（实测该 MacroSilicon 适配器必需）
    Flip,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum ProtocolKind {
    #[default]
    RsModbus,
    StdModbus,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum StdReadMode {
    /// 读 0~63 原始值（16 位）
    #[default]
    Raw,
    /// 读 64+ 处理值（32 位浮点）
    Processed,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum LinkKind {
    #[default]
    Wired,
    Lora,
    /// 内置 Modbus 从站模拟器（无硬件自测）
    Simulator,
    /// TCP 串口转接器（调试用，见 tools/vserial_bridge.py）
    Tcp,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct ServerConfig {
    #[serde(default = "default_bind")]
    pub bind: String,
    #[serde(default = "yes")]
    pub ws_push_frames: bool,
}
fn default_bind() -> String { "127.0.0.1:8790".into() }
fn yes() -> bool { true }
impl Default for ServerConfig {
    fn default() -> Self { Self { bind: default_bind(), ws_push_frames: true } }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DeviceConfig {
    #[serde(default = "default_sn")]
    pub sn: String,
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub location: String,
}
fn default_sn() -> String { "JDRK-QXZ-0001".into() }
impl Default for DeviceConfig {
    fn default() -> Self {
        Self { sn: default_sn(), name: String::new(), location: String::new() }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct PollConfig {
    #[serde(default = "yes")]
    pub enabled: bool,
    #[serde(default = "def_interval")]
    pub interval_ms: u64,
    #[serde(default = "def_round_timeout")]
    pub round_timeout_ms: u64,
    #[serde(default)]
    pub per_channel_retry: u32,
    #[serde(default = "def_retention")]
    pub retention_days: u32,
}
fn def_interval() -> u64 { 10_000 }
fn def_round_timeout() -> u64 { 9_000 }
fn def_retention() -> u32 { 365 }
impl Default for PollConfig {
    fn default() -> Self {
        Self { enabled: true, interval_ms: def_interval(), round_timeout_ms: def_round_timeout(), per_channel_retry: 0, retention_days: def_retention() }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct StorageConfig {
    #[serde(default = "def_sqlite")]
    pub sqlite_path: String,
    #[serde(default = "yes")]
    pub buffer_enabled: bool,
}
fn def_sqlite() -> String { "data/monitor.db".into() }
impl Default for StorageConfig {
    fn default() -> Self {
        Self { sqlite_path: def_sqlite(), buffer_enabled: true }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct MysqlTarget {
    pub name: String,
    #[serde(default)]
    pub enabled: bool,
    pub url: String,
    #[serde(default = "def_table")]
    pub table: String,
}
fn def_table() -> String { "tb_sensor_data".into() }

#[derive(Debug, Clone, Deserialize, Serialize, Default)]
pub struct ForwardConfig {
    #[serde(default)]
    pub enabled: bool,
    #[serde(default = "def_fwd_interval")]
    pub interval_ms: u64,
    #[serde(default = "def_batch")]
    pub batch_size: u32,
    #[serde(default = "def_attempts")]
    pub max_attempts: u32,
    #[serde(default)]
    pub targets: Vec<MysqlTarget>,
}
fn def_fwd_interval() -> u64 { 5_000 }
fn def_batch() -> u32 { 200 }
fn def_attempts() -> u32 { 10 }

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct DebugConfig {
    #[serde(default = "def_frame_cap")]
    pub frame_log_capacity: usize,
    #[serde(default = "yes")]
    pub frame_log_enabled: bool,
}
fn def_frame_cap() -> usize { 2000 }
impl Default for DebugConfig {
    fn default() -> Self {
        Self { frame_log_capacity: def_frame_cap(), frame_log_enabled: true }
    }
}

/// 链路档案（FR-09 / FR-11）。
#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct LinkProfile {
    pub name: String,
    #[serde(default)]
    pub kind: LinkKind,
    pub port: String,
    #[serde(default = "def_baud")]
    pub baud: u32,
    #[serde(default = "def_databits")]
    pub data_bits: u8,
    #[serde(default)]
    pub parity: Parity,
    #[serde(default = "def_stopbits")]
    pub stop_bits: u8,
    #[serde(default = "def_resp_timeout")]
    pub response_timeout_ms: u64,
    #[serde(default = "def_frame_gap")]
    pub frame_gap_ms: u64,
    #[serde(default = "def_silence")]
    pub silence_ms: u64,
    #[serde(default)]
    pub recv_mode: RecvMode,
    #[serde(default = "def_retries")]
    pub retries: u32,
    #[serde(default = "def_retry_interval")]
    pub retry_interval_ms: u64,
    #[serde(default)]
    pub rts_mode: RtsMode,
    #[serde(default)]
    pub rts_active_low: bool,
    #[serde(default)]
    pub protocol: ProtocolKind,
    #[serde(default = "def_slave")]
    pub std_slave_addr: u8,
    #[serde(default)]
    pub std_read_mode: StdReadMode,
    #[serde(default = "def_chunk")]
    pub chunk_regs: u16,
}
fn def_baud() -> u32 { 9600 }
fn def_databits() -> u8 { 8 }
fn def_stopbits() -> u8 { 1 }
fn def_resp_timeout() -> u64 { 300 }
fn def_frame_gap() -> u64 { 10 }
fn def_silence() -> u64 { 200 }
fn def_retries() -> u32 { 3 }
fn def_retry_interval() -> u64 { 250 }
fn def_slave() -> u8 { 1 }
fn def_chunk() -> u16 { 124 }

impl Default for LinkProfile {
    fn default() -> Self {
        Self {
            name: "debug_485".into(),
            kind: LinkKind::Wired,
            port: "COM3".into(),
            baud: 9600,
            data_bits: 8,
            parity: Parity::None,
            stop_bits: 1,
            response_timeout_ms: 300,
            frame_gap_ms: 10,
            silence_ms: 200,
            recv_mode: RecvMode::Silence,
            retries: 3,
            retry_interval_ms: 250,
            rts_mode: RtsMode::Auto,
            rts_active_low: false,
            protocol: ProtocolKind::RsModbus,
            std_slave_addr: 1,
            std_read_mode: StdReadMode::Raw,
            chunk_regs: 124,
        }
    }
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AppConfig {
    #[serde(default)]
    pub server: ServerConfig,
    #[serde(default)]
    pub device: DeviceConfig,
    #[serde(default)]
    pub poll: PollConfig,
    #[serde(default)]
    pub storage: StorageConfig,
    #[serde(default)]
    pub forward: ForwardConfig,
    #[serde(default)]
    pub debug: DebugConfig,
    #[serde(default)]
    pub links: Vec<LinkProfile>,
    #[serde(default)]
    pub active_link: Option<String>,
    #[serde(default)]
    pub channels: Vec<ChannelConfig>,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            server: ServerConfig::default(),
            device: DeviceConfig::default(),
            poll: PollConfig::default(),
            storage: StorageConfig::default(),
            forward: ForwardConfig::default(),
            debug: DebugConfig::default(),
            links: vec![LinkProfile::default()],
            active_link: Some("debug_485".into()),
            channels: ChannelConfig::default_set(20),
        }
    }
}

impl AppConfig {
    pub fn load(path: impl AsRef<Path>) -> Result<Self> {
        let text = std::fs::read_to_string(path.as_ref())?;
        let mut cfg: AppConfig = toml::from_str(&text)?;
        cfg.normalize();
        Ok(cfg)
    }

    pub fn from_str(text: &str) -> Result<Self> {
        let mut cfg: AppConfig = toml::from_str(text)?;
        cfg.normalize();
        Ok(cfg)
    }

    /// 补齐缺省项：至少 1 条链路、20 路通道、active_link 合法、slave_addr 默认 = no。
    pub fn normalize(&mut self) {
        if self.links.is_empty() {
            self.links.push(LinkProfile::default());
        }
        if self.active_link.is_none() {
            self.active_link = Some(self.links[0].name.clone());
        }
        if self.channels.is_empty() {
            self.channels = ChannelConfig::default_set(20);
        }
        for ch in &mut self.channels {
            if ch.name.is_empty() {
                ch.name = format!("通道{}", ch.no);
            }
            if ch.slave_addr == 0 {
                ch.slave_addr = ch.no as u8;
            }
            if ch.data_type == DataType::Ai1Ai2 {
                // 保持用户选择
            }
        }
        self.channels.sort_by_key(|c| c.no);
    }

    pub fn active_profile(&self) -> Result<&LinkProfile> {
        let name = self.active_link.as_deref().unwrap_or_default();
        self.links
            .iter()
            .find(|l| l.name == name)
            .or_else(|| self.links.first())
            .ok_or_else(|| AppError::Config("没有可用的链路档案".into()))
    }

    pub fn profile(&self, name: &str) -> Option<&LinkProfile> {
        self.links.iter().find(|l| l.name == name)
    }
}
