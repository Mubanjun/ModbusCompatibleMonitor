# ============================================================================
# JDRK 水质监控平台 · 构建 Windows 安装包（Inno Setup）
#
#   .\packaging\windows\build-installer.ps1                     # 全自动：编译后端+前端 → 打包
#   .\packaging\windows\build-installer.ps1 -SkipBuild          # 复用已有产物，只重新打包
#   .\packaging\windows\build-installer.ps1 -SkipFrontend       # 只打包后端（产物不含 GUI）
#   .\packaging\windows\build-installer.ps1 -Version 0.2.0
#
# 依赖：
#   · Rust 工具链（cargo）
#   · Qt6 + MinGW（前端；默认自动探测 C:\Qt\Qt6.*\*\mingw_64）
#   · Inno Setup 6.3+（ISCC.exe；默认自动探测常见安装路径）
#
# 产物：packaging\dist\JDRK-Monitor-<version>-setup.exe
# ============================================================================
[CmdletBinding()]
param(
    [string]$Version = '',
    [switch]$SkipBuild,
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [string]$QtDir = '',
    [string]$InnoSetupPath = '',
    [string]$OutDir = '',
    [string]$StageDir = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$winDir = $PSScriptRoot
$packagingDir = Split-Path -Parent $winDir
$ws = Split-Path -Parent $packagingDir

if (-not $OutDir)   { $OutDir = Join-Path $packagingDir 'dist' }
if (-not $StageDir) { $StageDir = Join-Path $packagingDir 'build\stage' }
$issPath = Join-Path $winDir 'jdrk-monitor.iss'

function Write-Step { param([string]$m) Write-Host ('[installer] ' + $m) -ForegroundColor Cyan }
function Write-Ok   { param([string]$m) Write-Host ('[installer] ' + $m) -ForegroundColor Green }
function Write-Warn2 { param([string]$m) Write-Warning ('[installer] ' + $m) }
function Fail { param([string]$m) throw ('[installer] ' + $m) }

# ---------------------------------------------------------------- 版本号
function Get-ProjectVersion {
    $cargo = Join-Path $ws 'backend\Cargo.toml'
    if (-not (Test-Path -LiteralPath $cargo)) { return '' }
    foreach ($line in Get-Content -LiteralPath $cargo -Encoding UTF8) {
        if ($line -match '^\s*version\s*=\s*"([^"]+)"') { return $Matches[1] }
    }
    return ''
}

if (-not $Version) { $Version = Get-ProjectVersion }
if (-not $Version) { Fail '无法从 backend\Cargo.toml 读取版本号，请用 -Version 指定' }
Write-Step ('项目版本：' + $Version)

# ---------------------------------------------------------------- Qt 定位
function Find-QtDir {
    foreach ($root in @('C:\Qt', 'D:\Qt', (Join-Path $env:USERPROFILE 'Qt'))) {
        if (-not (Test-Path -LiteralPath $root)) { continue }
        $kits = @()
        foreach ($vendor in (Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue)) {
            foreach ($ver in (Get-ChildItem -LiteralPath $vendor.FullName -Directory -ErrorAction SilentlyContinue)) {
                foreach ($kit in (Get-ChildItem -LiteralPath $ver.FullName -Directory -ErrorAction SilentlyContinue)) {
                    if (Test-Path -LiteralPath (Join-Path $kit.FullName 'bin\qmake.exe')) { $kits += $kit }
                }
            }
        }
        $mingw = $kits | Where-Object { $_.Name -like '*mingw*' } | Sort-Object FullName -Descending
        if ($mingw) { return $mingw[0].FullName }
        if ($kits) { return ($kits | Sort-Object FullName -Descending)[0].FullName }
    }
    return ''
}

if (-not $SkipFrontend) {
    if (-not $QtDir) {
        $QtDir = Find-QtDir
        if (-not $QtDir) { Fail '未找到 Qt6（可用 -QtDir 指定，例如 C:\Qt\Qt6.11.0\6.11.0\mingw_64）' }
    }
    $qtBin = Join-Path $QtDir 'bin'
    $qmake = Join-Path $qtBin 'qmake.exe'
    if (-not (Test-Path -LiteralPath $qmake)) { Fail ('Qt 目录中缺少 qmake.exe：' + $qmake) }

    # MinGW 工具链（Qt 自带）
    $qtRoot = Split-Path -Parent (Split-Path -Parent $QtDir)
    $mingwBin = ''
    $toolsDir = Join-Path $qtRoot 'Tools'
    if (Test-Path -LiteralPath $toolsDir) {
        $mg = Get-ChildItem -LiteralPath $toolsDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'mingw*' } | Sort-Object Name -Descending
        if ($mg) { $mingwBin = Join-Path $mg[0].FullName 'bin' }
    }
    $env:PATH = $qtBin + ';' + $mingwBin + ';' + $env:PATH
    Write-Step ('Qt：' + $QtDir)
}

