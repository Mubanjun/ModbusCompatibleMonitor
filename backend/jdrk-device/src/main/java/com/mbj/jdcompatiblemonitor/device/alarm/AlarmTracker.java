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

import java.util.Optional;

/**
 * 单个「规则 + 采样点」的报警状态机（FR-ALM-05：报警延时、去重、抑制窗口与恢复通知）。
 *
 * <p>策略：</p>
 * <ul>
 *   <li><b>防抖</b>：越限必须连续保持 {@code durationS} 秒才产生报警，抖动不会刷屏；</li>
 *   <li><b>抑制</b>：同一规则在 {@code suppressWindowS} 秒内不重复产生同类报警；</li>
 *   <li><b>去重</b>：已处于报警态且种类未变时，只按抑制窗口节流；</li>
 *   <li><b>恢复</b>：报警态下首次回到正常即产生一条恢复事件。</li>
 * </ul>
 */
public final class AlarmTracker {

    private final AlarmRuleSpec rule;

    private boolean alarming;
    private long violationSince;
    private long lastAlarmTime;
    private AlarmKind lastKind;
    private Double lastValue;
    private long lastTime;

    public AlarmTracker(AlarmRuleSpec rule) {
        this.rule = rule;
    }

    /**
     * 送入一次采样
     *
     * @param value     工程值
     * @param unit      单位
     * @param nowMillis 当前时间
     * @return 需要落库的报警事件，无事件返回空
     */
    public Optional<AlarmEvent> onSample(double value, String unit, long nowMillis) {
        Double previous = lastValue;
        long previousTime = lastTime;
        lastValue = value;
        lastTime = nowMillis;

        AlarmKind kind = AlarmEvaluator.evaluate(rule, value, previous, previousTime, nowMillis);
        if (kind == null) {
            violationSince = 0L;
            if (!alarming) {
                return Optional.empty();
            }
            alarming = false;
            lastKind = null;
            return Optional.of(new AlarmEvent(rule.ruleId(), AlarmKind.RECOVERED, value,
                AlarmEvaluator.describeRange(rule),
                AlarmEvaluator.describeMessage(rule, AlarmKind.RECOVERED, value, unit), nowMillis));
        }

        if (violationSince == 0L) {
            violationSince = nowMillis;
            if (rule.durationS() > 0) {
                // 防抖：首次越限只记时间，不报警
                return Optional.empty();
            }
        } else if (rule.durationS() > 0
            && nowMillis - violationSince < rule.durationS() * 1000L) {
            return Optional.empty();
        }

        if (alarming && kind == lastKind && rule.suppressWindowS() > 0
            && nowMillis - lastAlarmTime < rule.suppressWindowS() * 1000L) {
            return Optional.empty();
        }

        alarming = true;
        lastKind = kind;
        lastAlarmTime = nowMillis;
        return Optional.of(new AlarmEvent(rule.ruleId(), kind, value,
            AlarmEvaluator.describeRange(rule),
            AlarmEvaluator.describeMessage(rule, kind, value, unit), nowMillis));
    }

    /**
     * 外部强制复位（规则被删除或停用时调用），必要时产生恢复事件
     *
     * @param nowMillis 当前时间
     * @return 恢复事件
     */
    public Optional<AlarmEvent> reset(long nowMillis) {
        violationSince = 0L;
        lastValue = null;
        lastTime = 0L;
        if (!alarming) {
            return Optional.empty();
        }
        alarming = false;
        lastKind = null;
        return Optional.of(new AlarmEvent(rule.ruleId(), AlarmKind.RECOVERED, 0.0D,
            AlarmEvaluator.describeRange(rule),
            AlarmEvaluator.describeMessage(rule, AlarmKind.RECOVERED, 0.0D, null), nowMillis));
    }

    /** 是否处于报警状态。 */
    public boolean isAlarming() {
        return alarming;
    }

    /** 规则快照。 */
    public AlarmRuleSpec rule() {
        return rule;
    }
}
