#!/bin/sh
# 仁科兼容环境监测平台 — Linux 后端启动脚本（直接启动的备用方式）
# 注意：V1.1 起 systemd 默认引导 start-watchdog.sh（看门狗再拉起后端）；本脚本仅供手工/直连调试。
# FR-SYS-07：服务化 + 开机自启；NFR-COMPAT-01：Linux 运行时验证
# 安装：见 docs/开机自启与服务化.md「二之三、Linux（systemd）实测」
JAVA=${JAVA:-/usr/bin/java}
JAR=${JAR:-/home/ubt/jdrk/continew-admin.jar}
URL=${DB_URL:-'jdbc:mysql://127.0.0.1:3306/continew_admin?serverTimezone=Asia/Shanghai&useSSL=false&useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true'}
exec "$JAVA" -Xmx1g -jar "$JAR" \
  "--spring.datasource.url=$URL" \
  --spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver \
  --server.port=8000 \
  --logging.level.top.continew.admin=INFO
