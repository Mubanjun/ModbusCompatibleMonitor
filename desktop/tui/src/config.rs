//! 运行参数解析（与原桌面壳 ShellConfig 保持一致，全部可用环境变量覆盖）。
use std::path::{Path, PathBuf};
use std::time::Duration;

#[derive(Clone, Debug)]
pub struct ShellConfig {
    pub home: PathBuf,
    pub api_base: String,
    pub web_base: String,
    pub jar: PathBuf,
    pub java: PathBuf,
    pub start_command: Option<String>,
    pub stop_command: Option<String>,
    pub restart_command: Option<String>,
}

/// 在候选路径里挑第一个存在的（jar / java 默认值解析）。
pub fn first_existing(candidates: Vec<PathBuf>) -> Option<PathBuf> {
    candidates.into_iter().find(|path| path.exists())
}

/// jar 候选列表：用户主目录优先，其次可执行文件所在目录。
pub fn jar_candidates(home: &Path, exe_dir: Option<&Path>) -> Vec<PathBuf> {
    const REL: [&str; 3] = [
        "continew-admin.jar",
        "dist/continew-admin.jar",
        "backend/continew-server/build/libs/continew-admin.jar",
    ];
    let mut list = vec![home.join(REL[0])];
    if let Some(dir) = exe_dir {
        for rel in REL {
            list.push(dir.join(rel));
        }
    }
    for rel in REL {
        list.push(home.join(rel));
    }
    list
}

pub fn user_home() -> Option<PathBuf> {
    std::env::var("HOME")
        .ok()
        .or_else(|| std::env::var("USERPROFILE").ok())
        .filter(|value| !value.trim().is_empty())
        .map(PathBuf::from)
}

impl ShellConfig {
    pub fn load() -> Self {
        let exe_dir = std::env::current_exe()
            .ok()
            .and_then(|p| p.parent().map(PathBuf::from));
        let home = std::env::var("JDRK_HOME")
            .map(PathBuf::from)
            .ok()
            .or_else(user_home)
            .or_else(|| exe_dir.clone())
            .unwrap_or_else(|| PathBuf::from("."));
        let api_base =
            std::env::var("JDRK_API_BASE").unwrap_or_else(|_| "http://127.0.0.1:8000".to_string());
        let web_base = std::env::var("JDRK_WEB_BASE").unwrap_or_else(|_| api_base.clone());
        let jar = std::env::var("JDRK_JAR")
            .map(PathBuf::from)
            .unwrap_or_else(|_| {
                first_existing(jar_candidates(&home, exe_dir.as_deref()))
                    .unwrap_or_else(|| home.join("continew-admin.jar"))
            });
        let java = std::env::var("JDRK_JAVA")
            .map(PathBuf::from)
            .unwrap_or_else(|_| {
                first_existing(vec![
                    home.join("dist/runtime/bin/java"),
                    home.join("runtime/bin/java"),
                    home.join("dist/runtime/bin/java.exe"),
                    home.join("runtime/bin/java.exe"),
                ])
                .unwrap_or_else(|| PathBuf::from("java"))
            });
        ShellConfig {
            home,
            api_base: api_base.trim_end_matches('/').to_string(),
            web_base: web_base.trim_end_matches('/').to_string(),
            jar,
            java,
            start_command: std::env::var("JDRK_START_CMD").ok(),
            stop_command: std::env::var("JDRK_STOP_CMD").ok(),
            restart_command: std::env::var("JDRK_RESTART_CMD").ok(),
        }
    }

    pub fn endpoint(&self, path: &str) -> String {
        format!("{}{}", self.api_base, path)
    }
}

/// 读取「秒」型环境变量并按区间钳位。
pub fn env_seconds(name: &str, default: u64, min: u64, max: u64) -> Duration {
    let seconds = std::env::var(name)
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
        .unwrap_or(default)
        .clamp(min, max);
    Duration::from_secs(seconds)
}
