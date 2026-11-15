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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 报警防抖、抑制与恢复的单元测试（FR-ALM-05）。 */
class AlarmTrackerTest {

    private static AlarmRuleSpec rule(int durationS, int suppressWindowS) {
        return new AlarmRuleSpec(1L, "pH 越限", 10L, 1L, 1, 2, AlarmRuleType.RANGE, 8.0D, 6.0D,
            null, null, null, durationS, suppressWindowS, "pH");
    }

    @Test
    void alarmsImmediatelyWithoutDebounce() {
        AlarmTracker tracker = new AlarmTracker(rule(0, 0));
        Optional<AlarmEvent> event = tracker.onSample(9.5D, "pH", 1000L);
        assertTrue(event.isPresent());
        assertEquals(AlarmKind.UPPER_LIMIT, event.get().kind());
        assertTrue(tracker.isAlarming());
    }

    @Test
    void debounceRequiresSustainedViolation() {
        AlarmTracker tracker = new AlarmTracker(rule(30, 0));
        assertTrue(tracker.onSample(9.5D, "pH", 1000L).isEmpty(), "首次越限只是计时");
        assertTrue(tracker.onSample(9.6D, "pH", 20_000L).isEmpty(), "未满 30 秒不报警");
        Optional<AlarmEvent> event = tracker.onSample(9.7D, "pH", 31_500L);
        assertTrue(event.isPresent(), "满 30 秒后报警");
        assertEquals(AlarmKind.UPPER_LIMIT, event.get().kind());
    }

    @Test
    void debounceResetsWhenValueReturnsToNormal() {
        AlarmTracker tracker = new AlarmTracker(rule(30, 0));
        tracker.onSample(9.5D, "pH", 1000L);
        tracker.onSample(7.0D, "pH", 10_000L);
        assertTrue(tracker.onSample(9.5D, "pH", 20_000L).isEmpty(), "抖动后重新计时");
        assertTrue(tracker.onSample(9.5D, "pH", 49_000L).isEmpty(), "距第二次越限不足 30 秒");
        assertTrue(tracker.onSample(9.5D, "pH", 51_000L).isPresent());
    }

    @Test
    void suppressionWindowThrottlesRepeats() {
        AlarmTracker tracker = new AlarmTracker(rule(0, 300));
        assertTrue(tracker.onSample(9.5D, "pH", 1000L).isPresent());
        assertTrue(tracker.onSample(9.6D, "pH", 60_000L).isEmpty(), "抑制窗口内不重复");
        assertTrue(tracker.onSample(9.7D, "pH", 200_000L).isEmpty(), "仍在内");
        Optional<AlarmEvent> repeat = tracker.onSample(9.8D, "pH", 302_000L);
        assertTrue(repeat.isPresent(), "超出窗口后允许再次报警");
        assertEquals(AlarmKind.UPPER_LIMIT, repeat.get().kind());
    }

    @Test
    void emitsRecoveryOnceAfterAlarm() {
        AlarmTracker tracker = new AlarmTracker(rule(0, 0));
        tracker.onSample(9.5D, "pH", 1000L);
        Optional<AlarmEvent> recovery = tracker.onSample(7.0D, "pH", 2000L);
        assertTrue(recovery.isPresent());
        assertTrue(recovery.get().recovered());
        assertFalse(tracker.isAlarming());
        assertTrue(tracker.onSample(7.1D, "pH", 3000L).isEmpty(), "恢复正常后不再产生事件");
    }

    @Test
    void noRecoveryWithoutPriorAlarm() {
        AlarmTracker tracker = new AlarmTracker(rule(0, 0));
        assertTrue(tracker.onSample(7.0D, "pH", 1000L).isEmpty());
        assertTrue(tracker.onSample(7.1D, "pH", 2000L).isEmpty());
    }

    @Test
    void switchKindChangeAlarmsAgain() {
        AlarmRuleSpec spec = new AlarmRuleSpec(1L, "双侧", 10L, null, 1, 2, AlarmRuleType.RANGE,
            8.0D, 6.0D, null, null, null, 0, 300, "pH");
        AlarmTracker tracker = new AlarmTracker(spec);
        assertEquals(AlarmKind.UPPER_LIMIT, tracker.onSample(9.5D, "pH", 1000L)
            .orElseThrow()
            .kind());
        // 种类变化（上限 -> 下限）不受抑制窗口限制
        assertEquals(AlarmKind.LOWER_LIMIT, tracker.onSample(5.0D, "pH", 2000L)
            .orElseThrow()
            .kind());
    }

    @Test
    void resetProducesRecoveryWhenAlarming() {
        AlarmTracker tracker = new AlarmTracker(rule(0, 0));
        tracker.onSample(9.5D, "pH", 1000L);
        Optional<AlarmEvent> recovery = tracker.reset(5000L);
        assertTrue(recovery.isPresent());
        assertTrue(recovery.get().recovered());
        assertFalse(tracker.isAlarming());
        assertTrue(tracker.reset(6000L).isEmpty(), "未报警时复位不产生事件");
    }
}
