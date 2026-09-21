#!/bin/sh
# JDRK 水质监控平台 · Debian 卸载后置脚本
set -e

SVC="jdrk-monitor.service"
DATA_DIR="/var/lib/jdrk-monitor"
LOG_DIR="/var/log/jdrk-monitor"

if command -v systemctl >/dev/null 2>&1; then
  if [ "$1" = "purge" ]; then
    systemctl disable "$SVC" >/dev/null 2>&1 || true
  fi
  systemctl daemon-reload >/dev/null 2>&1 || true
fi

case "$1" in
  purge)
    # purge 才会删除采集数据；apt remove 会保留（建议先备份 monitor.db）
    rm -rf "$DATA_DIR" "$LOG_DIR"
    echo "jdrk-monitor：已删除数据目录 $DATA_DIR 与日志目录 $LOG_DIR"
    echo "  系统用户 jdrk-monitor 保留，如需彻底清理：deluser jdrk-monitor && delgroup jdrk-monitor"
    ;;
  remove|upgrade|failed-upgrade|abort-install|abort-upgrade|disappear)
    ;;
  *)
    ;;
esac

exit 0
