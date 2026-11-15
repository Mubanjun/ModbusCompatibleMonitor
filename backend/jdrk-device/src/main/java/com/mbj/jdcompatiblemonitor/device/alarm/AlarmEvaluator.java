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
 * 报警规则求值（FR-ALM-01/06）。
 *
 * <p>无状态纯函数：给定规则、当前值、上一次值与时间间隔，判定是否越限以及越限种类。
 * 是否真正产生报警由 {@link AlarmTracker} 依据防抖与抑制策略决定。</p>
 *
 * <ul>
 *   <li>阈值：高于上限或低于下限；</li>
 *   <li>偏差：与上一次采样的差值的绝对值超过允许偏差（跳变报警）；</li>
 *   <li>变化率：单位时间变化量超过上限（单位/分钟）；</li>
 *   <li>开关量：闭合或断开到配置的目标状态。</li>
 * </ul>
 */
public final class AlarmEvaluator {

    private AlarmEvaluator() {
    }

    /**
     * 求值一次采样
     *
     * @param rule         规则
     * @param value        当前工程值
     * @param previous     上一次工程值，可为空
     * @param previousTime 上一次采样时间（毫秒），无上次样本时忽略
     * @param nowMillis    当前时间（毫秒）
     * @return 越限种类，未越限返回 {@code null}
     */
    public static AlarmKind evaluate(AlarmRuleSpec rule, double value, Double previous,
        long previousTime, long nowMillis) {
        if (rule == null || rule.type() == null || Double.isNaN(value)
            || Double.isInfinite(value)) {
            return null;
        }
        return switch (rule.type()) {
            case RANGE -> evaluateRange(rule, value);
            case DEVIATION -> evaluateDeviation(rule, value, previous);
            case RATE -> evaluateRate(rule, value, previous, previousTime, nowMillis);
            case SWITCH -> evaluateSwitch(rule, value);
        };
    }

    /**
     * 生成报警范围描述
     *
     * @param rule 规则
     * @return 描述文本
     */
    public static String describeRange(AlarmRuleSpec rule) {
        if (rule == null || rule.type() == null) {
            return null;
        }
        return switch (rule.type()) {
            case RANGE -> (rule.lowerLimit() == null ? "-∞" : format(rule.lowerLimit())) + " ~ "
                + (rule.upperLimit() == null ? "+∞" : format(rule.upperLimit()));
            case DEVIATION -> "允许偏差 ±" + format(rule.deviation());
            case RATE -> "变化率上限 " + format(rule.rateLimit()) + "/分钟";
            case SWITCH -> "目标状态 " + (Integer.valueOf(1).equals(rule.switchState()) ? "闭合" : "断开");
        };
    }

    /**
     * 生成报警信息
     *
     * @param rule  规则
     * @param kind  报警种类
     * @param value 当前值
     * @param unit  单位
     * @return 提示语
     */
    public static String describeMessage(AlarmRuleSpec rule, AlarmKind kind, double value,
        String unit) {
        String element = rule.elementName() == null ? "该要素" : rule.elementName();
        String suffix = unit == null ? "" : unit;
        return switch (kind) {
            case UPPER_LIMIT -> element + " 高于上限：" + format(value) + suffix + " > "
                + format(rule.upperLimit()) + suffix;
            case LOWER_LIMIT -> element + " 低于下限：" + format(value) + suffix + " < "
                + format(rule.lowerLimit()) + suffix;
            case DEVIATION -> element + " 变化过大：" + format(value) + suffix;
            case RATE -> element + " 变化率过大：" + format(value) + suffix;
            case SWITCH_ON -> element + " 开关量闭合（" + format(value) + "）";
            case SWITCH_OFF -> element + " 开关量断开（" + format(value) + "）";
            case RECOVERED -> element + " 已恢复正常：" + format(value) + suffix;
        };
    }

    /** 阈值判定。 */
    private static AlarmKind evaluateRange(AlarmRuleSpec rule, double value) {
        if (rule.upperLimit() != null && value > rule.upperLimit()) {
            return AlarmKind.UPPER_LIMIT;
        }
        if (rule.lowerLimit() != null && value < rule.lowerLimit()) {
            return AlarmKind.LOWER_LIMIT;
        }
        return null;
    }

    /** 偏差判定（跳变）。 */
    private static AlarmKind evaluateDeviation(AlarmRuleSpec rule, double value, Double previous) {
        if (previous == null || rule.deviation() == null) {
            return null;
        }
        return Math.abs(value - previous) > rule.deviation() ? AlarmKind.DEVIATION : null;
    }

    /** 变化率判定（单位/分钟）。 */
    private static AlarmKind evaluateRate(AlarmRuleSpec rule, double value, Double previous,
        long previousTime, long nowMillis) {
        if (previous == null || rule.rateLimit() == null) {
            return null;
        }
        long elapsed = nowMillis - previousTime;
        if (elapsed <= 0L) {
            return null;
        }
        double perMinute = Math.abs(value - previous) * 60_000.0D / elapsed;
        return perMinute > rule.rateLimit() ? AlarmKind.RATE : null;
    }

    /** 开关量判定。 */
    private static AlarmKind evaluateSwitch(AlarmRuleSpec rule, double value) {
        boolean closed = value != 0.0D;
        Integer target = rule.switchState();
        if (target == null) {
            return null;
        }
        if (target == 1 && closed) {
            return AlarmKind.SWITCH_ON;
        }
        if (target == 0 && !closed) {
            return AlarmKind.SWITCH_OFF;
        }
        return null;
    }

    /** 数值格式化（去掉无意义的小数位）。 */
    private static String format(Double value) {
        if (value == null) {
            return "-";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(Math.round(value * 10000.0D) / 10000.0D);
    }
}
