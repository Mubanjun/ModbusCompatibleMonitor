//! 日志查看与过滤（FR-QT-05）：只读本地日志目录，支持级别/关键字过滤与尾行。
use std::path::{Path, PathBuf};

use crate::config::ShellConfig;

pub fn log_dir(config: &ShellConfig) -> PathBuf {
    if let Ok(dir) = std::env::var("JDRK_LOG_DIR") {
        if !dir.trim().is_empty() {
            return PathBuf::from(dir);
        }
    }
    config.home.join("logs")
}

/// 列出日志文件：logs/*.log 与 logs/module/*.log，按修改时间倒序。
pub fn list_logs(dir: &Path) -> Vec<PathBuf> {
    let mut files: Vec<PathBuf> = Vec::new();
    collect(dir, 0, &mut files);
    files.sort_by_key(|path| {
        std::fs::metadata(path)
            .and_then(|m| m.modified())
            .ok()
            .map(std::cmp::Reverse)
    });
    files
}

fn collect(dir: &Path, depth: u32, out: &mut Vec<PathBuf>) {
    if depth > 1 {
        return;
    }
    let Ok(entries) = std::fs::read_dir(dir) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_dir() {
            collect(&path, depth + 1, out);
        } else if path.extension().and_then(|e| e.to_str()) == Some("log") {
            out.push(path);
        }
    }
}

/// 读取文件末尾 n 行（大文件只读尾部 256KB）。
///
/// 审计缺陷 75：原实现用 std::fs::read 整读再切片，注释虽写「只读尾部」，
/// 实测现场日志可达数百 MB（watchdog.properties 记载一个下午 278MB），会整块分配内存。
/// 改为 seek 到尾部读取。
pub fn tail_lines(path: &Path, n: usize) -> Vec<String> {
    use std::io::{Read, Seek, SeekFrom};
    let mut file = match std::fs::File::open(path) {
        Ok(file) => file,
        Err(_) => return vec![format!("无法读取日志：{}", path.display())],
    };
    let len = file.metadata().map(|m| m.len()).unwrap_or(0);
    let start = len.saturating_sub(256 * 1024);
    if start > 0 && file.seek(SeekFrom::Start(start)).is_err() {
        return vec!["无法定位日志尾部".to_string()];
    }
    let mut buf = Vec::new();
    if file.read_to_end(&mut buf).is_err() {
        return vec!["无法读取日志尾部".to_string()];
    }
    let text = String::from_utf8_lossy(&buf);
    let mut lines: Vec<String> = text.lines().map(|l| l.to_string()).collect();
    if lines.len() > n {
        lines = lines.split_off(lines.len() - n);
    }
    lines
}

/// 级别 + 关键字过滤（大小写不敏感）。
pub fn filter_lines(lines: &[String], level: &str, keyword: &str) -> Vec<String> {
    let level = level.trim().to_ascii_uppercase();
    let keyword = keyword.trim().to_ascii_lowercase();
    lines
        .iter()
        .filter(|line| {
            let upper = line.to_ascii_uppercase();
            (level.is_empty() || upper.contains(&level))
                && (keyword.is_empty() || line.to_ascii_lowercase().contains(&keyword))
        })
        .cloned()
        .collect()
}
