; ============================================================================
; JDRK 水质监控平台 · Inno Setup 安装脚本（Inno Setup 6.3+ / 7.x）
;
; 编译（推荐用 build-installer.ps1，它会先构建并暂存文件）：
;   "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" ^
;       /DAppVersion=0.1.0 /DStageDir=..\build\stage /DOutDir=..\dist ^
;       packaging\windows\jdrk-monitor.iss
;
; 说明：
;   · 后端是控制台程序，未实现 Windows SCM 接口，因此以「开机计划任务」托管
;     （见 tools\jdrk-service.ps1），安装时自动注册并启动。
;   · 配置与数据放在 %ProgramData%\JDRK（升级不覆盖配置，卸载不删数据）。
; ============================================================================

; ----------------------------------------------------------------------------
; 版本号与路径来源：build-installer.ps1 通过环境变量传入（JDRK_APP_VERSION /
;   JDRK_STAGE_DIR / JDRK_OUT_DIR），手工编译时用下面的默认值。
;   用环境变量 + GetEnv() 而不是 #include 或 iscc /D：前者受 ISPP 文件编码影响，
;   后者会被 PowerShell 的参数转义破坏含反斜杠的路径。
; ----------------------------------------------------------------------------
#ifndef AppVersion
  #if GetEnv("JDRK_APP_VERSION") != ""
    #define AppVersion GetEnv("JDRK_APP_VERSION")
  #else
    #define AppVersion "0.1.0"
  #endif
#endif
#ifndef StageDir
  #if GetEnv("JDRK_STAGE_DIR") != ""
    #define StageDir GetEnv("JDRK_STAGE_DIR")
  #else
    #define StageDir "..\build\stage"
  #endif
#endif
#ifndef OutDir
  #if GetEnv("JDRK_OUT_DIR") != ""
    #define OutDir GetEnv("JDRK_OUT_DIR")
  #else
    #define OutDir "..\dist"
  #endif
#endif

; WithUI：是否打包 Qt 上位机。build-installer.ps1 -SkipFrontend 时设为 "0"，
; 此时 [Components]/[Files]/[Icons]/[Run] 里所有 ui 相关条目都会被裁掉。
#ifndef WithUI
  #if GetEnv("JDRK_WITH_UI") != ""
    #define WithUI GetEnv("JDRK_WITH_UI")
  #else
    #define WithUI "1"
  #endif
#endif

#define AppNameCN "JDRK 水质监控平台"
#define AppPublisher "JDRK"
#define AppURL "https://github.com/Mubanjun/ModbusCompatibleMonitor"
#define UiExeName "jdrk-monitor-ui.exe"
#define BackendExeName "jdrk-monitor.exe"
#define ServiceScript "jdrk-service.ps1"

