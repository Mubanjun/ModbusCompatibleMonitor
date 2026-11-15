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

import com.mbj.jdcompatiblemonitor.report.ReportBatch;

/**
 * 一次上报传输的输入（FR-RPT-01/02/03/07）
 *
 * @param target  上报目标
 * @param topic   MQTT 主题（已渲染），非 MQTT 目标为 null
 * @param batch   数据批次（数据库目标据此生成行）
 * @param payload 已格式化的报文（MQTT / HTTP 目标据此发送）
 * @author jdrk
 * @since 2026/9/23
 */
public record ReportTransmission(ReportTargetSpec target, String topic, ReportBatch batch,
    String payload) {
}
