//! JDRK 水质监控平台 · Rust 后端入口。

mod alarm;
mod api;
mod app;
mod collector;
mod config;
mod domain;
mod error;
mod events;
mod forwarder;
mod modbus;
mod state;
mod store;

use clap::Parser;
use tracing_subscriber::EnvFilter;

#[derive(Parser, Debug)]
#[command(name = "jdrk-monitor", version, about = "JDRK 水质监控平台后端")]
struct Cli {
    /// 配置文件路径
    #[arg(short, long, default_value = "config/default.toml")]
    config: String,
    /// 覆盖监听地址，如 0.0.0.0:8787
    #[arg(long)]
    bind: Option<String>,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .with_target(false)
        .init();

    if !modbus::crc::self_test() {
        eprintln!("CRC 自检失败，程序终止");
        std::process::exit(2);
    }

    let cli = Cli::parse();
    app::run(&cli.config, cli.bind).await
}
