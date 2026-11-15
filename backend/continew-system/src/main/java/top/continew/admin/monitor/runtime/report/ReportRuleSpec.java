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

/**
 * 上报规则的可执行快照（FR-RPT-04：多目标并行）
 *
 * @param id       规则 ID
 * @param name     规则名称
 * @param portId   限定端口 ID，为空表示不限
 * @param deviceId 限定设备 ID，为空表示不限
 * @param intervalMs 上报间隔（毫秒）
 * @param target   上报目标
 * @author jdrk
 * @since 2026/9/23
 */
public record ReportRuleSpec(long id, String name, Long portId, Long deviceId, int intervalMs,
    ReportTargetSpec target) {

    public ReportRuleSpec {
        intervalMs = Math.max(intervalMs, 1000);
    }

    /**
     * 判断规则是否适用于某个采样点
     *
     * @param samplePortId   端口 ID
     * @param sampleDeviceId 设备 ID
     * @return 适用返回 true
     */
    public boolean matches(long samplePortId, long sampleDeviceId) {
        if (portId != null && portId != samplePortId) {
            return false;
        }
        return deviceId == null || deviceId == sampleDeviceId;
    }
}
