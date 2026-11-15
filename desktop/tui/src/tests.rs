//! 迁移自原桌面壳的单元测试（保持回归口径一致）。
use crate::config::{env_seconds, first_existing, jar_candidates};
use crate::service::output_with_timeout;
use std::process::Command;
use std::time::{Duration, Instant};

#[test]
fn first_existing_picks_the_first_hit() {
    let dir = std::env::temp_dir().join("jdrk-tui-config-test");
    std::fs::create_dir_all(&dir).unwrap();
    let hit = dir.join("hit.jar");
    std::fs::write(&hit, b"x").unwrap();
    let picked = first_existing(vec![
        dir.join("missing.jar"),
        hit.clone(),
        dir.join("also-missing.jar"),
    ]);
    assert_eq!(picked.as_deref(), Some(hit.as_path()));
}

#[test]
fn first_existing_returns_none_when_nothing_exists() {
    let dir = std::env::temp_dir().join("jdrk-tui-config-test-none");
    assert!(first_existing(vec![dir.join("a"), dir.join("b")]).is_none());
}

#[test]
fn jar_candidates_prefer_user_home() {
    let home = std::path::Path::new("/home/wwt");
    let exe = std::path::Path::new("/opt/jdrk");
    let list = jar_candidates(home, Some(exe));
    assert_eq!(list[0], home.join("continew-admin.jar"));
    assert!(list.contains(&exe.join("continew-admin.jar")));
    assert!(list.contains(&home.join("dist/continew-admin.jar")));
    assert!(list.contains(&home.join("backend/continew-server/build/libs/continew-admin.jar")));
    assert_eq!(
        jar_candidates(home, None)[0],
        home.join("continew-admin.jar")
    );
}

#[test]
fn env_seconds_uses_default_and_clamps() {
    const NAME: &str = "JDRK_TEST_TIMEOUT_SECONDS";
    std::env::remove_var(NAME);
    assert_eq!(env_seconds(NAME, 120, 5, 3600).as_secs(), 120);
    std::env::set_var(NAME, "30");
    assert_eq!(env_seconds(NAME, 120, 5, 3600).as_secs(), 30);
    std::env::set_var(NAME, "0");
    assert_eq!(env_seconds(NAME, 120, 5, 3600).as_secs(), 5);
    std::env::set_var(NAME, "999999");
    assert_eq!(env_seconds(NAME, 120, 5, 3600).as_secs(), 3600);
    std::env::set_var(NAME, "not-a-number");
    assert_eq!(env_seconds(NAME, 120, 5, 3600).as_secs(), 120);
    std::env::remove_var(NAME);
}

#[test]
fn command_timeout_is_enforced() {
    #[cfg(target_os = "windows")]
    let command = {
        let mut c = Command::new("powershell");
        c.args(["-NoProfile", "-Command", "Start-Sleep -Seconds 5"]);
        c
    };
    #[cfg(not(target_os = "windows"))]
    let command = {
        let mut c = Command::new("sh");
        c.args(["-c", "sleep 5"]);
        c
    };
    let started = Instant::now();
    let error = output_with_timeout(command, Duration::from_secs(1), "超时用例").unwrap_err();
    assert!(error.contains("超时"), "错误信息应说明超时：{}", error);
    assert!(started.elapsed() < Duration::from_secs(4));
}

#[test]
fn command_returns_output_on_success() {
    #[cfg(target_os = "windows")]
    let command = {
        let mut c = Command::new("cmd");
        c.args(["/C", "echo jdrk-ok"]);
        c
    };
    #[cfg(not(target_os = "windows"))]
    let command = {
        let mut c = Command::new("sh");
        c.args(["-c", "echo jdrk-ok"]);
        c
    };
    let output = output_with_timeout(command, Duration::from_secs(30), "正常用例").unwrap();
    assert!(output.status.success());
    assert!(String::from_utf8_lossy(&output.stdout).contains("jdrk-ok"));
}

#[test]
fn tail_lines_reads_only_tail() {
    // 审计缺陷 75 回归：大日志（>256KB）只读尾部，且返回最后一行
    let dir = std::env::temp_dir().join("jdrk-tui-log-tail");
    std::fs::create_dir_all(&dir).unwrap();
    let path = dir.join("big.log");
    let mut content = String::new();
    for i in 0..60000 {
        content.push_str(&format!("line-{:06}\n", i));
    }
    content.push_str("LAST-LINE\n");
    std::fs::write(&path, &content).unwrap();
    assert!(std::fs::metadata(&path).unwrap().len() > 256 * 1024);
    let lines = crate::logs::tail_lines(&path, 5);
    assert_eq!(lines.last().map(|s| s.as_str()), Some("LAST-LINE"));
    assert!(lines.len() <= 5);
}

#[test]
fn log_filter_splits_level_and_keyword() {
    let lines: Vec<String> = vec![
        "2026-10-02 INFO 采集启动".to_string(),
        "2026-10-02 ERROR 串口超时".to_string(),
        "2026-10-02 ERROR 数据库断开".to_string(),
    ];
    let filtered = crate::logs::filter_lines(&lines, "ERROR", "串口");
    assert_eq!(filtered.len(), 1);
    assert!(filtered[0].contains("串口"));
    let all_error = crate::logs::filter_lines(&lines, "ERROR", "");
    assert_eq!(all_error.len(), 2);
}
