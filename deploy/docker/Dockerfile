# JDRK 后端镜像（Linux ARM64 / x86_64 通用，Java 21）
# 构建上下文需包含 continew-admin.jar（见 deploy/docker/README.md）
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=-Xmx1g

WORKDIR /app
COPY continew-admin.jar /app/continew-admin.jar

EXPOSE 8000
# 具体数据源/Redis 参数由 compose 的 command 传入，避免把口令写进镜像
ENTRYPOINT ["java", "-jar", "/app/continew-admin.jar"]
