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

/**
 * 采集运行参数的有效值（FR-ACQ-01/02/03、FR-PROTO-07、FR-CFG-01）。
 *
 * <p>三级采集间隔的优先级为「设备 &gt; 端口 &gt; 全局」，且不得低于 1 秒
 * （FR-ACQ-01、NFR-PERF-08）。构造时对每个字段做一次兜底收敛，
 * 保证下游拿到的永远是合法值。</p>
 *
 * @param intervalMs       全局采集间隔（毫秒）
 * @param timeoutMs        全局单帧超时（毫秒）
 * @param retries          失败重试次数（不含首次）
 * @param pollBudgetMs     单设备轮询预算（毫秒，超预算跳过剩余通道）
 * @param failureThreshold 连续失败熔断阈值
 * @param circuitOpenMs    熔断打开时长（毫秒）
 * @param maxBackoffMs     退避间隔上限（毫秒）
 * @param nightMultiplier  夜间间隔倍数（1 表示不降频，FR-ACQ-08 分时段策略）
 * @param nightStartHour   夜间起始小时（0~23）
 * @param nightEndHour     夜间结束小时（0~23，支持跨零点）
 */
public record AcquisitionSettings(int intervalMs, int timeoutMs, int retries, int pollBudgetMs,
    int failureThreshold, int circuitOpenMs, int maxBackoffMs, int nightMultiplier,
    int nightStartHour, int nightEndHour) {

    /** 采集间隔下限：1 秒（NFR-PERF-08）。 */
    public static final int MIN_INTERVAL_MS = 1000;
    /** 单帧超时下限：100 毫秒。 */
    public static final int MIN_TIMEOUT_MS = 100;
    /** 单设备轮询预算下限：100 毫秒。 */
    public static final int MIN_BUDGET_MS = 100;

    public AcquisitionSettings {
        intervalMs = Math.max(intervalMs, MIN_INTERVAL_MS);
        timeoutMs = Math.max(timeoutMs, MIN_TIMEOUT_MS);
        retries = Math.max(retries, 0);
        pollBudgetMs = Math.max(pollBudgetMs, MIN_BUDGET_MS);
        failureThreshold = Math.max(failureThreshold, 1);
        circuitOpenMs = Math.max(circuitOpenMs, MIN_INTERVAL_MS);
        maxBackoffMs = Math.max(maxBackoffMs, intervalMs);
        nightMultiplier = Math.max(nightMultiplier, 1);
        nightStartHour = Math.min(Math.max(nightStartHour, 0), 23);
        nightEndHour = Math.min(Math.max(nightEndHour, 0), 23);
    }

    /**
     * 默认参数：5 秒轮询、1 秒超时、重试 2 次、预算 3 秒、连续 5 次失败熔断 1 分钟、退避上限 5 分钟、不降频
     *
     * @return 默认参数
     */
    public static AcquisitionSettings defaults() {
        return new AcquisitionSettings(5000, 1000, 2, 3000, 5, 60_000, 300_000, 1, 22, 6);
    }

    /**
     * 应用分时段策略：落在夜间区间时按倍数降低采集频率（FR-ACQ-08）
     *
     * @param baseIntervalMs 基础间隔（毫秒）
     * @param hour           当前小时（0~23）
     * @return 生效间隔
     */
    public int applyTimeOfDay(int baseIntervalMs, int hour) {
        if (nightMultiplier <= 1) {
            return baseIntervalMs;
        }
        boolean night = nightStartHour <= nightEndHour
            ? hour >= nightStartHour && hour < nightEndHour
            : hour >= nightStartHour || hour < nightEndHour;
        return night ? Math.max(baseIntervalMs * nightMultiplier, MIN_INTERVAL_MS) : baseIntervalMs;
    }

    /**
     * 三级采集间隔：设备级优先于端口级，端口级优先于全局（FR-ACQ-01）
     *
     * @param portIntervalMs   端口级间隔，可为空
     * @param deviceIntervalMs 设备级间隔，可为空
     * @return 生效的采集间隔
     */
    public int resolveInterval(Integer portIntervalMs, Integer deviceIntervalMs) {
        Integer chosen = deviceIntervalMs;
        if (chosen == null || chosen <= 0) {
            chosen = portIntervalMs;
        }
        if (chosen == null || chosen <= 0) {
            chosen = intervalMs;
        }
        return Math.max(chosen, MIN_INTERVAL_MS);
    }

    /**
     * 超时：设备级优先于全局（FR-ACQ-03）
     *
     * @param deviceTimeoutMs 设备级超时，可为空
     * @return 生效的单帧超时
     */
    public int resolveTimeout(Integer deviceTimeoutMs) {
        if (deviceTimeoutMs == null || deviceTimeoutMs <= 0) {
            return timeoutMs;
        }
        return Math.max(deviceTimeoutMs, MIN_TIMEOUT_MS);
    }
}
