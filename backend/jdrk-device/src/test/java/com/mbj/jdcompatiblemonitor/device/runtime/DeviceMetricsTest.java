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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** 采集指标统计的单元测试（FR-ACQ-10）。 */
class DeviceMetricsTest {

    @Test
    void startsEmpty() {
        DeviceMetrics metrics = new DeviceMetrics();
        assertEquals(0L, metrics.pollCount());
        assertEquals(0d, metrics.successRate());
        assertEquals(0L, metrics.averageDurationMs());
        assertNull(metrics.lastError());
    }

    @Test
    void accumulatesSuccessAndFailure() {
        DeviceMetrics metrics = new DeviceMetrics();
        metrics.recordPoll(100);
        metrics.recordSuccess(150, 50);
        metrics.recordPoll(200);
        metrics.recordFailure(260, 60, true, "读取超时");
        metrics.recordPoll(300);
        metrics.recordFailure(420, 120, false, "CRC 校验失败");

        assertEquals(3L, metrics.pollCount());
        assertEquals(1L, metrics.successCount());
        assertEquals(2L, metrics.errorCount());
        assertEquals(1L, metrics.timeoutCount());
        assertEquals(1d / 3d, metrics.successRate());
        assertEquals(76L, metrics.averageDurationMs());
        assertEquals(150L, metrics.lastSuccessTime());
        assertEquals(420L, metrics.lastFailureTime());
        assertEquals("CRC 校验失败", metrics.lastError());
    }

    @Test
    void clearsErrorAfterRecovery() {
        DeviceMetrics metrics = new DeviceMetrics();
        metrics.recordPoll(0);
        metrics.recordFailure(10, 10, false, "设备无响应");
        metrics.recordPoll(20);
        metrics.recordSuccess(30, 10);
        assertNull(metrics.lastError());
        assertEquals(0.5d, metrics.successRate());
    }

    @Test
    void resetClearsEverything() {
        DeviceMetrics metrics = new DeviceMetrics();
        metrics.recordPoll(0);
        metrics.recordSuccess(1, 1);
        metrics.reset();
        assertEquals(0L, metrics.pollCount());
        assertEquals(0L, metrics.successCount());
        assertEquals(0L, metrics.lastSuccessTime());
    }
}
