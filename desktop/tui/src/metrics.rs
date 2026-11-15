//! 本机指标采集（FR-QT-01）：复用开源 sysinfo，不自研。
//!
//! 审计缺陷 76：原实现每次快照都 new 一个 System，sysinfo 的 CPU 使用率依赖两次刷新的差值，
//! 新建实例后首次 refresh 恒为 0（面板 CPU 永远显示 0%）。改为 App 持有长期存活的 System。
use sysinfo::{Disks, System};

#[derive(Clone, Copy, Default)]
pub struct Metrics {
    pub cpu_percent: f32,
    pub mem_used: u64,
    pub mem_total: u64,
    pub disk_used: u64,
    pub disk_total: u64,
}

pub fn snapshot(sys: &mut System) -> Metrics {
    sys.refresh_cpu_usage();
    sys.refresh_memory();
    let mut disk_used = 0u64;
    let mut disk_total = 0u64;
    for disk in Disks::new_with_refreshed_list().list() {
        disk_total += disk.total_space();
        disk_used += disk.total_space().saturating_sub(disk.available_space());
    }
    Metrics {
        cpu_percent: sys.global_cpu_usage(),
        mem_used: sys.used_memory(),
        mem_total: sys.total_memory(),
        disk_used,
        disk_total,
    }
}

pub fn human(bytes: u64) -> String {
    const GB: f64 = 1024.0 * 1024.0 * 1024.0;
    format!("{:.1} GB", bytes as f64 / GB)
}
