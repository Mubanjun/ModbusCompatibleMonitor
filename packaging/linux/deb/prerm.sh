#!/bin/sh
# JDRK 水质监控平台 · Debian 卸载前置脚本（优先优雅停机以释放串口）
set -e

SVC="jdrk-monitor.service"
API="http://127.0.0.1:8790/api/shutdown"

case "$1" in
  remove|deconfigure)
    # 先请求后端优雅停机（关闭串口后退出），失败再交给 systemd
    if command -v curl >/dev/null 2>&1; then
      curl -fsS -m 3 -X POST "$API" >/dev/null 2>&1 || true
      sleep 1
    fi
    if command -v systemctl >/dev/null 2>&1; then
      systemctl stop "$SVC" >/dev/null 2>&1 || true
    fi
    ;;
  upgrade|failed-upgrade)
    # 升级时不主动停服务，由 postinst 统一 restart
    ;;
  *)
    ;;
esac

exit 0
