# 重新启用 MacroSilicon USB Serial（需管理员）
$id = 'USB\VID_345F&PID_3020\3M0a01J3'
Write-Host "enable-device $id"
pnputil /enable-device "$id"
Start-Sleep -Seconds 3
Write-Host "done"
