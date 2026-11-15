//! 后端控制面访问（心跳 / 版本）。复用同一个阻塞 HTTP 客户端。
use std::time::Duration;

use serde::{Deserialize, Serialize};

use crate::config::ShellConfig;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct CheckItem {
    pub name: String,
    #[serde(default)]
    pub ok: Option<bool>,
    #[serde(default)]
    pub detail: Option<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct Heartbeat {
    #[serde(default)]
    pub healthy: Option<bool>,
    #[serde(default)]
    pub pid: Option<u64>,
    #[serde(default)]
    pub uptime_ms: Option<u64>,
    #[serde(default)]
    pub checks: Vec<CheckItem>,
    #[serde(default)]
    pub reachable: bool,
    #[serde(default)]
    pub error: Option<String>,
}

impl Heartbeat {
    pub fn unreachable(message: String) -> Self {
        Heartbeat {
            reachable: false,
            error: Some(message),
            ..Default::default()
        }
    }

    pub fn is_healthy(&self) -> bool {
        self.reachable && self.healthy.unwrap_or(false)
    }

    /// 第一个失败检查项的「名称：详情」。
    pub fn first_failure(&self) -> Option<String> {
        self.checks
            .iter()
            .find(|item| item.ok == Some(false))
            .map(|item| format!("{}：{}", item.name, item.detail.clone().unwrap_or_default()))
    }
}

fn http_client() -> Result<&'static reqwest::blocking::Client, String> {
    static CLIENT: std::sync::OnceLock<reqwest::blocking::Client> = std::sync::OnceLock::new();
    if let Some(client) = CLIENT.get() {
        return Ok(client);
    }
    let built = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(5))
        .build()
        .map_err(|e| e.to_string())?;
    Ok(CLIENT.get_or_init(|| built))
}

pub fn fetch_json_result(url: &str) -> Result<serde_json::Value, String> {
    let client = http_client()?;
    let response = client
        .get(url)
        .send()
        .map_err(|e| format!("后端不可达：{}", e))?;
    let status = response.status();
    let text = response.text().map_err(|e| e.to_string())?;
    if !status.is_success() {
        return Err(format!("后端返回 {}：{}", status.as_u16(), text));
    }
    serde_json::from_str(&text).map_err(|e| format!("响应不是 JSON：{}", e))
}

pub fn fetch_json(url: &str) -> serde_json::Value {
    fetch_json_result(url).unwrap_or_else(|message| serde_json::json!({ "error": message }))
}

pub fn fetch_heartbeat(config: &ShellConfig) -> Heartbeat {
    let value = match fetch_json_result(&config.endpoint("/monitor/watchdog/heartbeat")) {
        Ok(value) => value,
        Err(message) => return Heartbeat::unreachable(message),
    };
    let data = value.get("data").cloned().unwrap_or(value);
    match serde_json::from_value::<Heartbeat>(data) {
        Ok(mut beat) => {
            beat.reachable = true;
            beat
        }
        Err(e) => Heartbeat::unreachable(format!("心跳解析失败：{}", e)),
    }
}

pub fn fetch_version(config: &ShellConfig) -> serde_json::Value {
    fetch_json(&config.endpoint("/monitor/system/version"))
}
