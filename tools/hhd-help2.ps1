# 以 UTF-8 落盘 rspcli 帮助（需管理员）
$dir = 'C:\Program Files\HHD Software\Virtual Serial Port Tools'
$out = 'D:\EXPPROJECT\JDRK-MonitorPlatform\.tmp\hhd-help-utf8.txt'
"=== rspcli --help ===" | Out-File -Encoding utf8 $out
& "$dir\rspcli.exe" --help 2>&1 | Out-File -Append -Encoding utf8 $out
"=== rspcli -list bridge ===" | Out-File -Append -Encoding utf8 $out
& "$dir\rspcli.exe" -list bridge 2>&1 | Out-File -Append -Encoding utf8 $out
Add-Content -Encoding utf8 $out "=== done ==="
