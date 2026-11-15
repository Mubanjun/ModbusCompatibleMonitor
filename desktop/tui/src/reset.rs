//! 离线重置超管密码（FR-QT-04）：后端停机时复用后端 CLI 入口。
use std::process::Command;

use crate::api::fetch_json_result;
use crate::config::{env_seconds, ShellConfig};
use crate::service::output_with_timeout;

pub fn reset_password(config: &ShellConfig, password: &str) -> Result<String, String> {
    if password.chars().count() < 8 {
        return Err("密码长度至少 8 位".to_string());
    }
    // 后端在跑时 CLI 会撞单实例锁（实测退出码 1），先探测并给出可执行提示。
    if fetch_json_result(&config.endpoint("/monitor/watchdog/heartbeat")).is_ok() {
        return Err(
            "平台正在运行：请先在「服务控制」停止服务，再执行离线重置（离线重置要求后端停机）"
                .to_string(),
        );
    }
    let tenant = std::env::var("JDRK_RESET_TENANT").unwrap_or_else(|_| "0".to_string());
    let timeout = env_seconds("JDRK_RESET_TIMEOUT_SECONDS", 120, 5, 3600);
    let mut command = Command::new(&config.java);
    command
        .arg("-jar")
        .arg(&config.jar)
        .arg("--server.port=0")
        .arg("--jdrk.tool.reset-password=admin")
        .arg(format!("--jdrk.tool.reset-password-value={}", password))
        .arg(format!("--jdrk.tool.reset-password-tenant={}", tenant))
        .current_dir(&config.home);
    let output = output_with_timeout(command, timeout, "离线重置超管密码")
        .map_err(|e| format!("{}（jar={}）", e, config.jar.display()))?;
    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).to_string();
    if output.status.success() {
        Ok(stdout)
    } else {
        Err(format!(
            "重置失败（退出码 {:?}）：{}{}",
            output.status.code(),
            stdout,
            stderr
        ))
    }
}
