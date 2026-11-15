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
 * 报警规则的可执行快照（FR-ALM-01/05/06）。
 *
 * <p>由 {@code biz_alarm_rule} 装配而成，采集线程只依赖本记录，不接触数据库。</p>
 *
 * @param ruleId          规则 ID
 * @param name            规则名称
 * @param deviceId        限定设备 ID，为空表示不限
 * @param portId          限定端口 ID，为空表示不限
 * @param channelNo       限定通道号，为空表示不限
 * @param analogNo        限定模拟量序号，为空表示不限
 * @param type            规则类型
 * @param upperLimit      上限
 * @param lowerLimit      下限
 * @param deviation       允许偏差
 * @param rateLimit       变化率上限（单位/分钟）
 * @param switchState     开关量目标状态（0：断开；1：闭合）
 * @param durationS       防抖时长（秒）
 * @param suppressWindowS 抑制窗口（秒）
 * @param elementName     要素名称（用于提示语）
 */
public record AlarmRuleSpec(long ruleId, String name, Long deviceId, Long portId, Integer channelNo,
    Integer analogNo, AlarmRuleType type, Double upperLimit, Double lowerLimit, Double deviation,
    Double rateLimit, Integer switchState, int durationS, int suppressWindowS, String elementName) {

    public AlarmRuleSpec {
        durationS = Math.max(durationS, 0);
        suppressWindowS = Math.max(suppressWindowS, 0);
    }

    /**
     * 判断规则是否适用于某个采样点
     *
     * @param sampleDeviceId 设备 ID
     * @param samplePortId   端口 ID
     * @param sampleChannel  通道号
     * @param sampleAnalog   模拟量序号
     * @return 适用返回 true
     */
    public boolean matches(long sampleDeviceId, long samplePortId, int sampleChannel,
        int sampleAnalog) {
        if (deviceId != null && deviceId != sampleDeviceId) {
            return false;
        }
        if (portId != null && portId != samplePortId) {
            return false;
        }
        if (channelNo != null && channelNo != sampleChannel) {
            return false;
        }
        return analogNo == null || analogNo == sampleAnalog;
    }
}
