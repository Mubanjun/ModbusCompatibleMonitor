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

import com.mbj.jdcompatiblemonitor.report.ReportFormat;
import com.mbj.jdcompatiblemonitor.report.TransformSpec;

/**
 * 上报目标的可执行快照（FR-RPT-01/02/03/06）
 *
 * @param id                目标 ID
 * @param name              目标名称
 * @param targetType        目标类型（MQTT / DB / HTTP）
 * @param dbType            数据库方言
 * @param format            报文格式
 * @param qos               MQTT QoS
 * @param retain            MQTT 保留标志
 * @param retryCount        失败重试次数
 * @param retryIntervalMs   重试间隔（毫秒）
 * @param timeoutMs         超时（毫秒）
 * @param configJson        连接参数
 * @param transform         字段变换规则
 * @param templateContent   自定义报文模板
 * @author jdrk
 * @since 2026/9/23
 */
public record ReportTargetSpec(long id, String name, String targetType, String dbType,
    ReportFormat format, int qos, boolean retain, int retryCount, int retryIntervalMs,
    int timeoutMs,
    String configJson, TransformSpec transform, String templateContent) {

    /** 是否为 MQTT 目标。 */
    public boolean mqtt() {
        return "MQTT".equalsIgnoreCase(targetType);
    }

    /** 是否为数据库目标。 */
    public boolean database() {
        return "DB".equalsIgnoreCase(targetType);
    }

    /** 是否为 HTTP 目标。 */
    public boolean http() {
        return "HTTP".equalsIgnoreCase(targetType);
    }
}
