# 串口健壮性验证：空闲采集 -> 优雅停机(端口释放) -> 二次启动 -> 硬杀(进程/端口)
# 用法: powershell -File tools\verify-serial.ps1 -Exe <backend exe> [-Config config/hardware.toml] [-Port COM3]
param(
  [Parameter(Mandatory=$true)][string]$Exe,
  [string]$Config = "config/hardware.toml",
  [string]$Port = "COM3",
  [int]$BindPort = 8796
)
$ErrorActionPreference = "Continue"
$ws = Split-Path -Parent $PSScriptRoot

function Test-Port {
    param([string]$p)
    python -c "import serial
try:
    s=serial.Serial('$p',9600,timeout=0.2); print('OK'); s.close()
except Exception as e: print('FAIL')" 2>&1
}

function Start-Backend {
    $out = Join-Path $ws ".tmp\verify.out"
    $err = Join-Path $ws ".tmp\verify.err"
    Remove-Item $out, $err -Force -ErrorAction SilentlyContinue
    $env:RUST_LOG = "info"
    $p = Start-Process -FilePath $Exe -ArgumentList '--config', $Config, '--bind', "127.0.0.1:$BindPort" `
        -WorkingDirectory (Join-Path $ws "backend") -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Start-Sleep -Seconds 5
    return $p
}

Write-Host "=== 0) port before: $(Test-Port $Port) ==="

Write-Host "=== 1) start + acquire ==="
$p1 = Start-Backend
try { Write-Host ("link: " + (Invoke-RestMethod -Uri "http://127.0.0.1:$BindPort/api/link/status" -TimeoutSec 5 | ConvertTo-Json -Compress)) } catch { Write-Host "link ERR" }
try { $s = Invoke-RestMethod -Uri "http://127.0.0.1:$BindPort/api/samples/latest?per_channel=1" -TimeoutSec 5; Write-Host "samples=$($s.Count)" } catch { Write-Host "samples ERR" }

Write-Host "=== 2) GRACEFUL shutdown (/api/shutdown) ==="
try { Invoke-RestMethod -Uri "http://127.0.0.1:$BindPort/api/shutdown" -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 8 | Out-Null; Write-Host "shutdown ack" } catch { Write-Host "shutdown ERR" }
$sw = [System.Diagnostics.Stopwatch]::StartNew(); $ok = $p1.WaitForExit(8000); $sw.Stop()
Write-Host "GRACEFUL exit=$ok in $($sw.ElapsedMilliseconds)ms"
Start-Sleep -Milliseconds 500
Write-Host "port after GRACEFUL: $(Test-Port $Port)"

Write-Host "=== 3) second start + HARD kill ==="
$p2 = Start-Backend
$sw2 = [System.Diagnostics.Stopwatch]::StartNew()
Stop-Process -Id $p2.Id -Force
$ok2 = $p2.WaitForExit(8000); $sw2.Stop()
Write-Host "HARD exit=$ok2 in $($sw2.ElapsedMilliseconds)ms"
Start-Sleep -Milliseconds 800
Write-Host "port after HARD: $(Test-Port $Port)"
Write-Host "jdrk procs: $((Get-Process jdrk-monitor -ErrorAction SilentlyContinue | Measure-Object).Count)"
