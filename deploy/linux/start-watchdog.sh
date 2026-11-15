#!/bin/sh
# 启动独立看门狗（FR-WD-01）——Linux 版，由 systemd 引导（FR-SYS-07）。
# 看门狗与后端同包但独立运行：不依赖 Spring，只探测心跳并按策略拉起后端。
# 后端由看门狗以子进程方式拉起，避免两套进程管理逻辑（FR-QT-02/FR-SYS-05）。
set -e
JDRK_HOME=${JDRK_HOME:-/home/ubt/jdrk}
JAVA=${JDRK_JAVA:-java}
JAR=${JDRK_JAR:-$JDRK_HOME/continew-admin.jar}
CONFIG=${JDRK_WATCHDOG_CONFIG:-$JDRK_HOME/watchdog.properties}
cd "$JDRK_HOME"
echo "启动看门狗：java=$JAVA jar=$JAR config=$CONFIG"
exec "$JAVA" -cp "$JAR" -Dloader.main=top.continew.admin.watchdog.WatchdogMain \
  org.springframework.boot.loader.launch.PropertiesLauncher "--config=$CONFIG"
