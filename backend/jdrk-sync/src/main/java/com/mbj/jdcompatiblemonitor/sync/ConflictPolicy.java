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

package com.mbj.jdcompatiblemonitor.sync;

/**
 * 新旧冲突策略（FR-SYNC-02，与 {@code biz_sync_task.conflict_policy} 取值一致）。
 */
public enum ConflictPolicy {

    /** 新数据优先（默认）：按记录时间判定，谁新保留谁 */
    NEWER_WINS("新数据优先"),

    /** 覆盖：一律以来源数据为准 */
    OVERWRITE("来源覆盖"),

    /** 跳过：目标已存在则保留目标 */
    SKIP("保留目标"),

    /** 人工确认：冲突挂起，等待人工处理 */
    MANUAL("人工确认");

    private final String label;

    ConflictPolicy(String label) {
        this.label = label;
    }

    /** 中文名称。 */
    public String label() {
        return label;
    }

    /**
     * 宽松解析
     *
     * @param value 字符串
     * @return 策略，无法识别返回 {@link #NEWER_WINS}
     */
    public static ConflictPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return NEWER_WINS;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEWER_WINS;
        }
    }
}
