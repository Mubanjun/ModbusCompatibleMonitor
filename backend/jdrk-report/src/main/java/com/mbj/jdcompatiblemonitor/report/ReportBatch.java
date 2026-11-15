/*
 * Copyright (c) 2026-present 仁科兼容环境监测平台 Authors. All Rights Reserved.
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

package com.mbj.jdcompatiblemonitor.report;

import java.util.List;

/**
 * 一次上报的批次（FR-BUF-02 定时批量上报）。
 *
 * @param ruleId     上报规则 ID
 * @param ruleName   上报规则名称
 * @param targetId   上报目标 ID
 * @param targetName 上报目标名称
 * @param portName   端口地址
 * @param batchTime  批次时间（毫秒时间戳）
 * @param items      采样数据
 */
public record ReportBatch(long ruleId, String ruleName, long targetId, String targetName,
    String portName, long batchTime, List<ReportItem> items) {

    public ReportBatch {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** 批次内数据条数。 */
    public int count() {
        return items.size();
    }
}
