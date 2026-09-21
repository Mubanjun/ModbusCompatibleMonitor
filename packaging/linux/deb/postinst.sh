#!/bin/sh
# JDRK 水质监控平台 · Debian 安装后置脚本（创建用户、数据目录、注册并启动服务）
set -e

PKG_NAME="jdrk-monitor"
SVC="$${PKG_NAME}.service"
SVC_USER="jdrk-monitor"
DATA_DIR="/var/lib/$${PKG_NAME}"
LOG_DIR="/var/log/$${PKG_NAME}"
CONF="/etc/$${PKG_NAME}/config.toml"

case "$1" in
  configure)
    # 1) 系统用户与组（无登录权限）
    if ! getent group "$SVC_USER" >/dev/null 2>&1; then
      if command -v addgroup >/dev/null 2>&1; then
        addgroup --system "$SVC_USER" >/dev/null 2>&1 || true
      else
        groupadd --system "$SVC_USER" >/dev/null 2>&1 || true
      fi
    fi
    if ! getent passwd "$SVC_USER" >/dev/null 2>&1; then
      if command -v adduser >/dev/null 2>&1; then
        adduser --system --no-create-home --ingroup "$SVC_USER" \
          --home "$DATA_DIR" --shell /usr/sbin/nologin "$SVC_USER" >/dev/null 2>&1 || true
      else
        useradd --system --home-dir "$DATA_DIR" --shell /sbin/nologin \
          --gid "$SVC_USER" "$SVC_USER" >/dev/null 2>&1 || true
      fi
    fi

    # 2) 串口访问权限：把服务用户加入发行版定义的串口组
    #    （Debian/Ubuntu 用 dialout，RHEL/openSUSE 用 uucp 或 dialout）
    for grp in dialout uucp tty; do
      if getent group "$grp" >/dev/null 2>&1; then
        usermod -aG "$grp" "$SVC_USER" >/dev/null 2>&1 || true
      fi
    done

    # 3) 数据目录（SQLite 缓冲等）与日志目录
    install -d -o "$SVC_USER" -g "$SVC_USER" -m 0750 "$DATA_DIR"
    install -d -o "$SVC_USER" -g "$SVC_USER" -m 0750 "$LOG_DIR"

    # 4) 注册并启动服务（容器/chroot 内无 systemd 时跳过）
    if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
      systemctl daemon-reload >/dev/null 2>&1 || true
      systemctl enable "$SVC" >/dev/null 2>&1 || true
      if systemctl is-active --quiet "$SVC"; then
        systemctl restart "$SVC" >/dev/null 2>&1 || true
      else
        systemctl start "$SVC" >/dev/null 2>&1 || true
      fi
    fi

    echo "jdrk-monitor 已安装。"
    echo "  配置文件：$CONF（修改后执行 systemctl restart jdrk-monitor）"
    echo "  查看状态：systemctl status jdrk-monitor"
    echo "  串口权限：串口设备需为 root:dialout 0660，且服务用户已在 dialout 组"
    ;;
  abort-upgrade|abort-remove|abort-deconfigure)
    ;;
  *)
    ;;
esac

exit 0
