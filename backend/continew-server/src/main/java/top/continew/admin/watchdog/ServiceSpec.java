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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 被托管服务（进程）的定义（FR-WD-01/07）。
 *
 * <p>来源有两种，解析后统一成同一种结构：</p>
 * <ul>
 *   <li><b>多服务模式</b>：{@code service.<n>.name=...} 一组键，n 为序号；</li>
 *   <li><b>旧单服务模式</b>：只有 {@code app.jar/app.args/heartbeat.url} 等老键，
 *       自动等价为名为 {@code backend} 的一个服务，保证既有部署配置无需修改。</li>
 * </ul>
 *
 * @author jdrk
 * @since 2026/11/5
 */
public final class ServiceSpec {

    /** 服务类型：JVM 应用包 */
    static final String TYPE_JAVA = "java";

    /** 服务类型：任意可执行命令 */
    static final String TYPE_COMMAND = "command";

    /** 单服务模式下自动生成的服务名 */
    static final String LEGACY_NAME = "backend";

    private static final String PREFIX = "service.";

    final String name;

    final String type;

    final String jar;

    final String javaCommand;

    final String command;

    final String args;

    final Path dir;

    final Path logFile;

    final String heartbeatUrl;

    final String heartbeatToken;

    final int heartbeatTimeoutSeconds;

    final int startTimeoutSeconds;

    final int restartLimitPerHour;

    final boolean autostart;

    final boolean critical;

    private ServiceSpec(Map<String, String> values, String prefix, Map<String, String> global) {
        this.name = required(values, prefix + "name");
        this.type = values.getOrDefault(prefix + "type", TYPE_JAVA).trim().toLowerCase();
        this.jar = values.getOrDefault(prefix + "jar", global.getOrDefault("app.jar", ""));
        this.javaCommand = WatchdogMain.resolveJava(values.getOrDefault(prefix + "java", global
            .getOrDefault("app.java", "java")), this.jar);
        this.command = values.getOrDefault(prefix + "command", "").trim();
        this.args = values.getOrDefault(prefix + "args", global.getOrDefault("app.args", ""));
        String dir = values.getOrDefault(prefix + "dir", global.getOrDefault("app.dir", "."));
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
        String log = values.getOrDefault(prefix + "log", global.getOrDefault("app.log",
            "./logs/continew-admin.out.log"));
        this.logFile = Paths.get(log).toAbsolutePath().normalize();
        this.heartbeatUrl = values.getOrDefault(prefix + "heartbeat.url", global.getOrDefault(
            "heartbeat.url", "")).trim();
        this.heartbeatToken = values.getOrDefault(prefix + "heartbeat.token", global.getOrDefault(
            "heartbeat.token", "")).trim();
        this.heartbeatTimeoutSeconds =
            parseInt(values, prefix + "heartbeat.timeout.seconds", global,
                "heartbeat.timeout.seconds", 120);
        this.startTimeoutSeconds = parseInt(values, prefix + "start.timeout.seconds", global,
            "start.timeout.seconds", 180);
        this.restartLimitPerHour = parseInt(values, prefix + "restart.limit.per.hour", global,
            "restart.limit.per.hour", 5);
        this.autostart = Boolean.parseBoolean(values.getOrDefault(prefix + "autostart", "true"));
        this.critical = Boolean.parseBoolean(values.getOrDefault(prefix + "critical", "true"));
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填配置项：" + key);
        }
        return value.trim();
    }

    private static int parseInt(Map<String, String> values, String key, Map<String, String> global,
        String globalKey, int defaultValue) {
        String raw = values.getOrDefault(key, global.get(globalKey));
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析全部服务定义
     *
     * @param values 合并后的配置（文件 + 命令行覆盖）
     * @return 按配置顺序排列的服务定义；旧配置返回单元素列表
     */
    static List<ServiceSpec> parseAll(Map<String, String> values) {
        Set<Integer> indexes = new TreeSet<>();
        for (String key : values.keySet()) {
            if (!key.startsWith(PREFIX)) {
                continue;
            }
            int dot = key.indexOf('.', PREFIX.length());
            if (dot < 0) {
                continue;
            }
            try {
                indexes.add(Integer.parseInt(key.substring(PREFIX.length(), dot)));
            } catch (NumberFormatException e) {
                // 不是序号段，忽略
            }
        }
        List<ServiceSpec> result = new ArrayList<>();
        if (indexes.isEmpty()) {
            // 旧单服务模式：与历史行为完全一致
            Map<String, String> legacy = new LinkedHashMap<>();
            legacy.put(prefixOf(1) + "name", LEGACY_NAME);
            legacy.put(prefixOf(1) + "type", TYPE_JAVA);
            result.add(new ServiceSpec(legacy, prefixOf(1), values));
            return result;
        }
        for (Integer index : indexes) {
            String prefix = prefixOf(index);
            if (values.get(prefix + "name") == null) {
                continue;
            }
            result.add(new ServiceSpec(values, prefix, values));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("配置了 service.* 但没有任何 service.<n>.name");
        }
        return result;
    }

    private static String prefixOf(int index) {
        return PREFIX + index + ".";
    }

    /**
     * 组装该服务的启动命令
     *
     * @return 命令行参数列表
     */
    List<String> buildCommand() {
        List<String> line = new ArrayList<>();
        if (TYPE_COMMAND.equals(this.type)) {
            if (this.command.isBlank()) {
                throw new IllegalStateException("服务 " + this.name + " 为 command 类型但未配置 command");
            }
            for (String item : this.command.split(" ")) {
                if (!item.isBlank()) {
                    line.add(item);
                }
            }
            return line;
        }
        // JVM 选项要在 -jar 之前，Spring 应用参数要在 -jar 之后（审计缺陷 37）
        List<String> jvmOptions = new ArrayList<>();
        List<String> applicationArgs = new ArrayList<>();
        for (String item : this.args.split(" ")) {
            if (item.isBlank()) {
                continue;
            }
            if (WatchdogMain.isJvmOption(item)) {
                jvmOptions.add(item);
            } else {
                applicationArgs.add(item);
            }
        }
        line.add(this.javaCommand);
        line.addAll(jvmOptions);
        line.add("-jar");
        line.add(Paths.get(this.jar).toAbsolutePath().normalize().toString());
        line.addAll(applicationArgs);
        return line;
    }
}
