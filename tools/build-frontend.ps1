# Build the JDRK Qt6 QML frontend (Windows + MinGW).
# NOTE: qmake's compiler probe is blocked in this sandbox, so we pre-seed .qmake.stash.
param(
  [switch]$Clean
)
$ErrorActionPreference = "Stop"
$ws = Split-Path -Parent $PSScriptRoot
$qt = "C:\Qt\Qt6.11.0\6.11.0\mingw_64"
$mingw = "C:\Qt\Qt6.11.0\Tools\mingw1310_64\bin"

$env:PATH = "$qt\bin;$mingw;$env:PATH"
$env:TEMP = Join-Path $ws ".tmp"
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Force -Path $env:TEMP | Out-Null

$build = Join-Path $ws "frontend\build"
if ($Clean) { Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $build | Out-Null

$stash = Join-Path $build ".qmake.stash"
if (-not (Test-Path $stash)) {
    $mw = "C:/Qt/Qt6.11.0/Tools/mingw1310_64"
    $inc = "$mw/lib/gcc/x86_64-w64-mingw32/13.1.0/include/c++ $mw/lib/gcc/x86_64-w64-mingw32/13.1.0/include/c++/x86_64-w64-mingw32 $mw/lib/gcc/x86_64-w64-mingw32/13.1.0/include/c++/backward $mw/lib/gcc/x86_64-w64-mingw32/13.1.0/include $mw/lib/gcc/x86_64-w64-mingw32/13.1.0/include-fixed $mw/x86_64-w64-mingw32/include"
    $lib = "$mw/lib/gcc/x86_64-w64-mingw32/13.1.0 $mw/lib/gcc $mw/x86_64-w64-mingw32/lib $mw/lib"
    $lines = @(
        "QMAKE_CXX.COMPILER_MACROS = QT_COMPILER_STDCXX QMAKE_GCC_MAJOR_VERSION QMAKE_GCC_MINOR_VERSION QMAKE_GCC_PATCH_VERSION",
        "QMAKE_CXX.QT_COMPILER_STDCXX = 201703L",
        "QMAKE_CXX.QMAKE_GCC_MAJOR_VERSION = 13",
        "QMAKE_CXX.QMAKE_GCC_MINOR_VERSION = 1",
        "QMAKE_CXX.QMAKE_GCC_PATCH_VERSION = 0",
        "QMAKE_CXX.INCDIRS = $inc",
        "QMAKE_CXX.LIBDIRS = $lib"
    )
    Set-Content -Encoding ASCII -Path $stash -Value $lines
    Write-Host "seeded $stash"
}

Push-Location $build
try {
    qmake ..\jdrk-frontend.pro
    if ($LASTEXITCODE -ne 0) { throw "qmake failed" }
    mingw32-make -j4
    if ($LASTEXITCODE -ne 0) { throw "make failed" }
} finally {
    Pop-Location
}
Write-Host "built: $build\release\jdrk-monitor-ui.exe"
