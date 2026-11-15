mod api;
mod app;
mod config;
mod logs;
mod metrics;
mod reset;
mod service;
#[cfg(test)]
mod tests;
mod ui;

use clap::Parser;

/// 仁科兼容环境监测平台 TUI 终端管理端（FR-QT）
#[derive(Parser, Debug)]
#[command(
    name = "jdrk-tui",
    version,
    about = "仁科兼容环境监测平台 TUI 终端管理端"
)]
struct Args {
    /// 非交互模式：输出一次状态后退出（FR-QT-08）
    #[arg(long)]
    headless: bool,
    /// 状态刷新周期（秒），覆盖 JDRK_MONITOR_INTERVAL_SECONDS
    #[arg(long)]
    interval: Option<u64>,
}

fn main() {
    let args = Args::parse();
    let config = config::ShellConfig::load();
    if args.headless {
        match app::run_headless(&config) {
            Ok(()) => {}
            Err(e) => {
                eprintln!("headless 状态输出失败：{}", e);
                std::process::exit(1);
            }
        }
        return;
    }
    if let Err(e) = app::run(config, args.interval) {
        eprintln!("TUI 运行失败：{}", e);
        std::process::exit(1);
    }
}
