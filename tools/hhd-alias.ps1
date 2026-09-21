# 用 HHD rspcli 把 COM3 映射/路由到 COM1（需管理员）
$dir = 'C:\Program Files\HHD Software\Virtual Serial Port Tools'
$log = 'D:\EXPPROJECT\JDRK-MonitorPlatform\.tmp\hhd-alias.log'
"=== create alias COM1 -> COM3 ===" | Out-File -Encoding utf8 $log
& "$dir\rspcli.exe" -create alias --local-port 1 --alias-port 3 *>> $log
"=== list alias ===" | Out-File -Append -Encoding utf8 $log
& "$dir\rspcli.exe" -list alias *>> $log
"=== list bridge ===" | Out-File -Append -Encoding utf8 $log
& "$dir\rspcli.exe" -list bridge *>> $log
Add-Content -Encoding utf8 $log "=== done ==="
