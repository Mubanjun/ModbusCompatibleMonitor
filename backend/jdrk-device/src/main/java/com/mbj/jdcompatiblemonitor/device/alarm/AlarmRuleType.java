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

package com.mbj.jdcompatiblemonitor.device.alarm;

/**
 * 报警规则类型（FR-ALM-01/06，与 {@code biz_alarm_rule.rule_type} 取值一致）。
 */
public enum AlarmRuleType {

    /** 阈值报警：高于上限或低于下限 */
    RANGE("阈值报警"),

    /** 偏差报警：相邻两次采样的跳变超过允许偏差 */
    DEVIATION("偏差报警"),

    /** 变化率报警：单位时间变化量超过上限 */
    RATE("变化率报警"),

    /** 开关量报警：闭合或断开 */
    SWITCH("开关量报警");

    private final String label;

    AlarmRuleType(String label) {
        this.label = label;
    }

    /** 中文名称。 */
    public String label() {
        return label;
    }

    /**
     * 宽松解析规则类型
     *
     * @param value 字符串
     * @return 规则类型，无法识别返回 {@code null}
     */
    public static AlarmRuleType parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
