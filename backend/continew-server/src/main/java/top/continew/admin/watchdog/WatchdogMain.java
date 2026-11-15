/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.watchdog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 独立进程管理器（原「看门狗」）入口（FR-WD-01~07）。
 *
 * <p>与后端同包部署但完全独立运行：不依赖 Spring，只用 JDK 的 HTTP 客户端与进程 API。
 * 启动方式（Boot 胖包用 PropertiesLauncher 指定备用主类）：</p>
 *
 * <pre>
 * java -cp continew-admin.jar -Dloader.main=top.continew.admin.watchdog.WatchdogMain \
 *      org.springframework.boot.loader.launch.PropertiesLauncher --config=watchdog.properties
 * </pre>
 *
 * <p>职责：按 {@code service.<n>.*} 配置托管一组服务（后端与可选工作进程），周期探测心跳与进程存活，
 * 异常时按策略拉起并限流；同时提供 {@code /pm/*} 控制面（状态查询、启停、日志尾部）。
 * 重启与限流事件仍写入 {@code watchdog/restarts.jsonl}，由后端落库到 {@code biz_watchdog_log}。</p>
 *
 * @author jdrk
 * @since 2026/9/24
 */
public final class WatchdogMain {

    /** 进程不存在 */
    static final String EVENT_CRASH = "CRASH";

    /** 心跳超时（假死） */
    static final String EVENT_TIMEOUT = "HEARTBEAT_TIMEOUT";

    /** 触发重启限流 */
    static final String EVENT_LIMIT = "REBOOT_LIMIT";

    /**
     * JVM 自身选项的前缀：用于把 args 拆成「JVM 选项」与「Spring 应用参数」两段。
     *
     * <p>这两段必须分别放在 -jar 之前 / 之后，否则形如 --spring.datasource.url=... 的应用参数
     * 会被 JVM 当成自己的选项并直接报 "Unrecognized option"（审计缺陷 37）。</p>
     */
    private static final List<String> JVM_OPTION_PREFIXES = List.of("-X", "-D", "-XX", "-agent",
        "-javaagent", "-server", "-client", "-verbose", "-ea", "-enableassertions", "-da",
        "-disableassertions", "-cp", "-classpath", "--add-opens", "--add-exports", "--add-reads",
        "--patch-module", "--enable-preview", "--enable-native-access", "--illegal-native-access",
        "--module-path", "--upgrade-module-path", "--limit-modules", "--class-path", "--module");

    private WatchdogMain() {
    }

    /**
     * 管理器入口
     *
     * @param args 命令行参数（--key=value 或 --config=path）
     * @throws Exception 运行异常
     */
    public static void main(String[] args) throws Exception {
        Config config = Config.load(args);
        ProcessManager manager = new ProcessManager(config.services, config.dir, config.logFile,
            config.checkIntervalSeconds);
        ControlServer control = null;
        if (config.controlEnabled) {
            control = new ControlServer(manager, config.controlToken, config.controlLogLines);
            ControlServer started = control;
            try {
                started.start(config.controlHost, config.controlPort);
            } catch (IOException e) {
                manager.log("控制面启动失败（不影响进程守护）：" + e.getMessage());
                control = null;
            }
        }
        ControlServer running = control;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running != null) {
                running.stop();
            }
            manager.shutdown();
        }, "jdrk-pm-shutdown"));
        manager.run();
    }

    /**
     * 解析启动所用的 java 命令（NFR-COMPAT-03：随包携带精简运行时）
     *
     * <p>解析顺序：显式配置（app.java 为非默认值）→ 与应用包同级/上级目录下的 runtime/bin/java
     * → PATH 上的 java。发布包 dist/ 里 jar 与 runtime/ 同级，因此现场机不装 JDK 也能启动。</p>
     *
     * @param configured 配置项 app.java 的原值，可为 null
     * @param appJar 后端应用包路径
     * @return java 可执行文件路径；找不到随包运行时时回退为 "java"
     */
    static String resolveJava(String configured, String appJar) {
        String value = configured == null ? "" : configured.trim();
        if (!value.isEmpty() && !"java".equalsIgnoreCase(value)) {
            return value;
        }
        Path bundled = bundledJava(appJar);
        if (bundled != null) {
            return bundled.toString();
        }
        return value.isEmpty() ? "java" : value;
    }

    /**
     * 判断某个 args 片段是 JVM 选项还是 Spring 应用参数
     *
     * @param item args 按空格切分后的单个片段
     * @return true 表示 JVM 选项（需要放在 -jar 之前）
     */
    static boolean isJvmOption(String item) {
        for (String prefix : JVM_OPTION_PREFIXES) {
            if (item.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 探测随包携带的精简运行时（NFR-COMPAT-03）
     *
     * @param appJar 后端应用包路径
     * @return runtime/bin/java 的绝对路径；未随包携带时返回 null
     */
    static Path bundledJava(String appJar) {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("windows")
            ? "java.exe" : "java";
        List<Path> roots = new ArrayList<>();
        try {
            Path parent = Paths.get(appJar).toAbsolutePath().normalize().getParent();
            if (parent != null) {
                roots.add(parent.resolve("runtime"));
                if (parent.getParent() != null) {
                    // 兼容「jar 放在 lib/ 子目录」的分发布局
                    roots.add(parent.getParent().resolve("runtime"));
                }
            }
        } catch (Exception ignored) {
            // 应用包路径非法时，仅保留工作目录候选
        }
        roots.add(Paths.get("runtime").toAbsolutePath().normalize());
        for (Path root : roots) {
            Path candidate = root.resolve("bin").resolve(exe);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 管理器运行参数
     */
    static final class Config {

        final List<ServiceSpec> services;

        final int checkIntervalSeconds;

        final Path dir;

        final Path logFile;

        final boolean controlEnabled;

        final String controlHost;

        final int controlPort;

        final String controlToken;

        final int controlLogLines;

        private Config(Map<String, String> values) {
            this.services = ServiceSpec.parseAll(values);
            this.checkIntervalSeconds = parseInt(values, "check.interval.seconds", 15);
            this.dir = Paths.get(values.getOrDefault("watchdog.dir", "./watchdog")).toAbsolutePath()
                .normalize();
            this.logFile = Paths.get(values.getOrDefault("watchdog.log", "./logs/watchdog.log"))
                .toAbsolutePath().normalize();
            this.controlEnabled =
                Boolean.parseBoolean(values.getOrDefault("control.enabled", "true"));
            this.controlHost = values.getOrDefault("control.host", "127.0.0.1");
            this.controlPort = parseInt(values, "control.port", 18999);
            this.controlToken = values.getOrDefault("control.token", "");
            this.controlLogLines = parseInt(values, "control.log.lines", 200);
        }

        static Config load(String[] args) throws IOException {
            Map<String, String> values = new HashMap<>();
            List<String> rest = new ArrayList<>();
            Path configFile = null;
            for (String arg : args) {
                if (arg.startsWith("--config=")) {
                    configFile = Paths.get(arg.substring("--config=".length()));
                } else if (arg.startsWith("--")) {
                    rest.add(arg.substring(2));
                }
            }
            if (configFile != null && Files.isRegularFile(configFile)) {
                Properties properties = new Properties();
                try (InputStream in = Files.newInputStream(configFile)) {
                    properties.load(in);
                }
                for (String name : properties.stringPropertyNames()) {
                    values.put(name, properties.getProperty(name));
                }
            }
            for (String item : rest) {
                int index = item.indexOf('=');
                if (index > 0) {
                    values.put(item.substring(0, index), item.substring(index + 1));
                }
            }
            return new Config(values);
        }

        private static int parseInt(Map<String, String> values, String key, int defaultValue) {
            String raw = values.get(key);
            if (raw == null || raw.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    /**
     * 心跳响应（只取管理器关心的字段）
     *
     * <p>必须忽略未知字段：后端心跳里还有 processName/timestamp 等，Jackson 默认遇到未知字段会抛异常，
     * 曾导致心跳解析失败、看门狗误判后端不存在并重复拉起。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Heartbeat {

        /** 进程号 */
        public Long pid;

        /** 运行时长（毫秒） */
        public Long uptimeMs;

        /** 是否健康 */
        public Boolean healthy;

        /** 检查项 */
        public List<Check> checks = new ArrayList<>();

        /**
         * 检查项摘要
         *
         * @return 文本
         */
        String summary() {
            if (checks == null || checks.isEmpty()) {
                return "（无检查项）";
            }
            StringBuilder builder = new StringBuilder();
            for (Check check : checks) {
                if (builder.length() > 0) {
                    builder.append('；');
                }
                builder.append(check.name).append(Boolean.TRUE.equals(check.ok) ? "=OK" : "=FAIL");
            }
            return builder.toString();
        }
    }

    /**
     * 心跳中的单个检查项
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Check {

        /** 名称 */
        public String name;

        /** 是否通过 */
        public Boolean ok;

        /** 说明 */
        public String detail;
    }
}
