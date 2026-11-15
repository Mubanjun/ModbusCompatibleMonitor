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

import org.junit.jupiter.api.Test;

/** 采集参数收敛与三级间隔优先级的单元测试（FR-ACQ-01/02/03）。 */
class AcquisitionSettingsTest {

    @Test
    void shortensInvalidValuesToMinimums() {
        AcquisitionSettings settings = new AcquisitionSettings(10, 1, -5, 1, 0, 0, 0, 0, -3, 40);
        assertEquals(1000, settings.intervalMs());
        assertEquals(100, settings.timeoutMs());
        assertEquals(0, settings.retries());
        assertEquals(100, settings.pollBudgetMs());
        assertEquals(1, settings.failureThreshold());
        assertEquals(1000, settings.circuitOpenMs());
        assertEquals(1000, settings.maxBackoffMs());
        assertEquals(1, settings.nightMultiplier());
        assertEquals(0, settings.nightStartHour());
        assertEquals(23, settings.nightEndHour());
    }

    @Test
    void resolvesIntervalByDeviceThenPortThenGlobal() {
        AcquisitionSettings settings = new AcquisitionSettings(5000, 1000, 2, 3000, 5, 60_000,
            300_000, 1, 22, 6);
        assertEquals(5000, settings.resolveInterval(null, null));
        assertEquals(2000, settings.resolveInterval(2000, null));
        assertEquals(1500, settings.resolveInterval(2000, 1500));
        assertEquals(2000, settings.resolveInterval(2000, 0), "设备级非法值回落到端口级");
        assertEquals(5000, settings.resolveInterval(null, -1));
    }

    @Test
    void clampsResolvedIntervalToOneSecond() {
        AcquisitionSettings settings = AcquisitionSettings.defaults();
        assertEquals(1000, settings.resolveInterval(200, 300));
    }

    @Test
    void appliesNightFactorWithinWindow() {
        AcquisitionSettings disabled = AcquisitionSettings.defaults();
        assertEquals(5000, disabled.applyTimeOfDay(5000, 23), "不降频时夜间不变");

        AcquisitionSettings night = new AcquisitionSettings(5000, 1000, 2, 3000, 5, 60_000,
            300_000, 4, 22, 6);
        assertEquals(20_000, night.applyTimeOfDay(5000, 23), "跨零点的夜间区间");
        assertEquals(20_000, night.applyTimeOfDay(5000, 2));
        assertEquals(20_000, night.applyTimeOfDay(5000, 22), "起始小时含在区间内");
        assertEquals(5000, night.applyTimeOfDay(5000, 6), "结束小时不含在区间内");
        assertEquals(5000, night.applyTimeOfDay(5000, 12));
    }

    @Test
    void appliesNightFactorForNonCrossingWindow() {
        AcquisitionSettings settings = new AcquisitionSettings(5000, 1000, 2, 3000, 5, 60_000,
            300_000, 2, 9, 17);
        assertEquals(10_000, settings.applyTimeOfDay(5000, 9));
        assertEquals(10_000, settings.applyTimeOfDay(5000, 16));
        assertEquals(5000, settings.applyTimeOfDay(5000, 17));
        assertEquals(5000, settings.applyTimeOfDay(5000, 8));
    }

    @Test
    void resolvesTimeoutByDeviceThenGlobal() {
        AcquisitionSettings settings = AcquisitionSettings.defaults();
        assertEquals(1000, settings.resolveTimeout(null));
        assertEquals(2000, settings.resolveTimeout(2000));
        assertEquals(100, settings.resolveTimeout(10), "低于下限时收敛到下限");
    }
}
