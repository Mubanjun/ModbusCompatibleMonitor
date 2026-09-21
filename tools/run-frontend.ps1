# 运行 JDRK Qt6 QML 前端
# 需先启动后端（tools\run-backend.ps1 或 backend/target/debug/jdrk-monitor.exe）
$ErrorActionPreference = "Stop"
$ws = Split-Path -Parent $PSScriptRoot
$qt = "C:\Qt\Qt6.11.0\6.11.0\mingw_64"
$mingw = "C:\Qt\Qt6.11.0\Tools\mingw1310_64\bin"

$env:PATH = "$qt\bin;$mingw;$env:PATH"
$env:QML2_IMPORT_PATH = "$qt\qml"
$env:QT_PLUGIN_PATH = "$qt\plugins"

$exe = Join-Path $ws "frontend\build\release\jdrk-monitor-ui.exe"
if (-not (Test-Path $exe)) { throw "未找到前端可执行文件，请先运行 tools\build-frontend.ps1" }

Push-Location (Split-Path $exe)
try { & $exe } finally { Pop-Location }
