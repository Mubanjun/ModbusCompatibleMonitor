//! 服务控制：始终交给独立看门狗 / 系统服务，TUI 只下发指令（FR-QT-02、FR-SYS-05）。
use std::io::Read;
use std::process::{Command, Output, Stdio};
use std::time::{Duration, Instant};

use crate::config::{env_seconds, ShellConfig};

pub fn service_timeout() -> Duration {
    env_seconds("JDRK_SERVICE_TIMEOUT_SECONDS", 180, 5, 3600)
}

fn drain(pipe: Option<impl Read>) -> Vec<u8> {
    let mut buffer = Vec::new();
    if let Some(mut pipe) = pipe {
        let _ = pipe.read_to_end(&mut buffer);
    }
    buffer
}

/// 执行外部命令并强制超时（缺陷 62/O-8 的修复从原壳原样迁移）。
pub fn output_with_timeout(
    mut command: Command,
    timeout: Duration,
    label: &str,
) -> Result<Output, String> {
    let mut child = command
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("{} 启动失败：{}", label, e))?;
    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let read_stdout = std::thread::spawn(move || drain(stdout));
    let read_stderr = std::thread::spawn(move || drain(stderr));
    let deadline = Instant::now() + timeout;
    loop {
        match child.try_wait() {
            Ok(Some(status)) => {
                return Ok(Output {
                    status,
                    stdout: read_stdout.join().unwrap_or_default(),
                    stderr: read_stderr.join().unwrap_or_default(),
                });
            }
            Ok(None) => {
                if Instant::now() >= deadline {
                    let _ = child.kill();
                    let _ = child.wait();
                    let _ = read_stdout.join();
                    let _ = read_stderr.join();
                    return Err(format!(
                        "{} 执行超时（>{} 秒）：请检查数据库/Redis 是否可用",
                        label,
                        timeout.as_secs()
                    ));
                }
                std::thread::sleep(Duration::from_millis(200));
            }
            Err(e) => return Err(format!("{} 等待失败：{}", label, e)),
        }
    }
}

fn finish(output: Output, label: &str) -> Result<String, String> {
    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).to_string();
    if output.status.success() {
        Ok(if stdout.trim().is_empty() {
            format!("{} 执行完成", label)
        } else {
            stdout
        })
    } else {
        Err(format!(
            "{} 失败（退出码 {:?}）：{}{}",
            label,
            output.status.code(),
            stdout,
            stderr
        ))
    }
}

pub fn run_service(action: &str, config: &ShellConfig) -> Result<String, String> {
    let custom = match action {
        "start" => config.start_command.clone(),
        "stop" => config.stop_command.clone(),
        "restart" => config.restart_command.clone(),
        _ => None,
    };
    if let Some(command) = custom {
        return run_shell(&command, config);
    }
    platform_service(action, config)
}

#[cfg(target_os = "windows")]
fn platform_service(action: &str, config: &ShellConfig) -> Result<String, String> {
    let script = match action {
        "start" => "tools/start-monitor.ps1",
        "stop" => "tools/stop-monitor.ps1",
        _ => "tools/restart-monitor.ps1",
    };
    let path = config.home.join(script);
    if !path.exists() {
        return Err(format!("未找到脚本 {}", path.display()));
    }
    let mut command = Command::new("powershell");
    command
        .args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-File"])
        .arg(&path)
        .current_dir(&config.home);
    let output = output_with_timeout(command, service_timeout(), script)?;
    finish(output, script)
}

#[cfg(target_os = "linux")]
fn platform_service(action: &str, config: &ShellConfig) -> Result<String, String> {
    let unit =
        std::env::var("JDRK_SYSTEMD_UNIT").unwrap_or_else(|_| "jdrk-watchdog.service".to_string());
    let verbs = match action {
        "start" => vec!["start"],
        "stop" => vec!["stop"],
        _ => vec!["restart"],
    };
    let mut last_error = String::new();
    for prefix in [vec!["--user"], vec![]] {
        let mut command = Command::new("systemctl");
        command
            .args(&prefix)
            .args(&verbs)
            .arg(&unit)
            .current_dir(&config.home);
        match output_with_timeout(command, service_timeout(), "systemctl") {
            Ok(output) if output.status.success() => {
                return Ok(format!("systemctl {} {} 成功", prefix.join(" "), unit));
            }
            Ok(output) => last_error = String::from_utf8_lossy(&output.stderr).to_string(),
            Err(e) => last_error = e.to_string(),
        }
    }
    Err(format!("systemctl 执行失败：{}", last_error))
}

#[cfg(not(any(target_os = "windows", target_os = "linux")))]
fn platform_service(_action: &str, _config: &ShellConfig) -> Result<String, String> {
    Err("当前平台未实现服务控制".to_string())
}

fn run_shell(command: &str, config: &ShellConfig) -> Result<String, String> {
    #[cfg(target_os = "windows")]
    let mut child = Command::new("powershell");
    #[cfg(not(target_os = "windows"))]
    let mut child = Command::new("sh");
    #[cfg(target_os = "windows")]
    child.args([
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        command,
    ]);
    #[cfg(not(target_os = "windows"))]
    child.args(["-c", command]);
    child.current_dir(&config.home);
    let output = output_with_timeout(child, service_timeout(), command)?;
    finish(output, command)
}
