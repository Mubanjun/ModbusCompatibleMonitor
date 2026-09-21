# 优雅停止后端：调用 /api/shutdown（会先关闭串口再退出，避免端口被驱动锁住）
# 用法: powershell -File tools\stop-backend.ps1 [-Url http://127.0.0.1:8790]
param(
  [string]$Url = "http://127.0.0.1:8790"
)
$ErrorActionPreference = "Continue"
try {
  $r = Invoke-RestMethod -Uri "$Url/api/shutdown" -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 8
  Write-Host "已请求优雅停机: $($r | ConvertTo-Json -Compress)"
} catch {
  Write-Host "调用 /api/shutdown 失败: $($_.Exception.Message)"
  Write-Host "若后端未运行可忽略；若后端无响应，说明它已异常，请检查日志。"
}
Start-Sleep -Seconds 2
$n = (Get-Process jdrk-monitor -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Host "剩余 jdrk-monitor 进程数: $n"