# ---------------------------------------------------------------- 编译后端
$backendExe = Join-Path $ws 'backend\target\release\jdrk-monitor.exe'
if (-not $SkipBackend) {
    if (-not $SkipBuild) {
        Write-Step '编译后端（cargo build --release）…'
        & cargo build --release --manifest-path (Join-Path $ws 'backend\Cargo.toml')
        if ($LASTEXITCODE -ne 0) { Fail 'cargo build 失败' }
    }
    if (-not (Test-Path -LiteralPath $backendExe)) {
        Fail ('后端产物不存在：' + $backendExe + '（去掉 -SkipBuild 重新编译）')
    }
}

# ---------------------------------------------------------------- 编译前端
$uiExe = Join-Path $ws 'frontend\build\release\jdrk-monitor-ui.exe'
if (-not $SkipFrontend) {
    if (-not $SkipBuild) {
        Write-Step '编译前端（qmake + mingw32-make）…'
        & (Join-Path $ws 'tools\build-frontend.ps1')
        if ($LASTEXITCODE -ne 0) { Fail '前端编译失败' }
    }
    if (-not (Test-Path -LiteralPath $uiExe)) {
        Fail ('前端产物不存在：' + $uiExe + '（去掉 -SkipBuild 重新编译）')
    }
}

# ---------------------------------------------------------------- 暂存目录
Write-Step ('生成暂存目录：' + $StageDir)
foreach ($sub in @('backend', 'ui', 'tools', 'docs')) {
    $p = Join-Path $StageDir $sub
    if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $p | Out-Null
}

if (-not $SkipBackend) {
    Copy-Item -LiteralPath $backendExe -Destination (Join-Path $StageDir 'backend\jdrk-monitor.exe') -Force
}

# 由 backend\config\default.toml 派生安装配置（单一数据源），路径改为 Windows 安装布局
function New-StageConfig {
    param([string]$SourceToml, [string]$TargetToml)
    $header = @(
        '# ============================================================',
        '# JDRK 水质监控平台 · 安装配置（升级不会覆盖本文件）',
        '# 由 packaging\windows\build-installer.ps1 依据 backend\config\default.toml 生成。',
        '# 修改后重启服务：powershell -File tools\jdrk-service.ps1 -Action Restart',
        '# ============================================================'
    )
    # -Encoding UTF8 必须显式指定：Windows PowerShell 5.1 默认按系统 ANSI 读文件，
    # 会把 UTF-8 的中文注释解成乱码（甚至让换行错位）
    $body = Get-Content -LiteralPath $SourceToml -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*bind\s*=') { 'bind = "0.0.0.0:8790"' }
        elseif ($_ -match '^\s*sqlite_path\s*=') { 'sqlite_path = "C:/ProgramData/JDRK/data/monitor.db"' }
        else { $_ }
    }
    # 注意：必须以「无 BOM 的 UTF-8」写入，否则后端 TOML 解析会失败
    $text = [string]::Join([Environment]::NewLine, ($header + $body))
    [System.IO.File]::WriteAllText($TargetToml, $text, (New-Object System.Text.UTF8Encoding($false)))
}

Copy-Item -LiteralPath (Join-Path $winDir 'jdrk-service.ps1') -Destination (Join-Path $StageDir 'tools\jdrk-service.ps1') -Force
New-StageConfig -SourceToml (Join-Path $ws 'backend\config\default.toml') -TargetToml (Join-Path $StageDir 'backend\config.toml')

