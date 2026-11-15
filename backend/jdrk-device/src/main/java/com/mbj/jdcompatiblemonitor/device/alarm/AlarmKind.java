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
 * 报警种类（用于生成 {@code biz_alarm_record.alarm_type} 与提示语）。
 */
public enum AlarmKind {

    /** 高于上限 */
    UPPER_LIMIT("上限报警"),

    /** 低于下限 */
    LOWER_LIMIT("下限报警"),

    /** 偏差过大 */
    DEVIATION("偏差报警"),

    /** 变化率过大 */
    RATE("变化率报警"),

    /** 开关量闭合 */
    SWITCH_ON("开关量闭合报警"),

    /** 开关量断开 */
    SWITCH_OFF("开关量断开报警"),

    /** 报警恢复 */
    RECOVERED("报警恢复");

    private final String label;

    AlarmKind(String label) {
        this.label = label;
    }

    /** 中文名称。 */
    public String label() {
        return label;
    }

    /** 是否为恢复事件。 */
    public boolean isRecovered() {
        return this == RECOVERED;
    }
}