[Setup]
AppId={{7C2F5A61-9B3E-4D72-8A55-1E6D0C4B9F31}
AppName={#AppNameCN}
AppVersion={#AppVersion}
AppVerName={#AppNameCN} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}
AppUpdatesURL={#AppURL}
VersionInfoVersion={#AppVersion}
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppNameCN} 安装程序
VersionInfoProductName={#AppNameCN}
DefaultDirName={autopf}\JDRK Monitor
DefaultGroupName={#AppNameCN}
DisableProgramGroupPage=yes
LicenseFile=..\..\LICENSE
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir={#OutDir}
OutputBaseFilename=JDRK-Monitor-{#AppVersion}-setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName={#AppNameCN}
UninstallDisplayIcon={app}\ui\{#UiExeName}
SetupLogging=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Components]
; backend 为必装组件（Flags: fixed）；ui/docs 可取消勾选
Name: "backend"; Description: "后端服务（Modbus 采集 + SQLite 缓冲 + REST/WebSocket）"; Types: full compact custom; Flags: fixed
#if WithUI == "1"
Name: "ui";      Description: "Qt6 图形界面 jdrk-monitor-ui（可连接本机或远端后端）"; Types: full
#endif
Name: "docs";    Description: "文档与示例配置"; Types: full compact custom

[Tasks]
Name: "service"; Description: "注册为开机自启动服务（计划任务 · SYSTEM 权限）"; GroupDescription: "服务与网络："; Components: backend
Name: "firewall"; Description: "添加防火墙入站规则（允许 8790 端口被远程上位机访问）"; GroupDescription: "服务与网络："; Components: backend
#if WithUI == "1"
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加图标："; Components: ui
#endif

[Files]
; 后端 + 服务管理脚本
Source: "{#StageDir}\backend\{#BackendExeName}"; DestDir: "{app}\backend"; Components: backend; Flags: ignoreversion
Source: "{#StageDir}\tools\{#ServiceScript}"; DestDir: "{app}\tools"; Components: backend; Flags: ignoreversion
; 配置文件：仅首次安装写入，升级不覆盖，卸载不删除
Source: "{#StageDir}\backend\config.toml"; DestDir: "{commonappdata}\JDRK"; DestName: "config.toml"; Components: backend; Flags: onlyifdoesntexist uninsneveruninstall
; 前端（含 windeployqt 拷贝的 Qt 运行库与 QML 模块）
#if WithUI == "1"
Source: "{#StageDir}\ui\*"; DestDir: "{app}\ui"; Components: ui; Flags: ignoreversion recursesubdirs createallsubdirs
#endif
; 文档
Source: "{#StageDir}\docs\*"; DestDir: "{app}\docs"; Components: docs; Flags: ignoreversion recursesubdirs createallsubdirs

[Dirs]
Name: "{commonappdata}\JDRK"; Permissions: users-modify
Name: "{commonappdata}\JDRK\data"; Permissions: users-modify
Name: "{commonappdata}\JDRK\logs"; Permissions: users-modify

[Icons]
#if WithUI == "1"
Name: "{group}\{#AppNameCN}"; Filename: "{app}\ui\{#UiExeName}"; Components: ui
#endif
Name: "{group}\后端配置 config.toml"; Filename: "{commonappdata}\JDRK\config.toml"; Components: backend
Name: "{group}\后端日志目录"; Filename: "{commonappdata}\JDRK\logs"; Components: backend
Name: "{group}\后端服务状态"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\{#ServiceScript}"" -Action Status"; Components: backend
Name: "{group}\卸载 {#AppNameCN}"; Filename: "{uninstallexe}"
#if WithUI == "1"
Name: "{autodesktop}\{#AppNameCN}"; Filename: "{app}\ui\{#UiExeName}"; Components: ui; Tasks: desktopicon
#endif

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\{#ServiceScript}"" -Action Install -AppDir ""{app}"" -Config ""{commonappdata}\JDRK\config.toml"" -LogDir ""{commonappdata}\JDRK\logs"" -DataDir ""{commonappdata}\JDRK\data"" -Port 8790"; StatusMsg: "注册并启动 JDRK 后端服务…"; Flags: runhidden waituntilterminated; Components: backend; Tasks: service
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\{#ServiceScript}"" -Action Firewall -Port 8790"; StatusMsg: "添加防火墙入站规则…"; Flags: runhidden waituntilterminated; Components: backend; Tasks: firewall
#if WithUI == "1"
Filename: "{app}\ui\{#UiExeName}"; Description: "启动 {#AppNameCN}"; Flags: nowait postinstall skipifsilent; Components: ui
#endif

[UninstallRun]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\{#ServiceScript}"" -Action Uninstall -AppDir ""{app}"" -LogDir ""{commonappdata}\JDRK\logs"" -Port 8790"; Flags: runhidden waituntilterminated; RunOnceId: "StopAndUnregisterService"; Components: backend
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\tools\{#ServiceScript}"" -Action RemoveFirewall -Port 8790"; Flags: runhidden waituntilterminated; RunOnceId: "RemoveFirewallRule"; Components: backend

[UninstallDelete]
; 仅清理程序目录与运行日志；配置(config.toml)与采集数据(data)默认保留
Type: filesandordirs; Name: "{app}\ui"
Type: filesandordirs; Name: "{commonappdata}\JDRK\logs"