# 文档
Copy-Item -LiteralPath (Join-Path $ws 'README.md') -Destination (Join-Path $StageDir 'docs\README.md') -Force
Copy-Item -LiteralPath (Join-Path $packagingDir 'README.md') -Destination (Join-Path $StageDir 'docs\部署说明.md') -Force
$docsDir = Join-Path $ws 'docs'
if (Test-Path -LiteralPath $docsDir) {
    Copy-Item -Path (Join-Path $docsDir '*.md') -Destination (Join-Path $StageDir 'docs') -Force
}
if (Test-Path -LiteralPath (Join-Path $ws 'LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $ws 'LICENSE') -Destination (Join-Path $StageDir 'docs\LICENSE') -Force
}

# ---------------------------------------------------------------- 前端运行库
if (-not $SkipFrontend) {
    Write-Step '用 windeployqt 收集 Qt 运行库与 QML 模块…'
    $stageUi = Join-Path $StageDir 'ui'
    Copy-Item -LiteralPath $uiExe -Destination (Join-Path $stageUi 'jdrk-monitor-ui.exe') -Force
    $windeploy = Join-Path $QtDir 'bin\windeployqt.exe'
    if (-not (Test-Path -LiteralPath $windeploy)) { Fail ('缺少 windeployqt.exe：' + $windeploy) }
    # --qmldir 让 windeployqt 扫描 QML 源码，正确收集 QtQuick.Controls 等模块
    & $windeploy --release --no-translations --qmldir (Join-Path $ws 'frontend\qml') (Join-Path $stageUi 'jdrk-monitor-ui.exe')
    if ($LASTEXITCODE -ne 0) { Write-Warn2 ('windeployqt 返回码 ' + $LASTEXITCODE) }

    foreach ($need in @('Qt6Core.dll', 'Qt6Gui.dll', 'Qt6Qml.dll', 'Qt6Quick.dll', 'Qt6QuickControls2.dll')) {
        if (-not (Test-Path -LiteralPath (Join-Path $stageUi $need))) { Write-Warn2 ('缺少运行库 ' + $need + '，打包后 GUI 可能无法启动') }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $stageUi 'qml\QtQuick\Controls'))) {
        Write-Warn2 'QML 模块目录 qml\QtQuick\Controls 不存在，打包后 GUI 可能报“module is not installed”'
    }
}

# ---------------------------------------------------------------- 定位 ISCC
function Find-Iscc {
    param([string]$Hint)
    if ($Hint) {
        if (Test-Path -LiteralPath $Hint) { return $Hint }
        $cand = Join-Path $Hint 'ISCC.exe'
        if (Test-Path -LiteralPath $cand) { return $cand }
        Fail ('指定的 Inno Setup 路径无效：' + $Hint)
    }
    $cmd = Get-Command 'ISCC.exe' -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($p in @(
        'C:\Program Files (x86)\Inno Setup 6\ISCC.exe',
        'C:\Program Files\Inno Setup 6\ISCC.exe',
        'C:\Program Files (x86)\Inno Setup 5\ISCC.exe',
        'C:\Program Files\Inno Setup 5\ISCC.exe'
    )) {
        if (Test-Path -LiteralPath $p) { return $p }
    }
    foreach ($hive in @('HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1',
                        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Inno Setup 6_is1')) {
        $loc = (Get-ItemProperty -Path $hive -Name InstallLocation -ErrorAction SilentlyContinue).InstallLocation
        if ($loc) {
            $cand = Join-Path $loc 'ISCC.exe'
            if (Test-Path -LiteralPath $cand) { return $cand }
        }
    }
    Fail '未找到 ISCC.exe，请安装 Inno Setup 6.3+ 或用 -InnoSetupPath 指定'
}

$iscc = Find-Iscc -Hint $InnoSetupPath
Write-Step ('ISCC：' + $iscc)

if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Force -Path $OutDir | Out-Null }

# ---------------------------------------------------------------- 编译安装包
# 版本号与 staging/输出路径通过环境变量传给 Inno Setup 的 GetEnv()：
#   · 不用 iscc /D<name>=<含反斜杠路径>：会被 PowerShell 的参数转义破坏；
#   · 不用 #include 的 ISPP defines 文件：受文件编码与行处理影响，行为不稳定。
$env:JDRK_APP_VERSION = $Version
$env:JDRK_STAGE_DIR   = $StageDir
$env:JDRK_OUT_DIR     = $OutDir
$env:JDRK_WITH_UI     = $(if ($SkipFrontend) { '0' } else { '1' })
Write-Step ('ISPP 变量：AppVersion=' + $Version + '  StageDir=' + $StageDir)

Write-Step '生成安装包…'
& $iscc $issPath
if ($LASTEXITCODE -ne 0) { Fail ('ISCC 编译失败，返回码 ' + $LASTEXITCODE) }

$setup = Join-Path $OutDir ('JDRK-Monitor-' + $Version + '-setup.exe')
if (Test-Path -LiteralPath $setup) {
    Write-Ok ('安装包已生成：' + $setup + '（' + [math]::Round((Get-Item -LiteralPath $setup).Length / 1MB, 2) + ' MB）')
} else {
    Write-Warn2 '未找到预期的安装包文件名，请检查 packaging\dist 目录'
}
