//! 统一错误类型。

use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("配置错误: {0}")]
    Config(String),
    #[error("IO 错误: {0}")]
    Io(#[from] std::io::Error),
    #[error("串口错误: {0}")]
    Serial(#[from] serialport::Error),
    #[error("Modbus 错误: {0}")]
    Modbus(String),
    #[error("本地库错误: {0}")]
    Store(#[from] rusqlite::Error),
    #[error("MySQL 错误: {0}")]
    Mysql(#[from] mysql::Error),
    #[error("JSON 错误: {0}")]
    Json(#[from] serde_json::Error),
    #[error("TOML 解析错误: {0}")]
    Toml(#[from] toml::de::Error),
    #[error("{0}")]
    Other(String),
}

pub type Result<T> = std::result::Result<T, AppError>;

impl AppError {
    pub fn other(msg: impl Into<String>) -> Self {
        AppError::Other(msg.into())
    }
}
