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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 报警规则求值的单元测试（FR-ALM-01/06）。 */
class AlarmEvaluatorTest {

    private static AlarmRuleSpec range(Double lower, Double upper) {
        return new AlarmRuleSpec(1L, "pH 越限", 10L, 1L, 1, 2, AlarmRuleType.RANGE, upper, lower,
            null, null, null, 0, 0, "pH");
    }

    @Test
    void detectsUpperAndLowerLimit() {
        AlarmRuleSpec rule = range(6.0D, 8.0D);
        assertEquals(AlarmKind.UPPER_LIMIT,
            AlarmEvaluator.evaluate(rule, 9.5D, 7.0D, 0L, 1000L));
        assertEquals(AlarmKind.LOWER_LIMIT,
            AlarmEvaluator.evaluate(rule, 5.5D, 7.0D, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(rule, 7.0D, 7.0D, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(rule, 8.0D, 7.0D, 0L, 1000L), "边界值不算越限");
    }

    @Test
    void supportsOneSidedLimits() {
        AlarmRuleSpec upperOnly = range(null, 8.0D);
        assertEquals(AlarmKind.UPPER_LIMIT,
            AlarmEvaluator.evaluate(upperOnly, 9.0D, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(upperOnly, 1.0D, null, 0L, 1000L));

        AlarmRuleSpec lowerOnly = range(6.0D, null);
        assertEquals(AlarmKind.LOWER_LIMIT,
            AlarmEvaluator.evaluate(lowerOnly, 5.0D, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(lowerOnly, 100.0D, null, 0L, 1000L));
    }

    @Test
    void detectsDeviationJump() {
        AlarmRuleSpec rule = new AlarmRuleSpec(2L, "跳变", 10L, null, 1, 2,
            AlarmRuleType.DEVIATION, null, null, 2.0D, null, null, 0, 0, "pH");
        assertEquals(AlarmKind.DEVIATION,
            AlarmEvaluator.evaluate(rule, 10.0D, 7.0D, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(rule, 8.5D, 7.0D, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(rule, 7.0D, null, 0L, 1000L), "无上次样本不判定");
    }

    @Test
    void detectsRatePerMinute() {
        AlarmRuleSpec rule = new AlarmRuleSpec(3L, "变化率", 10L, null, 1, 2, AlarmRuleType.RATE,
            null, null, null, 3.0D, null, 0, 0, "pH");
        // 10 秒内变化 1.0 => 6/分钟 > 3
        assertEquals(AlarmKind.RATE, AlarmEvaluator.evaluate(rule, 8.0D, 7.0D, 0L, 10_000L));
        // 60 秒内变化 1.0 => 1/分钟，不报警
        assertNull(AlarmEvaluator.evaluate(rule, 8.0D, 7.0D, 0L, 60_000L));
        assertNull(AlarmEvaluator.evaluate(rule, 8.0D, 7.0D, 5000L, 5000L), "时间未前进不判定");
    }

    @Test
    void detectsSwitchState() {
        AlarmRuleSpec closed = new AlarmRuleSpec(4L, "闭合报警", 10L, null, 1, 2,
            AlarmRuleType.SWITCH, null, null, null, null, 1, 0, 0, "液位开关");
        assertEquals(AlarmKind.SWITCH_ON, AlarmEvaluator.evaluate(closed, 1.0D, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(closed, 0.0D, null, 0L, 1000L));

        AlarmRuleSpec open = new AlarmRuleSpec(5L, "断开报警", 10L, null, 1, 2, AlarmRuleType.SWITCH,
            null, null, null, null, 0, 0, 0, "液位开关");
        assertEquals(AlarmKind.SWITCH_OFF, AlarmEvaluator.evaluate(open, 0.0D, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(open, 1.0D, null, 0L, 1000L));
    }

    @Test
    void ignoresInvalidValues() {
        AlarmRuleSpec rule = range(6.0D, 8.0D);
        assertNull(AlarmEvaluator.evaluate(rule, Double.NaN, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(rule, Double.POSITIVE_INFINITY, null, 0L, 1000L));
        assertNull(AlarmEvaluator.evaluate(null, 100.0D, null, 0L, 1000L));
    }

    @Test
    void matchesSampleScope() {
        AlarmRuleSpec global = new AlarmRuleSpec(1L, "全局", null, null, null, null,
            AlarmRuleType.RANGE, 8.0D, null, null, null, null, 0, 0, "pH");
        assertTrue(global.matches(99L, 7L, 3, 2));

        AlarmRuleSpec scoped = new AlarmRuleSpec(1L, "限定", 10L, 1L, 3, 2, AlarmRuleType.RANGE, 8.0D,
            null, null, null, null, 0, 0, "pH");
        assertTrue(scoped.matches(10L, 1L, 3, 2));
        assertTrue(!scoped.matches(11L, 1L, 3, 2));
        assertTrue(!scoped.matches(10L, 2L, 3, 2));
        assertTrue(!scoped.matches(10L, 1L, 4, 2));
        assertTrue(!scoped.matches(10L, 1L, 3, 1));
    }

    @Test
    void describesRangeAndMessage() {
        AlarmRuleSpec rule = range(6.0D, 8.0D);
        assertEquals("6 ~ 8", AlarmEvaluator.describeRange(rule));
        assertEquals("pH 高于上限：9.5 > 8",
            AlarmEvaluator.describeMessage(rule, AlarmKind.UPPER_LIMIT, 9.5D, null));
        assertEquals("pH 低于下限：5.5pH < 6pH",
            AlarmEvaluator.describeMessage(rule, AlarmKind.LOWER_LIMIT, 5.5D, "pH"));
        assertEquals("允许偏差 ±2",
            AlarmEvaluator.describeRange(new AlarmRuleSpec(2L, "偏差", 10L, null, 1, 2,
                AlarmRuleType.DEVIATION, null, null, 2.0D, null, null, 0, 0, "pH")));
    }

    @Test
    void parsesRuleType() {
        assertEquals(AlarmRuleType.RANGE, AlarmRuleType.parse("range"));
        assertEquals(AlarmRuleType.SWITCH, AlarmRuleType.parse(" SWITCH "));
        assertNull(AlarmRuleType.parse("UNKNOWN"));
        assertNull(AlarmRuleType.parse(null));
    }
}
