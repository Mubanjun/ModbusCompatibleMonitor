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

package com.mbj.jdcompatiblemonitor.device.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 采集顺序与到期判定的单元测试（FR-ACQ-01/04/11）。 */
class DeviceScheduleTest {

    @Test
    void ordersByPriorityThenSortNoThenId() {
        List<DeviceSchedule> schedules = new ArrayList<>(List.of(
            new DeviceSchedule(9, 5, false, true, 5000),
            new DeviceSchedule(8, 1, false, true, 5000),
            new DeviceSchedule(7, 9, true, true, 5000)));
        schedules.sort(null);
        assertEquals(List.of(7L, 8L, 9L),
            schedules.stream().map(DeviceSchedule::deviceId).toList());
    }

    @Test
    void breaksTiesByIdForStableOrder() {
        List<DeviceSchedule> schedules = new ArrayList<>(List.of(
            new DeviceSchedule(3, 1, false, true, 5000),
            new DeviceSchedule(1, 1, false, true, 5000),
            new DeviceSchedule(2, 1, false, true, 5000)));
        schedules.sort(null);
        assertEquals(List.of(1L, 2L, 3L),
            schedules.stream().map(DeviceSchedule::deviceId).toList());
    }

    @Test
    void judgesDueByInterval() {
        assertTrue(DeviceSchedule.isDue(0, 10_000, 5000), "从未轮询过即到期");
        assertFalse(DeviceSchedule.isDue(6000, 10_000, 5000));
        assertTrue(DeviceSchedule.isDue(5000, 10_000, 5000));
        assertTrue(DeviceSchedule.isDue(1, 2, 0), "间隔非法时总是到期");
    }

    @Test
    void disabledDeviceIsNeverDue() {
        DeviceSchedule disabled = new DeviceSchedule(1, 1, false, false, 1000);
        assertFalse(disabled.isDue(0, 100_000));
        DeviceSchedule enabled = new DeviceSchedule(1, 1, false, true, 1000);
        assertTrue(enabled.isDue(0, 100_000));
    }
}
