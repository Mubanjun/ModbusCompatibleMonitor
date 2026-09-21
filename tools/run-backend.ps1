# 启动 JDRK 后端（自动准备 cargo 环境 + 本地 crates 代理）
param(
  [string]$Config = "config/default.toml",
  [string]$Bind = "",
  [switch]$Release
)

$ErrorActionPreference = "Stop"
$ws = Split-Path -Parent $PSScriptRoot

# 1) workspace 内的 CARGO_HOME（沙箱下 ~/.cargo 不可写）
$env:CARGO_HOME = Join-Path $ws ".cargo-home"
New-Item -ItemType Directory -Force -Path $env:CARGO_HOME | Out-Null

# 2) 本机 schannel 不可用，cargo 走本地 HTTP 稀疏索引代理
$proxy = Join-Path $ws "tools\local_crates_proxy.py"
$listening = Test-NetConnection -ComputerName 127.0.0.1 -Port 8787 -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $listening) {
  Start-Process python -ArgumentList "$proxy 8787" -WorkingDirectory $ws -WindowStyle Hidden
  Start-Sleep -Seconds 2
}

# 3) Qt 自带 MinGW（Rust windows-gnu 目标需要 gcc/ld）
$mingw = "C:\Qt\Qt6.11.0\Tools\mingw1310_64\bin"
if (Test-Path $mingw) { $env:PATH = "$mingw;$env:PATH" }

Push-Location (Join-Path $ws "backend")
try {
  $profile = if ($Release) { "--release" } else { "" }
  if ($Bind -ne "") {
    cargo run $profile -- --config $Config --bind $Bind
  } else {
    cargo run $profile -- --config $Config
  }
} finally {
  Pop-Location
}
