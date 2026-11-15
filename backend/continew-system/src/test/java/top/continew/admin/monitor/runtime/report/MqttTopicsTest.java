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

package top.continew.admin.monitor.runtime.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MQTT 主题规范化的单元测试（缺陷 61 回归，FR-RPT-01）。
 *
 * <p>锁死「设备名含 {@code #} / {@code +} 时不得让整条 MQTT 通道失效」这一行为，
 * 避免以后再退回「模板原样拼接 → Paho 抛通配符非法」的老路。</p>
 *
 * @author jdrk
 * @since 2026/10/1
 */
class MqttTopicsTest {

    @Test
    void normalizeShouldReplaceWildcardsPerLevel() {
        // 现场真实设备名「现场-#1」是缺陷 61 的原始复现输入
        assertEquals("jdrk/现场-_1/1", MqttTopics.normalize("jdrk/现场-#1/1"));
        assertEquals("jdrk/a_b/2", MqttTopics.normalize("jdrk/a+b/2"));
        assertEquals("jdrk/_mixed_/3", MqttTopics.normalize("jdrk/#mixed+/3"));
    }

    @Test
    void normalizeShouldKeepLegalTopicsAndBeIdempotent() {
        assertEquals("jdrk/1 号站/data", MqttTopics.normalize("jdrk/1 号站/data"));
        assertEquals("jdrk/data", MqttTopics.normalize(MqttTopics.DEFAULT_TOPIC));
        String once = MqttTopics.normalize("jdrk/现场-#1/+");
        assertEquals(once, MqttTopics.normalize(once));
        assertFalse(once.contains("#"));
        assertFalse(once.contains("+"));
    }

    @Test
    void normalizeShouldStripNul() {
        assertEquals("jdrk/a", MqttTopics.normalize("jdrk/a\u0000"));
    }

    @Test
    void normalizeShouldRejectBlankTopic() {
        assertThrows(IllegalStateException.class, () -> MqttTopics.normalize(null));
        assertThrows(IllegalStateException.class, () -> MqttTopics.normalize(""));
        assertThrows(IllegalStateException.class, () -> MqttTopics.normalize("   "));
    }

    @Test
    void normalizeShouldRejectOversizeTopic() {
        String oversize = "j".repeat(MqttTopics.MAX_TOPIC_BYTES + 1);
        assertThrows(IllegalStateException.class, () -> MqttTopics.normalize(oversize));
    }

    @Test
    void validateTemplateShouldAllowPlaceholders() {
        // 验收脚本与现场都在用的模板
        MqttTopics.validateTemplate("jdrk/${deviceName}/${nodeId}");
        MqttTopics.validateTemplate(MqttTopics.DEFAULT_TOPIC);
        MqttTopics.validateTemplate("jdrk/${unknown}/x");
        // 未闭合占位按字面量继续校验，其中不含通配符即放行
        MqttTopics.validateTemplate("jdrk/${name");
    }

    @Test
    void validateTemplateShouldRejectLiteralWildcards() {
        assertThrows(IllegalArgumentException.class, () -> MqttTopics.validateTemplate("jdrk/#"));
        assertThrows(IllegalArgumentException.class,
            () -> MqttTopics.validateTemplate("jdrk/+/data"));
        assertThrows(IllegalArgumentException.class,
            () -> MqttTopics.validateTemplate("jdrk/x#${nodeId}"));
        assertThrows(IllegalArgumentException.class,
            () -> MqttTopics.validateTemplate("jdrk/${name#"));
        assertThrows(IllegalArgumentException.class, () -> MqttTopics.validateTemplate(null));
        assertThrows(IllegalArgumentException.class, () -> MqttTopics.validateTemplate(""));
        assertThrows(IllegalArgumentException.class, () -> MqttTopics.validateTemplate("   "));
    }
}
