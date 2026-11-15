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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 进程管理器配置解析与命令组装测试（FR-WD-01/07）。
 *
 * <p>重点保证两件事：旧单服务配置仍然可用；多服务配置的默认值与命令行切分不回归。</p>
 *
 * @author jdrk
 * @since 2026/11/5
 */
class ProcessManagerConfigTest {

    private static Map<String, String> base() {
        Map<String, String> values = new HashMap<>();
        values.put("app.jar", "/opt/jdrk/continew-admin.jar");
        values.put("app.args", "-Xmx1g --server.port=8000");
        values.put("heartbeat.url", "http://127.0.0.1:8000/monitor/watchdog/heartbeat");
        return values;
    }

    @Test
    @DisplayName("旧单服务配置等价为 backend 一个服务")
    void legacyConfigBecomesSingleBackend() {
        List<ServiceSpec> specs = ServiceSpec.parseAll(base());
        assertEquals(1, specs.size());
        ServiceSpec spec = specs.get(0);
        assertEquals(ServiceSpec.LEGACY_NAME, spec.name);
        assertEquals(ServiceSpec.TYPE_JAVA, spec.type);
        assertTrue(spec.autostart);
        assertTrue(spec.critical);
        assertEquals(180, spec.startTimeoutSeconds);
        assertEquals(120, spec.heartbeatTimeoutSeconds);
        assertEquals(5, spec.restartLimitPerHour);
    }

    @Test
    @DisplayName("多服务配置按序号排序且支持 command 类型")
    void multiServiceConfig() {
        Map<String, String> values = base();
        values.put("service.2.name", "worker");
        values.put("service.2.type", "command");
        values.put("service.2.command", "/opt/jdrk/worker.sh --loop");
        values.put("service.2.autostart", "false");
        values.put("service.2.restart.limit.per.hour", "10");
        values.put("service.1.name", "backend");
        values.put("service.1.critical", "false");
        List<ServiceSpec> specs = ServiceSpec.parseAll(values);
        assertEquals(2, specs.size());
        assertEquals("backend", specs.get(0).name);
        assertEquals("worker", specs.get(1).name);
        ServiceSpec worker = specs.get(1);
        assertEquals(ServiceSpec.TYPE_COMMAND, worker.type);
        assertFalse(worker.autostart);
        assertEquals(10, worker.restartLimitPerHour);
        assertFalse(specs.get(0).critical);
        assertEquals(List.of("/opt/jdrk/worker.sh", "--loop"), worker.buildCommand());
    }

    @Test
    @DisplayName("java 服务把 JVM 选项放在 -jar 之前，应用参数放在其后")
    void javaCommandSplitsJvmAndAppArgs() {
        List<ServiceSpec> specs = ServiceSpec.parseAll(base());
        List<String> command = specs.get(0).buildCommand();
        int jarIndex = command.indexOf("-jar");
        assertTrue(command.indexOf("-Xmx1g") < jarIndex, "JVM 选项必须在 -jar 之前");
        assertTrue(command.indexOf("--server.port=8000") > jarIndex, "应用参数必须在 -jar 之后");
        Path jar = Path.of(command.get(jarIndex + 1));
        assertTrue(jar.isAbsolute());
    }

    @Test
    @DisplayName("service.* 缺少 name 时明确报错，避免静默不托管")
    void missingNameFailsFast() {
        Map<String, String> values = base();
        values.put("service.1.type", "java");
        assertThrows(IllegalArgumentException.class, () -> ServiceSpec.parseAll(values));
    }

    @Test
    @DisplayName("控制面默认只监听回环并默认开启")
    void controlDefaults() throws Exception {
        WatchdogMain.Config config =
            WatchdogMain.Config.load(new String[] {"--config=/nonexistent"});
        assertTrue(config.controlEnabled);
        assertEquals("127.0.0.1", config.controlHost);
        assertEquals(18999, config.controlPort);
        assertEquals("", config.controlToken);
        assertEquals(1, config.services.size());
    }
}
