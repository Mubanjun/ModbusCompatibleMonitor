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

import java.nio.charset.StandardCharsets;

/**
 * MQTT 主题规范化（缺陷 61，FR-RPT-01）。
 *
 * <p>主题模板会把设备名等运行期取值原样拼进主题，而 MQTT 3.1.1 规定 <b>发布</b>主题里不得出现
 * 通配符 {@code #} / {@code +}（它们是订阅侧语法）。现场设备名常带 {@code #}（如「现场-#1」），
 * 直接拼接会让 Paho 抛 {@code IllegalArgumentException: The topic name MUST NOT contain any
 * wildcard characters (#+)}，导致该规则的 MQTT 上报全部进死信、通道实际不可用。</p>
 *
 * <p>两类处置：</p>
 * <ul>
 *   <li>{@link #validateTemplate(String)}：保存上报目标时预校验，把「模板里写死了通配符」这种
 *   配置错误挡在保存阶段（运行期才暴露会让现场以为功能没做）。</li>
 *   <li>{@link #normalize(String)}：发布前的最后一道防线，把渲染结果里的 {@code #} / {@code +}
 *   逐层替换为 {@code _}，让含特殊字符的设备名不再让整条通道失效。</li>
 * </ul>
 *
 * @author jdrk
 * @since 2026/10/1
 */
public final class MqttTopics {

    /**
     * 主题默认值，与 {@code config.getStr("topic", "jdrk/data")} 保持一致
     */
    public static final String DEFAULT_TOPIC = "jdrk/data";

    /**
     * 通配符替换字符
     */
    public static final char REPLACEMENT = '_';

    /**
     * MQTT 3.1.1 规定主题名最大 65535 个 UTF-8 字节
     */
    public static final int MAX_TOPIC_BYTES = 65535;

    private MqttTopics() {
    }

    /**
     * 规范化待发布的 MQTT 主题
     *
     * <p>规则：{@code #} 与 {@code +} 替换为 {@code _}；去除 {@code U+0000}（MQTT 禁止）；
     * 空主题与超长主题给出明确错误而不是让 Paho 抛晦涩的运行时异常。该方法幂等。</p>
     *
     * @param topic 待发布主题（可为 null）
     * @return 规范化后的主题
     */
    public static String normalize(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalStateException("MQTT 主题为空：请检查上报目标配置里的 topic 模板");
        }
        StringBuilder builder = new StringBuilder(topic.length());
        for (int i = 0; i < topic.length(); i++) {
            char ch = topic.charAt(i);
            if (ch == '#' || ch == '+') {
                builder.append(REPLACEMENT);
            } else if (ch != '\u0000') {
                builder.append(ch);
            }
        }
        String normalized = builder.toString();
        int bytes = normalized.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_TOPIC_BYTES) {
            throw new IllegalStateException("MQTT 主题过长：" + bytes + " 字节，上限 " + MAX_TOPIC_BYTES
                + " 字节（主题=" + normalized.substring(0, Math.min(120, normalized.length()))
                + "…）");
        }
        return normalized;
    }

    /**
     * 校验上报目标保存时的主题模板
     *
     * <p>{@code ${变量}} 占位符本身允许（其取值在发布时由 {@link #normalize(String)} 兜底），
     * 但占位符之外出现字面 {@code #} / {@code +} 属于配置错误，必须挡在保存阶段。</p>
     *
     * @param topicTemplate 主题模板
     */
    public static void validateTemplate(String topicTemplate) {
        if (topicTemplate == null || topicTemplate.isBlank()) {
            throw new IllegalArgumentException("MQTT 主题模板不能为空");
        }
        int index = 0;
        while (index < topicTemplate.length()) {
            int start = topicTemplate.indexOf("${", index);
            int end = start < 0 ? topicTemplate.length() : start;
            for (int i = index; i < end; i++) {
                char ch = topicTemplate.charAt(i);
                if (ch == '#' || ch == '+') {
                    throw new IllegalArgumentException(
                        "MQTT 主题模板不能包含通配符 # / +（设备名等变量会在发布时自动把 # 与 + 规范化为 "
                            + REPLACEMENT + "）");
                }
            }
            if (start < 0) {
                break;
            }
            int close = topicTemplate.indexOf('}', start + 2);
            if (close < 0) {
                // 未闭合的 "${" 会被模板渲染原样输出，其后内容按字面量继续校验
                index = start + 2;
                continue;
            }
            index = close + 1;
        }
    }
}
