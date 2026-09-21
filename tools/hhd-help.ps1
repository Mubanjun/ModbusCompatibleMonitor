# 取 rspcli 帮助与当前桥列表（需管理员）
$dir = 'C:\Program Files\HHD Software\Virtual Serial Port Tools'
$log = 'D:\EXPPROJECT\JDRK-MonitorPlatform\.tmp\hhd-help.log'
"=== rspcli --help ===" | Out-File -Encoding utf8 $log
& "$dir\rspcli.exe" --help *>> $log
"=== rspcli -list bridge ===" | Out-File -Append -Encoding utf8 $log
& "$dir\rspcli.exe" -list bridge *>> $log
"=== done ===" | Out-File -Append -Encoding utf8 $log
